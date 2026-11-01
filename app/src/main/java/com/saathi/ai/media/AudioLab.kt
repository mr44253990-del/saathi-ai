package com.saathi.ai.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Base64
import com.saathi.ai.core.ErrorCenter
import com.saathi.ai.data.SecurePrefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

/**
 * Audio intelligence (Phase 9).
 *
 * What is genuinely on-device: duration/loudness facts, WAV denoising (noise-floor
 * gate + light smoothing) and WAV trimming. What needs the model: transcription,
 * summarisation and speaker labelling.
 *
 * Compressed audio (mp3/m4a/aac) cannot be decoded without a codec pipeline, so
 * for those the lab says so plainly and hands over the exact Termux/ffmpeg command
 * that will work — never a fake "cleaned" file.
 */
class AudioLab(private val context: Context) {

    private val prefs = SecurePrefs(context)
    private val mu = MediaUnderstanding(context)

    private val dir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "audio").apply { mkdirs() }

    private val http by lazy {
        OkHttpClient.Builder()
            .callTimeout(150, TimeUnit.SECONDS)
            .readTimeout(150, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // ------------------------------------------------------------------- facts

    fun info(path: String): String {
        val f = File(path)
        if (!f.exists()) return "ব্যর্থ: অডিও ফাইল পাওয়া যায়নি — $path"
        val facts = StringBuilder()
        facts.appendLine(mu.audioInfo(path))
        facts.appendLine("আকার: ${humanSize(f.length())}")
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(path)
            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: -1
            if (dur > 0) facts.appendLine("দৈর্ঘ্য: ${dur / 60000} মিনিট ${(dur % 60000) / 1000} সেকেন্ড")
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let { facts.appendLine("ধরন: $it") }
        } catch (t: Throwable) {
            facts.appendLine("মেটাডেটা পড়া যায়নি (${t.message})")
        } finally {
            runCatching { mmr.release() }
        }
        if (path.endsWith(".wav", true)) {
            val pcm = readWavPcm(f)
            if (pcm != null) {
                facts.appendLine("গড় শব্দমাত্রা (RMS): ${String.format(Locale.US, "%.0f", AudioGen(context).rms(pcm))}")
            }
        }
        return "সফল: অডিও তথ্য\n$facts"
    }

    // ------------------------------------------------------------- transcription

    /** Speech → text through the model (audio sent inline, base64). */
    fun transcribe(path: String): String {
        if (!prefs.isConfigured) return "ব্যর্থ: API কী নেই — ট্রান্সক্রিপশন সম্ভব নয়"
        val f = File(path)
        if (!f.exists()) return "ব্যর্থ: অডিও ফাইল পাওয়া যায়নি — $path"
        if (f.length() > MAX_INLINE_BYTES) {
            return "আটকে: ফাইলটি ${humanSize(f.length())} — মডেলের কাছে সরাসরি পাঠানোর সীমা " +
                "${humanSize(MAX_INLINE_BYTES)}। Termux-এ ffmpeg থাকলে ভাগ করে দিতে পারি:\n" +
                "ffmpeg -i '$path' -t 300 -c copy part1.m4a"
        }
        val mime = when {
            path.endsWith(".wav", true) -> "audio/wav"
            path.endsWith(".mp3", true) -> "audio/mp3"
            path.endsWith(".m4a", true) || path.endsWith(".aac", true) -> "audio/aac"
            path.endsWith(".ogg", true) -> "audio/ogg"
            path.endsWith(".flac", true) -> "audio/flac"
            else -> "audio/mp3"
        }
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray()
                    .put(JSONObject().put("text",
                        "এই অডিওতে যা বলা হয়েছে হুবহু লিখে ফেলো। ভাষা যেটিতে বলা হয়েছে সেটিতেই লেখো। " +
                            "শোনা না গেলে «অস্পষ্ট» লেখো — অনুমান করে বসিও না।"))
                    .put(JSONObject().put("inlineData", JSONObject()
                        .put("mimeType", mime)
                        .put("data", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))))
                )
            }))
        }
        return call(body, "ট্রান্সক্রিপশন")?.let { "সফল: অডিওর লেখা বের করা হয়েছে\n$it" }
            ?: "ব্যর্থ: ট্রান্সক্রিপশন করা যায়নি"
    }

    /** Transcript + condensed meaning. */
    fun summarize(path: String): String {
        val t = transcribe(path)
        if (t.startsWith("ব্যর্থ") || t.startsWith("আটকে")) return t
        val text = t.removePrefix("সফল: অডিওর লেখা বের করা হয়েছে\n")
        val s = com.saathi.ai.web.ResearchEngine(context).summarize(
            text, "এই অডিও ট্রান্সক্রিপ্টের মূল কথাগুলো বাংলায় সংক্ষেপে লেখো (৫টি পয়েন্টের মধ্যে)"
        )
        return "সফল: অডিও সামারি\n$s\n\n── পূর্ণ ট্রান্সক্রিপ্ট ──\n${text.take(2500)}"
    }

    /**
     * Speaker labels. Honest caveat: the model infers turns from the audio, this is
     * not a diarisation pipeline with voiceprint clustering.
     */
    fun speakerTags(path: String): String {
        if (!prefs.isConfigured) return "ব্যর্থ: API কী নেই"
        val t = transcribe(path)
        if (t.startsWith("ব্যর্থ") || t.startsWith("আটকে")) return t
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put(
                "text", "নিচের ট্রান্সক্রিপ্ট থেকে সম্ভব হলে বক্তা আলাদা করো (বক্তা ১ / বক্তা ২ …)। " +
                    "নিশ্চিত না হলে বলো যে আলাদা করা যায়নি — অনুমানকে সত্যি বলে চালিয়ে দিও না।\n\n" +
                        t.removePrefix("সফল: অডিওর লেখা বের করা হয়েছে\n").take(9000)
            )))))
        }
        return call(body, "বক্তা শনাক্তকরণ")?.let {
            "সফল: বক্তা অনুযায়ী সাজানো হয়েছে (মডেলের অনুমান — ভয়েসপ্রিন্ট ডায়েরাইজেশন নয়)\n$it"
        } ?: "ব্যর্থ: বক্তা আলাদা করা যায়নি"
    }

    // ------------------------------------------------------------- denoise/trim

    /**
     * WAV denoise: measure the noise floor from the quietest 10% of frames, then
     * gate and lightly smooth. Works for hiss and room tone; it is not a spectral
     * subtractor, and the report says exactly that.
     */
    fun denoise(path: String, strength: Float = 0.8f): String {
        val f = File(path)
        if (!f.exists()) return "ব্যর্থ: ফাইল পাওয়া যায়নি — $path"
        if (!path.endsWith(".wav", true)) {
            return "আটকে: অন-ডিভাইস নয়েজ কমানো শুধু WAV ফাইলে সম্ভব। $path ফরম্যাট ডিকোড করতে কোডেক দরকার।\n" +
                "Termux-এ ffmpeg থাকলে এই কমান্ডে হবে:\n" +
                "ffmpeg -i '$path' -af afftdn=nf=-25 '${f.nameWithoutExtension}-clean.wav'\n" +
                "চাইলে আমি Termux দিয়েই চালাতে পারি।"
        }
        val pcm = readWavPcm(f) ?: return "ব্যর্থ: WAV ডেটা পড়া যায়নি"
        return try {
            val frame = 480 // 20 ms at 24 kHz-ish
            val frames = pcm.size / 2 / frame
            if (frames < 4) return "আংশিক: অডিও খুব ছোট — নয়েজ মাপার মতো যথেষ্ট ফ্রেম নেই"
            val energies = DoubleArray(frames) { i ->
                var sum = 0.0
                for (s in 0 until frame) {
                    val v = sample(pcm, (i * frame + s) * 2).toDouble()
                    sum += v * v
                }
                sum / frame
            }
            val sorted = energies.sorted()
            val floor = kotlin.math.sqrt(sorted[max(0, sorted.size / 10)])
            val gate = (floor * 3.2 * strength).toInt().coerceAtLeast(30)
            val out = pcm.copyOf()
            var smoothed = 0
            for (i in 0 until pcm.size / 2) {
                val v = sample(pcm, i * 2)
                val a = abs(v)
                val nv = if (a < gate) (v * 0.25).toInt() else v
                smoothed = (smoothed * 0.35 + nv * 0.65).toInt()
                writeSample(out, i * 2, smoothed.toShort())
            }
            val outFile = writeWav(out, guessRate(pcm.size, frames, frame), "denoised")
            "সফল: নয়েজ কমানো হয়েছে (অফলাইন নয়েজ-গেট + স্মুদিং)\n" +
                "নয়েজ ফ্লোর: ${String.format(Locale.US, "%.0f", floor)} · গেট: $gate\n" +
                "ফাইল: ${outFile.absolutePath}\n" +
                "নোট: এটি হিস/রুম-টোন কমায়; ব্যাকগ্রাউন্ডে কথা বা গান থাকলে সেটা পুরোপুরি যাবে না।"
        } catch (t: Throwable) {
            ErrorCenter.logThrow("audio", "ডিনয়েজ ব্যর্থ", t)
            "ব্যর্থ: নয়েজ কমানো যায়নি (${t.message})"
        }
    }

    /** WAV trim without re-encoding: sample-accurate slice of the data chunk. */
    fun trim(path: String, startSec: Int, endSec: Int): String {
        val f = File(path)
        if (!f.exists()) return "ব্যর্থ: ফাইল পাওয়া যায়নি"
        if (!path.endsWith(".wav", true)) {
            return "আটকে: WAV ছাড়া অন-ডিভাইস কাটা সম্ভব নয়। Termux-এ ffmpeg থাকলে:\n" +
                "ffmpeg -i '$path' -ss $startSec -to $endSec -c copy '${f.nameWithoutExtension}-cut.${f.extension}'"
        }
        val pcm = readWavPcm(f) ?: return "ব্যর্থ: WAV ডেটা পড়া যায়নি"
        val rate = guessRateFromHeader(f)
        val from = (startSec * rate * 2).coerceIn(0, pcm.size)
        val to = (endSec * rate * 2).coerceIn(from + 2, pcm.size)
        val slice = pcm.copyOfRange(from, to)
        val out = writeWav(slice, rate, "trim")
        return "সফল: অডিও কাটা হয়েছে (${startSec}–${endSec} সে., ${(to - from) / (rate * 2)} সেকেন্ড রাখা হয়েছে)\nফাইল: ${out.absolutePath}"
    }

    // ------------------------------------------------------------------ plumbing

    private fun call(body: JSONObject, what: String): String? {
        var lastError = ""
        for (model in MODELS) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            val request = Request.Builder()
                .url(url)
                .header("x-goog-api-key", prefs.apiKey)
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            try {
                http.newCall(request).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        lastError = "$model → HTTP ${resp.code}"
                        if (resp.code == 404 || resp.code == 400 || resp.code == 413) return@use
                        ErrorCenter.log("audio", "$what কল ব্যর্থ", lastError)
                        return null
                    }
                    val parts = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts")
                    val sb = StringBuilder()
                    if (parts != null) for (i in 0 until parts.length()) {
                        parts.optJSONObject(i)?.optString("text")?.let { if (it.isNotBlank()) sb.appendLine(it) }
                    }
                    return sb.toString().trim().ifBlank { null }
                }
            } catch (t: Throwable) {
                lastError = t.message ?: "নেটওয়ার্ক ত্রুটি"
                ErrorCenter.log("audio", "$what নেটওয়ার্ক ত্রুটি", lastError)
            }
        }
        ErrorCenter.log("audio", "$what ব্যর্থ", lastError)
        return null
    }

    /** Reads the data chunk of a PCM WAV file (16-bit assumed). */
    private fun readWavPcm(f: File): ByteArray? {
        return try {
        val bytes = f.readBytes()
        var i = 12
        while (i + 8 <= bytes.size) {
            val id = String(bytes, i, 4)
            val size = ByteBuffer.wrap(bytes, i + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (id == "data") {
                val from = i + 8
                val to = minOf(bytes.size, from + size)
                return bytes.copyOfRange(from, to)
            }
            i += 8 + size + (size % 2)
        }
        null
    } catch (t: Throwable) {
        ErrorCenter.log("audio", "WAV পড়া যায়নি", t.message)
        null
    }
    }

    private fun guessRateFromHeader(f: File): Int = try {
        val bytes = f.readBytes().copyOfRange(0, 44)
        ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
    } catch (t: Throwable) {
        24_000
    }

    private fun guessRate(totalBytes: Int, frames: Int, frame: Int): Int =
        if (frames * frame > 0) (totalBytes / 2 / (frames * frame)).coerceIn(8_000, 96_000) else 24_000

    private fun sample(pcm: ByteArray, index: Int): Short =
        ByteBuffer.wrap(pcm, index, 2).order(ByteOrder.LITTLE_ENDIAN).short

    private fun writeSample(pcm: ByteArray, index: Int, value: Short) {
        val b = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
        pcm[index] = b[0]
        pcm[index + 1] = b[1]
    }

    private fun writeWav(pcm: ByteArray, rate: Int, prefix: String): File =
        AudioGen(context).writeWav(pcm, rate, prefix)

    private fun humanSize(b: Long): String = when {
        b > 1_048_576 -> String.format(Locale.US, "%.1f MB", b / 1_048_576.0)
        b > 1024 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
        else -> "$b B"
    }

    fun capabilities(): String = buildString {
        appendLine("অডিও ল্যাব:")
        appendLine("• info: দৈর্ঘ্য/আকার/ধরন/শব্দমাত্রা (অফলাইন)")
        appendLine("• denoise: WAV-এ নয়েজ গেট + স্মুদিং (অফলাইন); অন্য ফরম্যাটে ffmpeg কমান্ড দেয়")
        appendLine("• trim: WAV কাটা (অফলাইন, স্যাম্পল-নিখুঁত)")
        appendLine("• transcribe: অডিও → লেখা (মডেল)")
        appendLine("• summarize: মূল পয়েন্ট (মডেল)")
        appendLine("• speakerTags: বক্তা অনুযায়ী ভাগ (মডেলের অনুমান)")
        append("API কী: ${if (prefs.isConfigured) "আছে" else "নেই"}")
    }

    companion object {
        private const val MAX_INLINE_BYTES = 15L * 1024 * 1024
        val MODELS = listOf("gemini-2.5-flash", "gemini-3-pro", "gemini-2.5-pro")
    }
}
