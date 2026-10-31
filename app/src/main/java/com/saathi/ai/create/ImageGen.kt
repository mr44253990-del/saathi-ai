package com.saathi.ai.create

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.saathi.ai.data.SecurePrefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Jimi draws by itself.
 *
 * Two engines, and the order matters:
 *  • [ArtEngine] — pure Kotlin canvas drawing, offline, instant. Good for shapes,
 *    posters, mandalas, scenes, diagrams; always available.
 *  • **This class** — real image generation through the Gemini image model with the
 *    same API key the Live session uses (`generateContent` with
 *    `responseModalities: [TEXT, IMAGE]`, image returned as base64 `inlineData`).
 *    This is what produces photographic/artwork results and, importantly, *edits an
 *    existing photo* when the instruction and the image are sent together.
 *
 * Every failure is reported verbatim (HTTP code + API message) so Jimi never
 * claims an image was made when it was not, and the model list is tried in order
 * because the image model name has moved between releases.
 */
class ImageGen(private val context: Context) {

    private val prefs = SecurePrefs(context)

    private val http by lazy {
        OkHttpClient.Builder()
            .callTimeout(150, TimeUnit.SECONDS)
            .readTimeout(150, TimeUnit.SECONDS)
            .connectTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val outDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "art").apply { mkdirs() }

    /** Text → image. Returns an honest report including the saved path. */
    fun generate(prompt: String, aspect: String = "1:1"): String {
        if (!prefs.isConfigured) return "API Key নেই — সেটিংসে কী দাও, তারপর ছবি বানাতে পারব।"
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }))
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
                put("imageConfig", JSONObject().put("aspectRatio", normalizeAspect(aspect)))
            })
        }
        return call(body, "ছবি বানাচ্ছি: ${prompt.take(60)}")
    }

    /** Instruction + existing image → edited image (the "edit anything" path). */
    fun edit(imagePath: String, instruction: String): String {
        if (!prefs.isConfigured) return "API Key নেই — সেটিংসে কী দাও।"
        val src = File(imagePath)
        if (!src.exists()) {
            // also accept content:// and file:// style strings the model often passes
            val alt = imagePath.removePrefix("file://")
            if (!File(alt).exists()) return "ছবিটা পাওয়া যায়নি: $imagePath"
            return edit(alt, instruction)
        }
        val bytes = runCatching { src.readBytes() }.getOrNull()
            ?: return "ছবি পড়া যায়নি"
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val scaled = if (bmp != null && maxOf(bmp.width, bmp.height) > 1024) {
            val k = 1024f / maxOf(bmp.width, bmp.height)
            Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true)
        } else bmp
        if (scaled == null) return "ছবি ডিকোড করা যায়নি"

        val bos = java.io.ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 92, bos)
        val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)

        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray()
                    .put(JSONObject().put("text", instruction))
                    .put(JSONObject().put("inlineData", JSONObject()
                        .put("mimeType", "image/png")
                        .put("data", b64)))
                )
            }))
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
            })
        }
        return call(body, "এডিট করছি: ${instruction.take(60)}")
    }

    // ------------------------------------------------------------------ plumbing

    private fun call(body: JSONObject, what: String): String {
        val key = prefs.apiKey
        var lastError = ""
        for (model in MODELS) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            val request = Request.Builder()
                .url(url)
                .header("x-goog-api-key", key)
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            try {
                http.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        lastError = "$model → HTTP ${resp.code}: ${errorOf(text)}"
                        // 404 means this model name is not available for this key; try next
                        if (resp.code == 404 || resp.code == 400) return@use
                        return "$what\nব্যর্থ: $lastError"
                    }
                    val image = extractImage(text)
                    val note = extractText(text)
                    if (image == null) {
                        lastError = "$model → ছবি ফেরত দেয়নি" +
                            (if (note.isNotBlank()) " (বলেছে: ${note.take(160)})" else "")
                        return@use
                    }
                    val file = File(outDir, "jimi-${System.currentTimeMillis()}.png")
                    file.writeBytes(image)
                    return buildString {
                        append("$what\nসফল: ছবি তৈরি হয়েছে: ${file.absolutePath} (${image.size / 1024} KB, মডেল $model)")
                        if (note.isNotBlank()) append("\nমডেলের কথা: ").append(note.take(300))
                        append("\nএখন share_file দিয়ে শেয়ার করতে পারো, বা set_wallpaper দিয়ে ওয়ালপেপার বানাও।")
                    }
                }
            } catch (t: Throwable) {
                lastError = "$model → ${t.message}"
            }
        }
        return "$what\nব্যর্থ: ছবি তৈরি করা যায়নি। চেষ্টা করেছি ${MODELS.joinToString(", ")}।\nশেষ সমস্যা: $lastError"
    }

    private fun extractImage(json: String): ByteArray? {
        return runCatching {
            val o = JSONObject(json)
            val parts = o.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts") ?: return null
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                val data = inline?.optString("data").orEmpty()
                if (data.length > 200) {
                    return Base64.decode(data, Base64.DEFAULT)
                }
            }
            null
        }.getOrNull()
    }

    private fun extractText(json: String): String = runCatching {
        val o = JSONObject(json)
        val parts = o.optJSONArray("candidates")
            ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val t = parts.optJSONObject(i)?.optString("text").orEmpty()
                if (t.isNotBlank()) sb.append(t).append(' ')
            }
        }
        sb.toString().trim()
    }.getOrDefault("")

    private fun errorOf(json: String): String = runCatching {
        val o = JSONObject(json)
        o.optJSONObject("error")?.optString("message")?.take(220) ?: json.take(220)
    }.getOrDefault(json.take(220))

    private fun normalizeAspect(a: String): String {
        val v = a.trim()
        val allowed = listOf("1:1", "2:3", "3:2", "3:4", "4:3", "4:5", "5:4", "9:16", "16:9", "21:9")
        return allowed.firstOrNull { it == v } ?: "1:1"
    }

    companion object {
        /** Tried in order: the image model name has changed across releases. */
        val MODELS = listOf(
            "gemini-2.5-flash-image",
            "gemini-3.1-flash-image",
            "gemini-2.0-flash-exp-image-generation"
        )
    }
}
