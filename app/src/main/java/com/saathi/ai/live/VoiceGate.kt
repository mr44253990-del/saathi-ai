package com.saathi.ai.live

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat

/**
 * The "বললেই আবার যুক্ত হবে" guard.
 *
 * A live session that nobody talks to is pure battery and data burn, and the server
 * eventually drops it anyway. So after a configurable idle period the service parks
 * the connection — and this gate keeps listening to the microphone for *energy only*
 * (no streaming, no upload, ~2% of the cost). The moment real speech is detected it
 * fires [onVoice] and the service reconnects, so from the user's side Jimi simply
 * woke up when they spoke.
 *
 * The threshold adapts: a rolling noise floor tracks the room, and a frame only
 * counts as voice when it is clearly above that floor for several frames in a row.
 * That keeps fans and traffic from waking the session while a normal sentence always
 * does.
 */
class VoiceGate(private val context: Context, private val onVoice: () -> Unit) {

    @Volatile
    private var running = false

    private var thread: Thread? = null

    val isRunning: Boolean get() = running

    /** Why the gate is not running, for honest reporting in Settings. */
    var lastRefusal: String? = null
        private set

    fun start() {
        if (running) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            lastRefusal = "মাইকের অনুমতি নেই"
            return
        }
        running = true
        lastRefusal = null
        thread = Thread({ loop() }, "jimi-voice-gate").apply { isDaemon = true }.also { it.start() }
    }

    fun stop() {
        running = false
        thread?.let { runCatching { it.join(400) } }
        thread = null
    }

    private fun loop() {
        var record: AudioRecord? = null
        try {
            val rate = 16000
            val minBuf = AudioRecord.getMinBufferSize(
                rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) {
                lastRefusal = "এই ফোনে মাইক বাফার পাওয়া যায়নি"
                running = false
                return
            }
            val bufSize = maxOf(minBuf, rate / 5 * 2)
            record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                lastRefusal = "মাইক খোলা যায়নি (সম্ভবত অন্য অ্যাপ ব্যবহার করছে)"
                runCatching { record.release() }
                running = false
                return
            }
            record.startRecording()

            val frame = ShortArray(rate / 20) // 50 ms
            var noiseFloor = 300.0
            var hits = 0

            while (running) {
                val n = record.read(frame, 0, frame.size)
                if (n <= 0) {
                    Thread.sleep(20)
                    continue
                }
                var sum = 0.0
                for (i in 0 until n) {
                    val v = frame[i].toDouble()
                    sum += v * v
                }
                val rms = Math.sqrt(sum / n)
                if (rms < noiseFloor * 1.6) noiseFloor = noiseFloor * 0.985 + rms * 0.015
                val threshold = maxOf(520.0, noiseFloor * 3.2)
                if (rms > threshold) {
                    hits++
                    if (hits >= 3) {
                        running = false
                        onVoice()
                        return
                    }
                } else if (hits > 0) {
                    hits--
                }
            }
        } catch (t: Throwable) {
            lastRefusal = "মাইক পড়া যায়নি: ${t.javaClass.simpleName}"
        } finally {
            runCatching { record?.stop() }
            runCatching { record?.release() }
        }
    }
}
