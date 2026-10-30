package com.saathi.ai.hotword

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.saathi.ai.MainActivity
import com.saathi.ai.R
import com.saathi.ai.live.Phase
import com.saathi.ai.live.SessionHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Always-ready wake-word listener ("হেই Jimi" / "Hey Jimi").
 *
 * Design notes
 * ------------
 * • Runs one low-cost AudioRecord (16 kHz mono, VOICE_RECOGNITION) and only ever
 *   looks at the loudness envelope — no audio leaves the device here.
 * • The noise floor adapts to the room, so a fan, a bus or a quiet bedroom all
 *   work without retuning the constant.
 * • The Live session (which transcribes speech anyway) decides whether the wake
 *   word was actually said, so a cough only opens Jimi, it does not start a
 *   conversation.
 * • Self-suspends whenever the live mic is already open, otherwise two
 *   AudioRecords fight for the microphone and one of them dies.
 */
class HotwordService : Service() {

    private var record: AudioRecord? = null
    private var worker: Thread? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var running = false
    @Volatile private var lastTriggerAt = 0L
    @Volatile private var liveMicBusy = false

    /** Rolling noise floor; starts conservative and converges in ~2 s. */
    private var floor = DEFAULT_FLOOR

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundSafely()
        watchSession()
        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_RESUME, false) == true && !running) startListening()
        return START_STICKY
    }

    // ---------------------------------------------------------------- session awareness

    private fun watchSession() {
        scope.launch {
            SessionHub.state.collect { st ->
                val busy = st.phase == Phase.LISTENING ||
                    st.phase == Phase.SPEAKING ||
                    st.phase == Phase.THINKING ||
                    st.phase == Phase.CONNECTING
                if (busy && !liveMicBusy) {
                    liveMicBusy = true
                    releaseRecorder()
                } else if (!busy && liveMicBusy) {
                    liveMicBusy = false
                    startListening()
                }
            }
        }
    }

    // ---------------------------------------------------------------- capture loop

    @SuppressLint("MissingPermission")
    private fun startListening() {
        if (running || liveMicBusy) return

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
            val buf = ShortArray(1_600) // 100 ms @16 kHz
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

                // adapt the floor to the room (slow down, fast up)
                floor = if (rms < floor) {
                    floor * 0.92 + rms * 0.08
                } else {
                    floor * 0.995 + rms * 0.005
                }
                val threshold = max(MIN_THRESHOLD, floor * TRIGGER_RATIO)
                val speech = rms > threshold && peak > MIN_PEAK

                if (speech) {
                    speechMs += 100
                    silenceMs = 0
                } else {
                    silenceMs += 100
                    if (silenceMs > 350) speechMs = 0
                }

                // a person said ~0.4–1.6 s of something: open Jimi once
                if (speechMs in 400..1_600 && System.currentTimeMillis() - lastTriggerAt > COOLDOWN_MS) {
                    lastTriggerAt = System.currentTimeMillis()
                    speechMs = 0
                    onPossibleWakeWord()
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

    private fun onPossibleWakeWord() {
        val audio = getSystemService(AudioManager::class.java)
        if (audio != null && audio.ringerMode != AudioManager.RINGER_MODE_SILENT) {
            // a soft tick, so the user knows Jimi woke up
            runCatching {
                audio.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.35f)
            }
        }
        runCatching { vibrateTick() }

        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WAKE, true)
        }
        runCatching { startActivity(open) }
    }

    private fun vibrateTick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibrator = getSystemService(Vibrator::class.java) ?: return
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(
                    android.os.VibrationEffect.createOneShot(
                        28, android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            }
        }
    }

    // ---------------------------------------------------------------- foreground

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Jimi ডাকলে সাড়া দেয়",
                    NotificationManager.IMPORTANCE_MIN
                ).apply { setShowBadge(false) }
            )
        }
    }

    private fun startForegroundSafely() {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Jimi শুনছে")
            .setContentText("“হেই Jimi” বলে ডাকো — যেকোনো অ্যাপ থেকে")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open)
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
        private const val CHANNEL_ID = "jimi_hotword"
        private const val NOTIF_ID = 4444

        private const val DEFAULT_FLOOR = 260.0
        private const val MIN_THRESHOLD = 900.0
        private const val MIN_PEAK = 3_200
        private const val TRIGGER_RATIO = 2.6
        private const val COOLDOWN_MS = 10_000L

        const val EXTRA_WAKE = "wake"
        const val EXTRA_RESUME = "resume"

        fun start(context: Context) {
            val intent = Intent(context, HotwordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HotwordService::class.java))
        }
    }
}
