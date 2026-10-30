package com.saathi.ai.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Microphone capture: 16 kHz, mono, 16-bit PCM — exactly what Gemini Live wants.
 * Echo cancellation / noise suppression are enabled when supported so the
 * assistant can hear you while it is still talking (barge-in).
 */
class MicStream(
    private val onChunk: (ByteArray) -> Unit,
    private val onLevel: (Float) -> Unit
) {
    private var record: AudioRecord? = null
    private var worker: Thread? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null

    @Volatile
    private var running = false

    val isRunning: Boolean get() = running

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = max(minBuf * 2, SAMPLE_RATE / 5 * 2) // ~200 ms

        val recorder = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufSize)
                .build()
        } catch (t: Throwable) {
            null
        }

        if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder?.release()
            return
        }

        record = recorder
        runCatching {
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(recorder.audioSessionId)?.apply { enabled = true }
            }
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(recorder.audioSessionId)?.apply { enabled = true }
            }
            if (AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(recorder.audioSessionId)?.apply { enabled = true }
            }
        }

        running = true
        recorder.startRecording()

        worker = thread(name = "saathi-mic", priority = Thread.MAX_PRIORITY) {
            val buffer = ByteArray(CHUNK_BYTES)
            while (running) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    onChunk(buffer.copyOf(read))
                    onLevel(levelOf(buffer, read))
                } else if (read < 0) {
                    break
                }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { worker?.join(400) }
        worker = null
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        runCatching {
            aec?.release()
            ns?.release()
            agc?.release()
        }
        aec = null; ns = null; agc = null
        onLevel(0f)
    }

    private fun levelOf(buf: ByteArray, len: Int): Float {
        var sum = 0.0
        var i = 0
        while (i + 1 < len) {
            val sample = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort().toInt()
            sum += sample.toDouble() * sample
            i += 2
        }
        val rms = sqrt(sum / (len / 2).coerceAtLeast(1))
        return (rms / 8000.0).coerceIn(0.0, 1.0).toFloat()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHUNK_BYTES = 2_048 // ~64 ms of audio
    }
}

/**
 * Speaker playback of the model's 24 kHz PCM stream.
 * All writes happen on a dedicated thread so the WebSocket reader never blocks,
 * and [flush] supports instant barge-in interruption.
 */
class PcmPlayer {

    private val queue = LinkedBlockingQueue<ByteArray>(MAX_QUEUED_CHUNKS)
    private var track: AudioTrack? = null
    private var writer: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    var isSpeaking: Boolean = false
        private set

    fun start() {
        if (running) return
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val t = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(max(minBuf * 2, SAMPLE_RATE / 5 * 2))
                .build()
        } catch (t: Throwable) {
            null
        } ?: return

        track = t
        t.play()
        running = true
        queue.clear()

        writer = thread(name = "saathi-speaker", priority = Thread.MAX_PRIORITY) {
            while (running) {
                val chunk = try {
                    queue.take()
                } catch (e: InterruptedException) {
                    break
                }
                if (chunk.isEmpty()) continue
                isSpeaking = true
                runCatching { t.write(chunk, 0, chunk.size) }
                if (queue.isEmpty()) isSpeaking = false
            }
        }
    }

    fun enqueue(pcm: ByteArray) {
        if (!running) return
        if (queue.size >= MAX_QUEUED_CHUNKS) queue.poll() // drop oldest, stay realtime
        queue.offer(pcm)
    }

    /** Immediately silences the assistant (used on interruption / barge-in). */
    fun flush() {
        queue.clear()
        isSpeaking = false
        runCatching {
            track?.pause()
            track?.flush()
            track?.play()
        }
    }

    fun stop() {
        running = false
        isSpeaking = false
        queue.clear()
        runCatching { writer?.interrupt() }
        writer = null
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
    }

    companion object {
        const val SAMPLE_RATE = 24_000
        private const val MAX_QUEUED_CHUNKS = 48
    }
}
