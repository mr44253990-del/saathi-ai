package com.saathi.ai.media

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Audio generation (Phase 20): the Gemini audio model speaks, and Jimi plays it
 * in the background.
 *
 * How it works: `generateContent` with `responseModalities: ["AUDIO"]` returns
 * base64 **PCM, 24 kHz, 16-bit, mono**. We wrap it in a WAV header, save it under
 * `external-files/audio`, and hand the path to [AudioPlaybackService], which keeps
 * playing with a notification when the app is closed or the screen is off.
 *
 * Two voices can speak one script (`multiSpeakerVoiceConfig`) — that is how Jimi
 * tells a story or reads a dialogue instead of flattening it to one voice.
 *
 * Offline fallbacks, because audio must not depend on the network alone:
 *  - Android's own TextToSpeech synthesised to a file;
 *  - procedurally generated ambience (rain, waves, brown noise, focus tone) that
 *    needs no model at all.
 */
class AudioGen(private val context: Context) {

    private val prefs = SecurePrefs(context)
    private val sampleRate = 24_000

    private val outDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "audio").apply { mkdirs() }

    private val http by lazy {
        OkHttpClient.Builder()
            .callTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // ------------------------------------------------------------------ voices

    /** The 30 prebuilt voices, with a Bangla hint about each one's character. */
    fun voices(): String = buildString {
        appendLine("Gemini অডিও মডেলের ৩০টি কণ্ঠ (voiceName):")
        VOICE_HINTS.forEach { (name, hint) -> appendLine("• $name — $hint") }
        appendLine("বর্তমান পছন্দ: ${prefs.ttsVoice}")
        append("মডেল: ${prefs.ttsModel.ifBlank { "স্বয়ংক্রিয় (${MODELS.joinToString(", ")})" }}")
    }

    // ------------------------------------------------------------------- speak

    /**
     * Single-speaker speech. Style is written into the prompt as plain language,
     * exactly as the TTS model expects ("উৎসবের আমেজে পড়ো: …").
     */
    fun speak(text: String, voice: String = prefs.ttsVoice, style: String = ""): String {
        if (text.isBlank()) return "ব্যর্থ: কী বলবে সেটা খালি"
        if (!prefs.isConfigured) return localFallback(text, "API কী নেই")
        val spoken = if (style.isBlank()) text else "$style: $text"
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", spoken)))
            }))
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().put("AUDIO"))
                put("speechConfig", JSONObject().put("voiceConfig", JSONObject()
                    .put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice))))
            })
        }
        return synth(body, "কণ্ঠ $voice", text)
    }

    /**
     * Two-voice script. The speaker names in the transcript must match the config,
     * so we parse "Name: line" pairs and hand the first two names to the API.
     */
    fun dialogue(script: String): String {
        if (!prefs.isConfigured) return localFallback(script, "API কী নেই")
        val lines = script.lines().map { it.trim() }.filter { it.contains(":") }
        val speakers = lines.map { it.substringBefore(":").trim() }.distinct().take(2)
        if (speakers.size < 2) {
            // Not a real dialogue: speak it as one voice instead of inventing a second.
            return speak(script, voice = prefs.ttsVoice)
        }
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put(
                    "text", "TTS the following conversation between ${speakers[0]} and ${speakers[1]}:\n$script"
                )))
            }))
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().put("AUDIO"))
                put("speechConfig", JSONObject().put("multiSpeakerVoiceConfig", JSONObject()
                    .put("speakerVoiceConfigs", JSONArray()
                        .put(JSONObject()
                            .put("speaker", speakers[0])
                            .put("voiceConfig", JSONObject().put("prebuiltVoiceConfig",
                                JSONObject().put("voiceName", DIALOGUE_VOICES[0]))))
                        .put(JSONObject()
                            .put("speaker", speakers[1])
                            .put("voiceConfig", JSONObject().put("prebuiltVoiceConfig",
                                JSONObject().put("voiceName", DIALOGUE_VOICES[1]))))
                    )))
            })
        }
        return synth(body, "দুই কণ্ঠের সংলাপ (${speakers.joinToString(" ও ")})", script)
    }

    /** Sends the request, decodes PCM, writes WAV and reports the path honestly. */
    private fun synth(body: JSONObject, what: String, source: String): String {
        var lastError = ""
        for (model in models()) {
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
                        lastError = "$model → HTTP ${resp.code}: ${errorOf(raw)}"
                        if (resp.code == 404 || resp.code == 400) return@use
                        ErrorCenter.log("audio", "TTS কল ব্যর্থ", lastError)
                        return localFallback(source, lastError)
                    }
                    val parts = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts")
                    var b64 = ""
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val inline = parts.optJSONObject(i)?.optJSONObject("inlineData")
                            val data = inline?.optString("data")
                            if (!data.isNullOrBlank()) {
                                b64 = data
                                break
                            }
                        }
                    }
                    if (b64.isBlank()) {
                        lastError = "$model → অডিও ডেটা ফেরত দেয়নি"
                        ErrorCenter.log("audio", "TTS-এ অডিও নেই", lastError)
                        return@use
                    }
                    val pcm = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    val file = writeWav(pcm, sampleRate, "jimi-voice")
                    val seconds = pcm.size / (sampleRate * 2.0)
                    ErrorCenter.info("audio", "অডিও তৈরি: ${file.name} (${String.format(Locale.US, "%.1f", seconds)} সে.)")
                    return "সফল: $what তৈরি হয়েছে\n" +
                        "ফাইল: ${file.absolutePath}\n" +
                        "দৈর্ঘ্য: ${String.format(Locale.US, "%.1f", seconds)} সেকেন্ড · মডেল: $model · কণ্ঠ-নমুনা 24 kHz/16-bit/mono"
                }
            } catch (t: Throwable) {
                lastError = "$model → ${t.message}"
                ErrorCenter.log("audio", "TTS নেটওয়ার্ক ত্রুটি", lastError)
            }
        }
        return localFallback(source, lastError.ifBlank { "কোনো TTS মডেল সাড়া দেয়নি" })
    }

    private fun models(): List<String> {
        val custom = prefs.ttsModel.trim()
        return (if (custom.isNotBlank()) listOf(custom) else emptyList()) + MODELS
    }

    /** Android's own TTS, synthesised to a file — works with no API key at all. */
    private fun localFallback(text: String, reason: String): String {
        return try {
            val file = File(outDir, "jimi-local-${System.currentTimeMillis()}.wav")
            val latch = CountDownLatch(1)
            var done = false
            val tts = TextToSpeech(context) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    latch.countDown()
                    return@TextToSpeech
                }
            }
            tts.language = Locale("bn")
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    done = true
                    latch.countDown()
                }
                @Deprecated("deprecated in Java")
                override fun onError(utteranceId: String?) {
                    latch.countDown()
                }
            })
            // synthesizeToFile(File) is deprecated in favour of the ParcelFileDescriptor
            // variant, but the File form still works on every API level we support
            // (minSdk 26) and avoids leaking a descriptor if synthesis fails.
            tts.synthesizeToFile(text, null, file, "jimi-local")
            latch.await(45, TimeUnit.SECONDS)
            val size = if (file.exists()) file.length() else 0L
            runCatching { tts.shutdown() }
            if (done && size > 1000) {
                "আংশিক: Gemini অডিও মডেল পাওয়া যায়নি ($reason) — তাই ফোনের নিজস্ব বাংলা কণ্ঠ দিয়ে বানিয়েছি।\n" +
                    "ফাইল: ${file.absolutePath}\n(মান Gemini-র চেয়ে কম, কিন্তু অফলাইনেও কাজ করে)"
            } else {
                if (file.exists()) file.delete()
                "ব্যর্থ: অডিও বানানো যায়নি — কারণ: $reason; ফোনের নিজস্ব TTS-ও ফাইল তৈরি করতে পারেনি"
            }
        } catch (t: Throwable) {
            ErrorCenter.logThrow("audio", "লোকাল TTS ব্যর্থ", t)
            "ব্যর্থ: অডিও বানানো যায়নি ($reason) এবং লোকাল TTS-ও ব্যর্থ (${t.message})"
        }
    }

    // ----------------------------------------------------------------- ambience

    /**
     * Procedurally generated ambience — no network, no model, no license issues.
     * Kinds: rain, waves, brown/pink noise, focus tone, night.
     */
    fun ambient(kind: String, seconds: Int = 120): String {
        val secs = seconds.coerceIn(10, 900)
        val low = kind.lowercase()
        val type = when {
            low.contains("বৃষ্টি") || low.contains("rain") -> "rain"
            low.contains("ঢেউ") || low.contains("সমুদ্র") || low.contains("wave") -> "waves"
            low.contains("ফোকাস") || low.contains("মনোযোগ") || low.contains("focus") -> "focus"
            low.contains("রাত") || low.contains("night") || low.contains("ঝিঁঝি") -> "night"
            else -> "brown"
        }
        return try {
            val pcm = synthAmbience(type, secs)
            val file = writeWav(pcm, sampleRate, "jimi-ambient-$type")
            "সফল: অফলাইনে পরিবেশ-শব্দ বানানো হয়েছে ($type, $secs সেকেন্ড)\nফাইল: ${file.absolutePath}"
        } catch (t: Throwable) {
            ErrorCenter.logThrow("audio", "অ্যাম্বিয়েন্ট সিন্থ ব্যর্থ", t)
            "ব্যর্থ: পরিবেশ-শব্দ বানানো যায়নি (${t.message})"
        }
    }

    private fun synthAmbience(type: String, seconds: Int): ByteArray {
        val total = sampleRate * seconds
        val buf = ByteBuffer.allocate(total * 2).order(ByteOrder.LITTLE_ENDIAN)
        val rnd = Random(System.currentTimeMillis())
        var brown = 0.0
        var last = 0.0
        for (i in 0 until total) {
            val t = i.toDouble() / sampleRate
            val white = rnd.nextDouble(-1.0, 1.0)
            val sample: Double = when (type) {
                "rain" -> {
                    // High-passed noise + occasional droplet transients.
                    val hp = white - last
                    last = white
                    val droplet = if (rnd.nextInt(900) == 0) sin(2 * PI * 1800 * t) * exp(-((i % 900) / 90.0)) * 0.25 else 0.0
                    (hp * 0.42 + droplet) * 0.6
                }
                "waves" -> {
                    // Brown noise shaped by a slow 0.08 Hz swell.
                    brown = (brown + 0.02 * white).coerceIn(-1.0, 1.0)
                    val swell = 0.55 + 0.45 * sin(2 * PI * 0.08 * t)
                    brown * swell * 0.85
                }
                "focus" -> {
                    // Pink-ish noise plus a very quiet 432 Hz bed.
                    brown = (brown + 0.03 * white).coerceIn(-1.0, 1.0)
                    brown * 0.5 + 0.035 * sin(2 * PI * 432 * t)
                }
                "night" -> {
                    // Soft noise floor with sparse cricket chirps.
                    val chirp = if (rnd.nextInt(2400) == 0) sin(2 * PI * 4200 * t) * exp(-((i % 2400) / 160.0)) * 0.18 else 0.0
                    white * 0.06 + chirp
                }
                else -> {
                    // Brown noise: the classic sleep masker.
                    brown = (brown + 0.02 * white).coerceIn(-1.0, 1.0)
                    brown * 0.9
                }
            }
            val v = (sample.coerceIn(-1.0, 1.0) * 32767 * 0.7).toInt().toShort()
            buf.putShort(v)
        }
        return buf.array()
    }

    // ----------------------------------------------------------------- helpers

    /** PCM (16-bit mono) → WAV file with a correct 44-byte header. */
    fun writeWav(pcm: ByteArray, rate: Int, prefix: String): File {
        val file = File(outDir, "$prefix-${System.currentTimeMillis()}.wav")
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        val channels = 1
        val bits = 16
        val byteRate = rate * channels * bits / 8
        header.put("RIFF".toByteArray())
        header.putInt(36 + pcm.size)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)                 // PCM
        header.putShort(channels.toShort())
        header.putInt(rate)
        header.putInt(byteRate)
        header.putShort((channels * bits / 8).toShort())
        header.putShort(bits.toShort())
        header.put("data".toByteArray())
        header.putInt(pcm.size)
        FileOutputStream(file).use { out ->
            out.write(header.array())
            out.write(pcm)
        }
        return file
    }

    /** Duration helper the playback UI uses. */
    fun durationOf(file: File): String {
        val bytes = file.length() - 44
        if (bytes <= 0) return "0.0 সে."
        return String.format(Locale.US, "%.1f সে.", bytes / (sampleRate * 2.0))
    }

    /** Everything Jimi has generated so far, newest first. */
    fun library(): String {
        val files = outDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (files.isEmpty()) return "এখনো কোনো অডিও ফাইল বানানো হয়নি"
        return buildString {
            appendLine("অডিও লাইব্রেরি: ${files.size}টি ফাইল")
            files.take(25).forEach {
                appendLine("• ${it.name} — ${humanSize(it.length())} — ${durationOf(it)}")
            }
            append("ফোল্ডার: ${outDir.absolutePath}")
        }
    }

    private fun humanSize(b: Long): String = when {
        b > 1_048_576 -> String.format(Locale.US, "%.1f MB", b / 1_048_576.0)
        b > 1024 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
        else -> "$b B"
    }

    private fun errorOf(body: String): String = try {
        JSONObject(body).optJSONObject("error")?.optString("message")?.take(200) ?: body.take(200)
    } catch (t: Throwable) {
        body.take(200)
    }

    /** Rough loudness measure used by AudioLab's noise gate. */
    fun rms(pcm: ByteArray): Double {
        var sum = 0.0
        var n = 0
        for (i in 0 until pcm.size - 1 step 2) {
            val s = ByteBuffer.wrap(pcm, i, 2).order(ByteOrder.LITTLE_ENDIAN).short.toDouble()
            sum += s * s
            n++
        }
        return if (n == 0) 0.0 else sqrt(sum / n)
    }

    companion object {
        val MODELS = listOf(
            "gemini-2.5-flash-preview-tts",
            "gemini-2.5-pro-preview-tts"
        )

        private val DIALOGUE_VOICES = listOf("Kore", "Puck")

        val VOICE_HINTS = listOf(
            "Zephyr" to "উজ্জ্বল, প্রাণবন্ত",
            "Puck" to "চনমনে, হাসিখুশি",
            "Charon" to "তথ্যবহুল, গম্ভীর",
            "Kore" to "দৃঢ়, স্পষ্ট",
            "Fenrir" to "উত্তেজিত, জোরালো",
            "Leda" to "যুবক কণ্ঠ, স্বাভাবিক",
            "Orus" to "শান্ত, স্থির",
            "Aoede" to "মৃদু, গল্প বলার ধরন",
            "Callirrhoe" to "সহজ, কথোপকথনমূলক",
            "Autonoe" to "উজ্জ্বল, তরুণ",
            "Enceladus" to "ধীর, শ্বাসযুক্ত",
            "Iapetus" to "পরিষ্কার, সুরেলা",
            "Umbriel" to "সহজ, নিরীহ",
            "Algieba" to "মসৃণ, গভীর",
            "Despina" to "মসৃণ, নরম",
            "Erinome" to "পরিষ্কার, সংযত",
            "Algenib" to "খসখসে, গভীর",
            "Rasalgethi" to "তথ্যবহুল",
            "Laomedeia" to "প্রাণবন্ত, উষ্ণ",
            "Achernar" to "নরম, মন্থর",
            "Alnilam" to "স্থির, দৃঢ়",
            "Schedar" to "সমান, নিরপেক্ষ",
            "Gacrux" to "পরিণত, সংযত",
            "Pulcherrima" to "প্রাণবন্ত, আবেগী",
            "Achird" to "বন্ধুত্বপূর্ণ",
            "Zubenelgenubi" to "আকস্মিক, ক্যাজুয়াল",
            "Vindemiatrix" to "কোমল, আত্মবিশ্বাসী",
            "Sadachbia" to "লুকানো শক্তি, স্থির",
            "Sadaltager" to "জ্ঞানী, শান্ত",
            "Sulafat" to "উষ্ণ, আন্তরিক"
        )
    }
}
