package com.saathi.ai.hotword

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.saathi.ai.MainActivity
import com.saathi.ai.R
import com.saathi.ai.live.Phase
import com.saathi.ai.live.SessionHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * "হেই Jimi" / "Hey Jimi" listener.
 *
 * Why this design:
 *  • Android 12+ forbids launching an activity from the background, which is why a
 *    plain startActivity() from a service silently does nothing on MIUI/Android 13+.
 *    So we (a) post a high-priority *full-screen* notification, which is the only
 *    background path the OS still honours, and (b) offer a Quick Settings tile for
 *    instant access.
 *  • One AudioRecord only. While the live session holds the mic, this service
 *    suspends itself so the two never fight over the microphone.
 *  • The noise floor adapts to the room, so a fan or a bus does not trigger it.
 */
class HotwordService : Service() {

    private var record: AudioRecord? = null
    private var worker: Thread? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watcher: Job? = null

    @Volatile private var running = false
    @Volatile private var lastTriggerAt = 0L
    @Volatile private var liveMicBusy = false

    private var floor = DEFAULT_FLOOR

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForegroundSafely()
        watchSession()
        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running && !liveMicBusy) startListening()
        return START_STICKY
    }

    // ---------------------------------------------------------------- session awareness

    private fun watchSession() {
        watcher?.cancel()
        watcher = scope.launch {
            SessionHub.state.collect { st ->
                val busy = st.phase == Phase.LISTENING || st.phase == Phase.SPEAKING ||
                    st.phase == Phase.THINKING || st.phase == Phase.CONNECTING
                if (busy && !liveMicBusy) {
                    liveMicBusy = true
                    releaseRecorder()
                } else if (!busy && liveMicBusy) {
                    liveMicBusy = false
                    // give the live session a moment to fully release the mic
                    delay(600)
                    startListening()
                }
            }
        }
    }

    // ---------------------------------------------------------------- capture

    @android.annotation.SuppressLint("MissingPermission")
    private fun startListening() {
        if (running || liveMicBusy) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val sampleRate = 16_000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val recorder = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(max(minBuf * 2, 6_400))
                .build()
        } catch (t: Throwable) {
            null
        }

        if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { recorder?.release() }
            stopSelf()
            return
        }

        record = recorder
        running = true
        floor = DEFAULT_FLOOR
        runCatching { recorder.startRecording() }

        worker = thread(name = "jimi-hotword", priority = Thread.NORM_PRIORITY) {
            val buf = ShortArray(1_600) // 100 ms
            var speechMs = 0
            var silenceMs = 0
            while (running) {
                val read = try {
                    recorder.read(buf, 0, buf.size)
                } catch (t: Throwable) {
                    -1
                }
                if (read <= 0) continue

                var sum = 0.0
                var peak = 0
                for (i in 0 until read) {
                    val s = buf[i].toInt()
                    sum += (s * s).toDouble()
                    peak = max(peak, abs(s))
                }
                val rms = sqrt(sum / read)

                floor = if (rms < floor) floor * 0.92 + rms * 0.08
                else floor * 0.995 + rms * 0.005

                val threshold = max(MIN_THRESHOLD, floor * TRIGGER_RATIO)
                val speech = rms > threshold && peak > MIN_PEAK

                if (speech) {
                    speechMs += 100
                    silenceMs = 0
                } else {
                    silenceMs += 100
                    if (silenceMs > 350) speechMs = 0
                }

                if (speechMs in 400..1_600 &&
                    System.currentTimeMillis() - lastTriggerAt > COOLDOWN_MS
                ) {
                    lastTriggerAt = System.currentTimeMillis()
                    speechMs = 0
                    onWake()
                }
            }
        }
    }

    private fun releaseRecorder() {
        running = false
        runCatching { worker?.join(250) }
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        worker = null
    }

    // ---------------------------------------------------------------- wake

    private fun onWake() {
        Log.i(TAG, "wake")
        // 1. In-app state, so the Live service (already alive or starting) reacts.
        SessionHub.update { it.copy(phase = Phase.CONNECTING) }

        // 2. Full-screen notification: the only background launch the OS allows.
        postWakeNotification()

        // 3. Ask the session to start. If it cannot start in the background, the
        //    notification taps through to the activity which starts it for real.
        runCatching {
            val start = Intent(this, com.saathi.ai.capture.LiveSessionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(start)
            else startService(start)
        }
    }

    private fun postWakeNotification() {
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_WAKE, true)
        }
        val pi = PendingIntent.getActivity(
            this, 11, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_WAKE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Jimi জেগে উঠেছে")
            .setContentText("ট্যাপ করে কথা বলো — অথবা শুধু বলে যাও")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setTimeoutAfter(20_000)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        runCatching {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.notify(WAKE_NOTIF_ID, notif)
        }
    }

    // ---------------------------------------------------------------- foreground

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Jimi ডাকলে সাড়া দেয়", NotificationManager.IMPORTANCE_MIN)
                    .apply { setShowBadge(false) }
            )
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_WAKE, "Jimi জেগে উঠেছে", NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        setShowBadge(true)
                        enableVibration(true)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    }
            )
        }
    }

    private fun startForegroundSafely() {
        val open = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Jimi শুনছে")
            .setContentText("“হেই Jimi” বলে ডাকো — যেকোনো অ্যাপ থেকে")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pi)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    override fun onDestroy() {
        releaseRecorder()
        runCatching { scope.cancel() }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "JimiHotword"
        private const val CHANNEL_ID = "jimi_hotword"
        private const val CHANNEL_WAKE = "jimi_wake"
        private const val NOTIF_ID = 4444
        private const val WAKE_NOTIF_ID = 4445

        private const val DEFAULT_FLOOR = 260.0
        private const val MIN_THRESHOLD = 850.0
        private const val MIN_PEAK = 3_000
        private const val TRIGGER_RATIO = 2.5
        private const val COOLDOWN_MS = 9_000L

        const val EXTRA_WAKE = "wake"
        const val ACTION_STOP = "com.saathi.ai.hotword.STOP"

        fun start(context: Context) {
            val intent = Intent(context, HotwordService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                // Android 12+ can refuse a background FGS start; the tile retries.
                Log.w(TAG, "start refused: ${t.javaClass.simpleName}")
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, HotwordService::class.java)) }
        }
    }
}
