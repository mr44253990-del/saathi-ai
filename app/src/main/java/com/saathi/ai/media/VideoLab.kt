package com.saathi.ai.media

import android.content.Context
import android.graphics.Bitmap
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
import java.util.Locale
import kotlin.math.abs

/**
 * Video intelligence (Phase 9).
 *
 * Small files go to the model whole (best quality). Large ones are understood from
 * sampled frames, which is honest but coarser — the report says which path was used.
 * Scene detection is on-device (frame histogram difference) so it works offline and
 * its timestamps come from real decoding, not from a model guess.
 *
 * Editing is the one thing this lab cannot do on-device: re-encoding needs a codec
 * pipeline. Instead of pretending, it produces the exact ffmpeg command and can hand
 * it to the terminal agent.
 */
class VideoLab(private val context: Context) {

    private val prefs = SecurePrefs(context)
    private val mu = MediaUnderstanding(context)

    private val dir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "video").apply { mkdirs() }

    private val http by lazy {
        OkHttpClient.Builder()
            .callTimeout(180, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // ------------------------------------------------------------------- facts

    fun info(path: String): String {
        val f = File(path)
        if (!f.exists()) return "ব্যর্থ: ভিডিও ফাইল পাওয়া যায়নি — $path"
        val mmr = MediaMetadataRetriever()
        val sb = StringBuilder()
        try {
            mmr.setDataSource(path)
            sb.appendLine(mu.videoInfo(path))
            sb.appendLine("আকার: ${humanSize(f.length())}")
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { d ->
                if (d > 0) sb.appendLine("দৈর্ঘ্য: ${d / 60000} মিনিট ${(d % 60000) / 1000} সেকেন্ড")
            }
            val rot = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
            if (rot != null) sb.appendLine("ঘূর্ণন: $rot°")
            val frames = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
            if (frames != null) sb.appendLine("ফ্রেমরেট: ${String.format(Locale.US, "%.2f", frames)} fps")
        } catch (t: Throwable) {
            sb.appendLine("মেটাডেটা পড়া যায়নি: ${t.message}")
        } finally {
            runCatching { mmr.release() }
        }
        return "সফল: ভিডিও তথ্য\n$sb"
    }

    // --------------------------------------------------------------- summarize

    /** Whole-file understanding for small clips, frame sampling for big ones. */
    fun summarize(path: String): String {
        if (!prefs.isConfigured) return "ব্যর্থ: API কী নেই — ভিডিও বোঝার জন্য মডেল দরকার"
        val f = File(path)
        if (!f.exists()) return "ব্যর্থ: ভিডিও পাওয়া যায়নি — $path"
        return if (f.length() <= MAX_INLINE_BYTES) {
            inlineAsk(f, "এই ভিডিওতে কী ঘটছে সংক্ষেপে বলো: শুরু, মাঝ, শেষ। " +
                "কোনো লেখা/টেক্সট দেখালে সেটাও উল্লেখ করো। অনিশ্চিত হলে বলো।")
                ?.let { "সফল: ভিডিও সামারি (পুরো ফাইল মডেলকে পাঠানো হয়েছে)\n$it" }
                ?: framesAsk(path, "summarize")
        } else {
            framesAsk(path, "summarize")
        }
    }

    private fun framesAsk(path: String, mode: String): String {
        val frames = mu.videoFrames(path, FRAME_COUNT, 640)
        if (frames.isEmpty()) {
            return "ব্যর্থ: ভিডিওর কোনো ফ্রেম ডিকোড করা যায়নি — ফাইলটি সম্ভবত নষ্ট বা অসমর্থিত ফরম্যাট"
        }
        val prompt = when (mode) {
            "scenes" -> "প্রতিটি ফ্রেমের সময় দেখানো আছে। কোন সময়গুলোতে দৃশ্য/বিষয় বদলেছে তা আলাদা করে বলো।"
            "subtitles" -> "ফ্রেমে যেসব লেখা/সাবটাইটেল দেখা যাচ্ছে সেগুলো সময় অনুযায়ী লিখে দাও। " +
                "কোনো লেখা না দেখা গেলে স্পষ্ট বলো «কোনো লেখা পাওয়া যায়নি»।"
            else -> "এগুলো ভিডিওর ${frames.size}টি নমুনা ফ্রেম (সময়সহ)। পুরো ভিডিওতে কী ঘটছে তা " +
                "সম্ভাব্যভাবে বলো, আর কোথায় নিশ্চিত নও তা আলাদা করে চিহ্নিত করো।"
        }
        val body = JSONObject().apply {
            val parts = JSONArray().put(JSONObject().put("text", prompt))
            frames.forEachIndexed { i, bmp ->
                val jpeg = mu.toJpeg(bmp, 82)
                parts.put(JSONObject().put("text", "ফ্রেম ${i + 1} — ${stampMs(i, frames.size, path)}"))
                parts.put(JSONObject().put("inlineData", JSONObject()
                    .put("mimeType", "image/jpeg").put("data", mu.toBase64(jpeg))))
                bmp.recycle()
            }
            put("contents", JSONArray().put(JSONObject().put("parts", parts)))
        }
        val note = "(নমুনা ফ্রেম থেকে — পুরো ফাইল পাঠানোর সীমা ${humanSize(MAX_INLINE_BYTES)} ছাড়িয়ে গেছে)"
        return call(body, "ভিডিও বিশ্লেষণ")?.let { "সফল: ভিডিও বিশ্লেষণ $note\n$it" }
            ?: "ব্যর্থ: ভিডিও বিশ্লেষণ করা যায়নি"
    }

    private fun stampMs(index: Int, total: Int, path: String): String {
        val dur = durationMs(path)
        val at = if (dur > 0 && total > 1) dur * index / (total - 1) else 0L
        return if (dur > 0) String.format(Locale.US, "%.1f s", at / 1000.0) else "অজানা সময়"
    }

    private fun inlineAsk(f: File, prompt: String): String? {
        val mime = when (f.extension.lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "3gp" -> "video/3gpp"
            "mov" -> "video/quicktime"
            else -> "video/mp4"
        }
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray()
                    .put(JSONObject().put("text", prompt))
                    .put(JSONObject().put("inlineData", JSONObject()
                        .put("mimeType", mime)
                        .put("data", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))))
                )
            }))
        }
        return call(body, "ভিডিও বিশ্লেষণ")
    }

    // ------------------------------------------------------------------ scenes

    /**
     * On-device scene detection: sample frames, compare normalised RGB histograms,
     * and report the timestamps where the difference crosses the threshold.
     */
    fun scenes(path: String, threshold: Double = 0.24): String {
        val dur = durationMs(path)
        if (dur <= 0) return "ব্যর্থ: ভিডিওর দৈর্ঘ্য পড়া যায়নি — ফাইলটি অসমর্থিত বা নষ্ট"
        val mmr = MediaMetadataRetriever()
        val changes = mutableListOf<Long>()
        var prev: IntArray? = null
        var sampled = 0
        try {
            mmr.setDataSource(path)
            val step = (dur / SAMPLE_STEPS).coerceAtLeast(400)
            var at = 0L
            while (at < dur && sampled < SAMPLE_STEPS) {
                val bmp = mmr.getFrameAtTime(at * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                if (bmp != null) {
                    val hist = histogram(bmp)
                    bmp.recycle()
                    val p = prev
                    if (p != null) {
                        val d = distance(p, hist)
                        if (d > threshold) changes.add(at)
                    }
                    prev = hist
                    sampled++
                }
                at += step
            }
        } catch (t: Throwable) {
            ErrorCenter.logThrow("media", "সিন শনাক্তকরণ ব্যর্থ", t)
            runCatching { mmr.release() }
            return "ব্যর্থ: ফ্রেম ডিকোড করা যায়নি (${t.message})"
        } finally {
            runCatching { mmr.release() }
        }
        if (changes.isEmpty()) {
            return "আংশিক: ${sampled}টি ফ্রেম পরীক্ষা করে কোনো বড় দৃশ্য-পরিবর্তন পাওয়া যায়নি — " +
                "ভিডিওটি সম্ভবত একটানা একই দৃশ্যের।"
        }
        return buildString {
            appendLine("সফল: ${changes.size}টি দৃশ্য-পরিবর্তন পাওয়া গেছে (${sampled}টি ফ্রেম পরীক্ষা করা হয়েছে)")
            changes.take(40).forEachIndexed { i, ms ->
                appendLine("• দৃশ্য ${i + 2} শুরু: ${clock(ms)} (${ms / 1000} s)")
            }
            if (changes.size > 40) appendLine("…আরও ${changes.size - 40}টি")
            append("নোট: সময়গুলো ফ্রেম হিস্টোগ্রামের পার্থক্য থেকে মাপা (অন-ডিভাইস), মডেলের অনুমান নয়।")
        }
    }

    private fun histogram(bmp: Bitmap): IntArray {
        val small = Bitmap.createScaledBitmap(bmp, 64, 48, true)
        val px = IntArray(64 * 48)
        small.getPixels(px, 0, 64, 0, 0, 64, 48)
        val h = IntArray(24) // 8 bins each for R, G, B
        px.forEach { c ->
            h[(android.graphics.Color.red(c) shr 5)]++
            h[8 + (android.graphics.Color.green(c) shr 5)]++
            h[16 + (android.graphics.Color.blue(c) shr 5)]++
        }
        if (small !== bmp) small.recycle()
        val total = h.sum().toFloat().coerceAtLeast(1f)
        return IntArray(h.size) { (h[it] * 1000 / total).toInt() }
    }

    private fun distance(a: IntArray, b: IntArray): Double {
        var sum = 0
        for (i in a.indices) sum += abs(a[i] - b[i])
        return sum / (a.size * 1000.0)
    }

    private fun clock(ms: Long): String = String.format(
        Locale.US, "%02d:%02d:%02d", ms / 3_600_000, (ms % 3_600_000) / 60_000, (ms % 60_000) / 1000
    )

    private fun durationMs(path: String): Long {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(path)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (t: Throwable) {
            0L
        } finally {
            runCatching { mmr.release() }
        }
    }

    // --------------------------------------------------------------- subtitles

    /**
     * Visible text/captions → an SRT file. If the video has no burned-in captions
     * this says so instead of inventing lines; it cannot decode an embedded subtitle
     * track (that needs a demuxer).
     */
    fun subtitles(path: String): String {
        if (!prefs.isConfigured) return "ব্যর্থ: API কী নেই"
        val raw = framesAsk(path, "subtitles")
        if (raw.startsWith("ব্যর্থ")) return raw
        val text = raw.substringAfter("\n", raw)
        if (text.contains("কোনো লেখা পাওয়া যায়নি")) {
            return "আংশিক: ভিডিওতে দৃশ্যমান কোনো লেখা/সাবটাইটেল পাওয়া যায়নি।\n" +
                "এমবেড করা সাবটাইটেল ট্র্যাক বের করতে ffmpeg দরকার (Termux-এ হলে করবো):\n" +
                "ffmpeg -i '$path' -map 0:s:0 subs.srt"
        }
        val srt = toSrt(text, durationMs(path))
        val out = File(dir, "subs-${System.currentTimeMillis() / 1000}.srt")
        return try {
            FileOutputStream(out).use { it.write(srt.toByteArray()) }
            "সফল: সাবটাইটেল ফাইল (.srt) তৈরি হয়েছে\nফাইল: ${out.absolutePath}\n\n$srt"
        } catch (t: Throwable) {
            "ব্যর্থ: SRT লেখা যায়নি (${t.message})"
        }
    }

    /** Turns "mm:ss text" style model output into numbered SRT blocks. */
    private fun toSrt(text: String, durationMs: Long): String {
        val lines = text.lines().map { it.trim() }
            .map { it.removePrefix("•").removePrefix("-").trim() }
            .filter { it.isNotBlank() && it.length > 1 }
        if (lines.isEmpty()) return "1\n00:00:00,000 --> 00:00:05,000\n(কোনো লেখা পাওয়া যায়নি)\n"
        val span = (if (durationMs > 0) durationMs else lines.size * 4000L) / lines.size
        return buildString {
            lines.forEachIndexed { i, l ->
                val start = span * i
                val end = start + (span - 200).coerceAtLeast(800)
                appendLine(i + 1)
                appendLine("${clock(start)},000 --> ${clock(end)},000")
                appendLine(l)
                appendLine()
            }
        }
    }

    // -------------------------------------------------------------------- edit

    /**
     * Editing is offered as a real, runnable command rather than a false success.
     * With Termux + ffmpeg installed the terminal agent can execute it directly.
     */
    fun trimHint(path: String, goal: String = ""): String {
        val f = File(path)
        if (!f.exists()) return "ব্যর্থ: ভিডিও পাওয়া যায়নি — $path"
        val dur = durationMs(path)
        val name = f.nameWithoutExtension
        val ext = f.extension.ifBlank { "mp4" }
        val out = File(dir, "$name-edit.${if (ext == "3gp") "mp4" else ext}")
        val (ss, to) = parseGoalRange(goal, dur)
        val cmd = "ffmpeg -i '$path' -ss $ss -to $to -c:v libx264 -c:a aac -preset veryfast '${out.absolutePath}'"
        val alt = "ffmpeg -i '$path' -vf \"scale=720:-2\" -c:a copy '${File(dir, "$name-720.$ext").absolutePath}'"
        return buildString {
            appendLine("আংশিক: ভিডিও এডিট অন-ডিভাইসে করা যায় না (রি-এনকোডিংয়ের জন্য কোডেক পাইপলাইন দরকার) — মিথ্যা ফাইল বানানো হবে না।")
            appendLine("Termux + ffmpeg থাকলে এই কমান্ডে কাটা যাবে:")
            appendLine(cmd)
            appendLine("আকার কমাতে:")
            appendLine(alt)
            append("দৈর্ঘ্য: ${if (dur > 0) clock(dur) else "অজানা"} · লক্ষ্য: ${if (goal.isBlank()) "ডিফল্ট (প্রথম ৩০ সেকেন্ড)" else goal.take(60)}")
        }
    }

    private fun parseGoalRange(goal: String, durationMs: Long): Pair<String, String> {
        val nums = Regex("(\\d+)\\s*(min|m|মিনিট)?").findAll(goal).map { it.groupValues[1].toInt() }.toList()
        return when {
            nums.size >= 2 -> clock(nums[0] * 1000L) to clock(nums[1] * 1000L)
            nums.size == 1 -> "00:00:00" to clock((nums[0] * 1000L).coerceAtMost(durationMs.takeIf { it > 0 } ?: 60_000))
            else -> "00:00:00" to clock(minOf(30_000, if (durationMs > 0) durationMs else 30_000))
        }
    }

    /** Grabs a poster frame — real on-device work, used for reports and sharing. */
    fun posterFrame(path: String, atMs: Long = -1): String {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(path)
            val dur = durationMs(path)
            val at = if (atMs >= 0) atMs else if (dur > 0) dur / 4 else 0
            val bmp = mmr.getFrameAtTime(at * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: return "ব্যর্থ: ফ্রেম ডিকোড করা যায়নি"
            val out = File(dir, "frame-${System.currentTimeMillis() / 1000}.jpg")
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            bmp.recycle()
            "সফল: ভিডিওর ফ্রেম (${clock(at)}) সংরক্ষিত হয়েছে\nফাইল: ${out.absolutePath}"
        } catch (t: Throwable) {
            ErrorCenter.logThrow("media", "পোস্টার ফ্রেম ব্যর্থ", t)
            "ব্যর্থ: ফ্রেম বের করা যায়নি (${t.message})"
        } finally {
            runCatching { mmr.release() }
        }
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
                        ErrorCenter.log("media", "$what ব্যর্থ", lastError)
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
                ErrorCenter.log("media", "$what নেটওয়ার্ক ত্রুটি", lastError)
            }
        }
        return null
    }

    private fun humanSize(b: Long): String = when {
        b > 1_048_576 -> String.format(Locale.US, "%.1f MB", b / 1_048_576.0)
        b > 1024 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
        else -> "$b B"
    }

    fun capabilities(): String = buildString {
        appendLine("ভিডিও ল্যাব:")
        appendLine("• info: দৈর্ঘ্য/রেজল্যুশন/ফ্রেমরেট/ঘূর্ণন (অফলাইন)")
        appendLine("• scenes: দৃশ্য-পরিবর্তনের সময় (অফলাইন, হিস্টোগ্রাম)")
        appendLine("• posterFrame: একটি ফ্রেম ছবি হিসেবে (অফলাইন)")
        appendLine("• summarize: ভিডিওতে কী হচ্ছে (মডেল; বড় ফাইলে নমুনা ফ্রেম)")
        appendLine("• subtitles: দৃশ্যমান লেখা → .srt (মডেল)")
        appendLine("• trimHint: কাটার ffmpeg কমান্ড (এডিট অন-ডিভাইসে সম্ভব নয় — সত্যিটাই বলে)")
        append("API কী: ${if (prefs.isConfigured) "আছে" else "নেই"}")
    }

    companion object {
        private const val MAX_INLINE_BYTES = 18L * 1024 * 1024
        private const val SAMPLE_STEPS = 28
        private const val FRAME_COUNT = 6
        val MODELS = listOf("gemini-2.5-flash", "gemini-3-pro", "gemini-2.5-pro")
    }
}
