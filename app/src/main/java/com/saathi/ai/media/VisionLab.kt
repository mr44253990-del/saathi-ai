package com.saathi.ai.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Image intelligence (Phase 9).
 *
 * Two kinds of work live here on purpose:
 *  - **On-device pixel work** that needs no network and is therefore always
 *    available: enhancement (upscale + contrast + sharpen), background removal
 *    (corner flood-fill with tolerance), histogram/size facts.
 *  - **Model work** for things pixels alone cannot answer: OCR of handwriting,
 *    object descriptions, comparing two images. When the API key is missing or the
 *    call fails, the report says so instead of returning invented text.
 */
class VisionLab(private val context: Context) {

    private val prefs = SecurePrefs(context)
    private val mu = MediaUnderstanding(context)

    private val outDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "edited").apply { mkdirs() }

    private val http by lazy {
        OkHttpClient.Builder()
            .callTimeout(110, TimeUnit.SECONDS)
            .readTimeout(110, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // ------------------------------------------------------------------- model

    /** Reads every piece of text visible in the image, in reading order. */
    fun ocr(path: String): String = askModel(
        path,
        "এই ছবিতে যা যা লেখা আছে সব হুবহু বের করো। পড়ার ক্রম অনুসারে লাইন ধরে লেখো। " +
            "লেখা না থাকলে স্পষ্ট করে লেখো «কোনো লেখা পাওয়া যায়নি»। নিজের থেকে কিছু যোগ করো না।"
    ).let { report ->
        if (report.startsWith("ব্যর্থ")) report
        else "সফল: ছবি থেকে লেখা বের করা হয়েছে\n$report"
    }

    /** What is in the image, where it is, and how confident the reading is. */
    fun detect(path: String): String = askModel(
        path,
        "এই ছবিতে কী কী আছে তা শনাক্ত করো। প্রতিটি বস্তুর জন্য লেখো: নাম, অবস্থান " +
            "(বাম/মাঝ/ডান, উপর/মাঝ/নিচ) এবং আনুমানিক আয়তন (ছবির শতকরা কত ভাগ)। " +
            "অনিশ্চিত হলে «সম্ভবত» লিখো — অনুমানকে নিশ্চিত তথ্যের মতো দেখিও না।"
    ).let { if (it.startsWith("ব্যর্থ")) it else "সফল: বস্তু শনাক্ত করা হয়েছে\n$it" }

    /** Side-by-side comparison of two images. */
    fun compare(pathA: String, pathB: String, question: String = "পার্থক্যগুলো কী?"): String {
        val a = File(pathA); val b = File(pathB)
        if (!a.exists() || !b.exists()) return "ব্যর্থ: দুটি ছবিই পাওয়া যায়নি"
        return askModelTwo(a, b, question)
    }

    private fun askModel(path: String, prompt: String): String {
        if (!prefs.isConfigured) {
            return "ব্যর্থ: API কী নেই — ছবির লেখা/বস্তু পড়তে মডেল দরকার। " +
                "অফলাইনে শুধু পিক্সেল এডিট (উজ্জ্বলতা/কাটা/ব্যাকগ্রাউন্ড) সম্ভব।"
        }
        val bmp = mu.loadBitmap(path) ?: return "ব্যর্থ: ছবি ডিকোড করা যায়নি — $path"
        val small = mu.downscale(bmp, 1280)
        val jpeg = mu.toJpeg(small, 88)
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray()
                    .put(JSONObject().put("text", prompt))
                    .put(JSONObject().put("inlineData", JSONObject()
                        .put("mimeType", "image/jpeg")
                        .put("data", mu.toBase64(jpeg))))
                )
            }))
            put("generationConfig", JSONObject().put("temperature", 0.1))
        }
        small.recycle()
        if (small !== bmp) bmp.recycle()
        return call(body)
    }

    private fun askModelTwo(a: File, b: File, prompt: String): String {
        if (!prefs.isConfigured) return "ব্যর্থ: API কী নেই — দুটি ছবি মিলিয়ে দেখা সম্ভব নয়"
        val ba = mu.loadBitmap(a.absolutePath)?.let { mu.downscale(it, 900) } ?: return "ব্যর্থ: প্রথম ছবি ডিকোড হয়নি"
        val bb = mu.loadBitmap(b.absolutePath)?.let { mu.downscale(it, 900) } ?: return "ব্যর্থ: দ্বিতীয় ছবি ডিকোড হয়নি"
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray()
                    .put(JSONObject().put("text", prompt))
                    .put(JSONObject().put("inlineData", JSONObject()
                        .put("mimeType", "image/jpeg").put("data", mu.toBase64(mu.toJpeg(ba, 85)))))
                    .put(JSONObject().put("text", "প্রথম ছবি উপরে, দ্বিতীয় ছবি নিচে।"))
                    .put(JSONObject().put("inlineData", JSONObject()
                        .put("mimeType", "image/jpeg").put("data", mu.toBase64(mu.toJpeg(bb, 85)))))
                )
            }))
        }
        ba.recycle(); bb.recycle()
        return call(body).let { if (it.startsWith("ব্যর্থ")) it else "সফল: দুটি ছবি মিলিয়ে দেখা হয়েছে\n$it" }
    }

    private fun call(body: JSONObject): String {
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
                        if (resp.code == 404 || resp.code == 400) return@use
                        ErrorCenter.log("media", "ভিশন কল ব্যর্থ", lastError)
                        return "ব্যর্থ: ছবি বিশ্লেষণ করা যায়নি ($lastError)"
                    }
                    val parts = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts")
                    val sb = StringBuilder()
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            parts.optJSONObject(i)?.optString("text")?.let { if (it.isNotBlank()) sb.appendLine(it) }
                        }
                    }
                    return sb.toString().trim().ifBlank { "ব্যর্থ: মডেল কোনো উত্তর দেয়নি" }
                }
            } catch (t: Throwable) {
                lastError = "$model → ${t.message}"
                ErrorCenter.log("media", "ভিশন নেটওয়ার্ক ত্রুটি", lastError)
            }
        }
        return "ব্যর্থ: ছবি বিশ্লেষণ করা যায়নি ($lastError)"
    }

    // --------------------------------------------------------------- on-device

    /**
     * Enhancement without any network: mild upscale, contrast stretch and a 3x3
     * sharpen kernel. Honest about what it is — a classic filter chain, not a
     * super-resolution model.
     */
    fun enhance(path: String, targetSide: Int = 1600): String {
        val bmp = mu.loadBitmap(path) ?: return "ব্যর্থ: ছবি ডিকোড করা যায়নি — $path"
        return try {
            val scale = min(2.0f, targetSide.toFloat() / max(bmp.width, bmp.height))
            val w = (bmp.width * max(1f, scale)).toInt()
            val h = (bmp.height * max(1f, scale)).toInt()
            val up = Bitmap.createScaledBitmap(bmp, w, h, true)
            val contrast = contrastStretch(up, 1.18f)
            val sharp = sharpen(contrast, 0.55f)
            val srcW = bmp.width
            val srcH = bmp.height
            val out = File(outDir, "enhanced-${System.currentTimeMillis()}.jpg")
            FileOutputStream(out).use { sharp.compress(Bitmap.CompressFormat.JPEG, 94, it) }
            if (sharp !== contrast) sharp.recycle()
            if (contrast !== up) contrast.recycle()
            if (up !== bmp) up.recycle()
            bmp.recycle()
            "সফল: ছবি উন্নত করা হয়েছে (অফলাইন ফিল্টার — আপস্কেল + কনট্রাস্ট + শার্পেন)\n" +
                "আগে: ${srcW}x${srcH} → পরে: ${w}x${h}\nফাইল: ${out.absolutePath}\n" +
                "নোট: এটি AI সুপার-রেজোলিউশন নয়; খুব ঝাপসা ছবি পুরোপুরি পরিষ্কার হবে না।"
        } catch (t: Throwable) {
            ErrorCenter.logThrow("media", "এনহ্যান্স ব্যর্থ", t)
            bmp.recycle()
            "ব্যর্থ: ছবি উন্নত করা যায়নি (${t.message})"
        }
    }

    /**
     * Background removal by flood-filling from the four corners with a colour
     * tolerance. Works well on plain/uniform backgrounds (product shots, scanned
     * pages, selfies against a wall) and says plainly when the background is too
     * busy for this method.
     */
    fun removeBackground(path: String, tolerance: Int = 42): String {
        val src = mu.loadBitmap(path) ?: return "ব্যর্থ: ছবি ডিকোড করা যায়নি — $path"
        return try {
            val w = src.width; val h = src.height
            val px = IntArray(w * h)
            src.getPixels(px, 0, w, 0, 0, w, h)
            val visited = BooleanArray(w * h)
            val queue = IntArray(w * h)
            var head = 0; var tail = 0
            val corners = listOf(0, w - 1, (h - 1) * w, (h - 1) * w + w - 1)
            val refs = corners.map { px[it] }

            fun enqueue(i: Int) {
                if (!visited[i]) {
                    visited[i] = true
                    queue[tail++] = i
                }
            }
            corners.forEach { enqueue(it) }
            var removed = 0
            while (head < tail) {
                val i = queue[head++]
                val x = i % w; val y = i / w
                px[i] = Color.TRANSPARENT
                removed++
                // 4-neighbour flood fill with tolerance against any corner colour.
                if (x > 0 && !visited[i - 1] && close(px[i - 1], refs, tolerance)) enqueue(i - 1)
                if (x < w - 1 && !visited[i + 1] && close(px[i + 1], refs, tolerance)) enqueue(i + 1)
                if (y > 0 && !visited[i - w] && close(px[i - w], refs, tolerance)) enqueue(i - w)
                if (y < h - 1 && !visited[i + w] && close(px[i + w], refs, tolerance)) enqueue(i + w)
            }
            val outBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            outBmp.setPixels(px, 0, w, 0, 0, w, h)
            val out = File(outDir, "nobg-${System.currentTimeMillis()}.png")
            FileOutputStream(out).use { outBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            src.recycle(); outBmp.recycle()
            val pct = removed * 100 / (w * h)
            val verdict = when {
                pct < 5 -> "আংশিক: খুব কম অংশ (${pct}%) সরানো গেছে — ব্যাকগ্রাউন্ড সম্ভবত নানা রঙের। " +
                    "tolerance বাড়িয়ে আবার চেষ্টা করতে বলো, অথবা API এডিট পথ ব্যবহার করো।"
                pct > 85 -> "আংশিক: ${pct}% অংশ সরে গেছে — সম্ভবত মূল বিষয়ও মুছে গেছে। tolerance কমাও।"
                else -> "সফল: ব্যাকগ্রাউন্ড সরানো হয়েছে (${pct}% অংশ স্বচ্ছ)"
            }
            "$verdict\nফাইল (PNG, স্বচ্ছ ব্যাকগ্রাউন্ড): ${out.absolutePath}"
        } catch (t: Throwable) {
            ErrorCenter.logThrow("media", "ব্যাকগ্রাউন্ড রিমুভ ব্যর্থ", t)
            src.recycle()
            "ব্যর্থ: ব্যাকগ্রাউন্ড সরানো যায়নি (${t.message})"
        }
    }

    private fun close(color: Int, refs: List<Int>, tol: Int): Boolean = refs.any { r ->
        abs(Color.red(color) - Color.red(r)) <= tol &&
            abs(Color.green(color) - Color.green(r)) <= tol &&
            abs(Color.blue(color) - Color.blue(r)) <= tol
    }

    /** Simple histogram stretch — lifts flat, washed-out photos. */
    private fun contrastStretch(bmp: Bitmap, amount: Float): Bitmap {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val r = ((Color.red(c) - 128) * amount + 128).toInt().coerceIn(0, 255)
            val g = ((Color.green(c) - 128) * amount + 128).toInt().coerceIn(0, 255)
            val b = ((Color.blue(c) - 128) * amount + 128).toInt().coerceIn(0, 255)
            px[i] = Color.argb(Color.alpha(c), r, g, b)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /** 3x3 unsharp mask. */
    private fun sharpen(bmp: Bitmap, strength: Float): Bitmap {
        val w = bmp.width; val h = bmp.height
        val src = IntArray(w * h)
        bmp.getPixels(src, 0, w, 0, 0, w, h)
        val out = src.copyOf()
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                for (shift in intArrayOf(16, 8, 0)) {
                    val c = (src[i] shr shift) and 0xFF
                    val n = ((src[i - 1] shr shift) and 0xFF) + ((src[i + 1] shr shift) and 0xFF) +
                        ((src[i - w] shr shift) and 0xFF) + ((src[i + w] shr shift) and 0xFF)
                    val v = (c + strength * (4 * c - n) / 4f).toInt().coerceIn(0, 255)
                    out[i] = (out[i] and (0xFF shl shift).inv()) or (v shl shift)
                }
            }
        }
        val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        res.setPixels(out, 0, w, 0, 0, w, h)
        return res
    }

    /** Facts that need no model: size, brightness, dominant colours, aspect. */
    fun facts(path: String): String {
        val f = mu.describeImage(path) ?: return "ব্যর্থ: ছবি পড়া যায়নি — $path"
        return "সফল: ছবির তথ্য\n" + mu.describe(f) + "\nফাইল: $path"
    }

    /** Draws measured text onto an image — used for posters and captions. */
    fun caption(path: String, text: String, atBottom: Boolean = true): String {
        val bmp = mu.loadBitmap(path) ?: return "ব্যর্থ: ছবি ডিকোড করা যায়নি"
        val out = com.saathi.ai.create.ArtEngine.withText(bmp, text, if (atBottom) 0xFFFFFFFF.toInt() else 0xFF101018.toInt())
        val f = File(outDir, "caption-${System.currentTimeMillis()}.jpg")
        FileOutputStream(f).use { out.compress(Bitmap.CompressFormat.JPEG, 94, it) }
        bmp.recycle(); if (out !== bmp) out.recycle()
        return "সফল: ছবিতে লেখা বসানো হয়েছে\nফাইল: ${f.absolutePath}"
    }

    /** Everything this lab can do, for the model and the settings screen. */
    fun capabilities(): String = buildString {
        appendLine("ছবি ল্যাব — যা যা পারি:")
        appendLine("• ocr: ছবি থেকে লেখা হুবহু বের করা (মডেল দরকার)")
        appendLine("• detect: কী কী আছে, কোথায়, কতটা জায়গা জুড়ে (মডেল দরকার)")
        appendLine("• compare: দুটি ছবির পার্থক্য (মডেল দরকার)")
        appendLine("• enhance: আপস্কেল + কনট্রাস্ট + শার্পেন (অফলাইন)")
        appendLine("• removeBackground: কোণা থেকে ফ্লাড-ফিল করে স্বচ্ছ PNG (অফলাইন)")
        appendLine("• facts: আকার/উজ্জ্বলতা/প্রধান রং (অফলাইন)")
        appendLine("• caption: ছবির উপর লেখা বসানো (অফলাইন)")
        append("API কী: ${if (prefs.isConfigured) "আছে" else "নেই — শুধু অফলাইন কাজ চলবে"}")
    }

    /** Saves a bitmap to the lab's output folder (used by other modules). */
    fun save(bmp: Bitmap, prefix: String, png: Boolean = false): File {
        val f = File(outDir, "$prefix-${System.currentTimeMillis()}.${if (png) "png" else "jpg"}")
        FileOutputStream(f).use {
            bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 94, it)
        }
        return f
    }

    /** Blank canvas helper for other generators. */
    fun blank(w: Int, h: Int, color: Int = Color.WHITE): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(b).drawColor(color)
        return b
    }

    fun paintOf(color: Int = Color.BLACK, size: Float = 28f): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
    }

    fun sizeText(): String = String.format(Locale.US, "আউটপুট ফোল্ডার: %s", outDir.absolutePath)

    companion object {
        val MODELS = listOf("gemini-2.5-flash", "gemini-3-pro", "gemini-2.5-pro")

        /** Quick check whether a file looks like an image we can decode. */
        fun looksLikeImage(name: String): Boolean {
            val ext = name.substringAfterLast('.', "").lowercase()
            return ext in listOf("jpg", "jpeg", "png", "webp", "bmp", "heic")
        }

        /** Base64 of a file for callers that build their own request. */
        fun base64Of(file: File): String? = try {
            Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        } catch (t: Throwable) {
            null
        }
    }
}
