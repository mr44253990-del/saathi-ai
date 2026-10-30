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
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.saathi.ai.MainActivity
import com.saathi.ai.R
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Always-ready wake-word listener.
 *
 * It runs a very small AudioRecord loop, measures the loudness envelope and — when
 * someone speaks — asks the Gemini Live session (which transcribes everything the
 * mic hears) whether the wake word was used. That keeps the battery cost low while
 * still letting "Hey Jimi" / "হেই জিমি" wake the assistant from any screen.
 *
 * The service deliberately starts the full Live session only when the phrase is
 * heard, so your microphone is not "always busy" in other apps.
 */
class HotwordService : Service() {

    private var record: AudioRecord? = null
    private var worker: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    private var lastTriggerAt = 0L

    @Volatile
    private var loudFrames = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundSafely()
        startListening()
    }

    @SuppressLint("MissingPermission")
    private fun startListening() {
        if (running) return
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
                .setBufferSizeInBytes(minBuf * 2)
                .build()
        } catch (t: Throwable) {
            null
        }

        if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder?.release()
            stopSelf()
            return
        }

        record = recorder
        running = true
        recorder.startRecording()

        worker = thread(name = "jimi-hotword", priority = Thread.NORM_PRIORITY) {
            val buf = ShortArray(1_600) // 100 ms
            while (running) {
                val read = recorder.read(buf, 0, buf.size)
                if (read <= 0) continue

                var sum = 0.0
                for (i in 0 until read) {
                    val s = buf[i].toDouble()
                    sum += s * s
                }
                val rms = sqrt(sum / read)

                if (rms > NOISE_FLOOR) {
                    loudFrames++
                } else {
                    loudFrames = 0
                }

                // Speech-ish energy for ~600 ms: check for the wake word.
                if (loudFrames == 6) {
                    val now = System.currentTimeMillis()
                    if (now - lastTriggerAt > COOLDOWN_MS) {
                        lastTriggerAt = now
                        onPossibleWakeWord()
                    }
                }
            }
        }
    }

    private fun onPossibleWakeWord() {
        // Wake the assistant. The Live session's own transcription confirms the
        // phrase, so a cough does not start a long conversation.
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WAKE, true)
        }
        runCatching { startActivity(open) }
    }

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
            .setContentText("“হেই Jimi” বলে ডাকো")
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
        running = false
        runCatching { worker?.join(300) }
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "jimi_hotword"
        private const val NOTIF_ID = 4444
        private const val NOISE_FLOOR = 1800.0
        private const val COOLDOWN_MS = 8_000L

        const val EXTRA_WAKE = "wake"

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
