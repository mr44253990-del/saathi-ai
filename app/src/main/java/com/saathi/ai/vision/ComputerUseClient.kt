package com.saathi.ai.vision

import android.content.Context
import android.util.Base64
import com.saathi.ai.core.ErrorCenter
import com.saathi.ai.data.SecurePrefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client for Gemini's official `computer_use` tool in the **mobile** environment
 * (Phase 3).
 *
 * The contract, exactly as the API documents it:
 *  - send: goal + screenshot + what has already been done;
 *  - receive: one or more function calls — `click`, `long_press`, `type`,
 *    `scroll`, `drag_and_drop`, `press_key`, `wait`, `take_screenshot`,
    `open_app`, `go_back` — each with an `intent` explaining itself;
 *  - coordinates arrive **normalised to a 0–999 grid**; [ActionMapper] scales
 *    them to real pixels, so resolution and density never matter;
 *  - the client executes, screenshots again and the loop repeats.
 *
 * If the key has no access to a computer-use model the client says so plainly
 * and [VisualCore] falls back to the accessibility-tree + grid path that already
 * shipped in v2 — never a silent downgrade.
 */
class ComputerUseClient(private val context: Context) {

    private val prefs = SecurePrefs(context)

    data class Action(val name: String, val args: JSONObject, val intent: String) {
        fun int(key: String, fallback: Int = 0): Int = args.optInt(key, fallback)
        fun str(key: String, fallback: String = ""): String = args.optString(key, fallback)
        fun bool(key: String, fallback: Boolean = false): Boolean = args.optBoolean(key, fallback)
        fun describe(): String = when (name) {
            "click" -> "ট্যাপ (${int("x")},${int("y")})"
            "long_press" -> "লং প্রেস (${int("x")},${int("y")})"
            "type" -> "টাইপ: «${str("text").take(40)}»"
            "scroll" -> "স্ক্রল ${str("direction", "down")}"
            "drag_and_drop" -> "ড্র্যাগ (${int("start_x")},${int("start_y")}) → (${int("end_x")},${int("end_y")})"
            "press_key" -> "কী: ${str("key")}"
            "hotkey" -> "কী সংমিশ্রণ"
            "wait" -> "অপেক্ষা ${int("seconds", 1)} সে."
            "take_screenshot" -> "নতুন স্ক্রিনশট"
            "open_app" -> "অ্যাপ খোলা: ${str("app").ifBlank { str("text") }}"
            "go_back" -> "পেছনে"
            "go_home" -> "হোম"
            else -> name
        }
    }

    data class Reply(
        val actions: List<Action>,
        val text: String,
        val model: String,
        val error: String,
        val done: Boolean
    ) {
        val ok: Boolean get() = error.isBlank()
    }

    private val http by lazy {
        OkHttpClient.Builder()
            .callTimeout(90, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** Configured model first, then the known public names. */
    fun models(): List<String> {
        val custom = prefs.computerUseModel.trim()
        return (if (custom.isNotBlank()) listOf(custom) else emptyList()) + FALLBACKS
    }

    /**
     * One turn of the loop.
     *
     * @param history short Bangla/English lines describing what was already done
     */
    fun ask(
        goal: String,
        jpeg: ByteArray,
        history: List<String>,
        screenW: Int,
        screenH: Int
    ): Reply {
        if (!prefs.isConfigured) {
            return Reply(emptyList(), "", "", "API কী নেই — সেটিংসে কী বসাও", false)
        }
        val instruction = buildString {
            appendLine("লক্ষ্য: $goal")
            appendLine("স্ক্রিনের আসল আকার: ${screenW}x${screenH} পিক্সেল (তোমার দেওয়া 0-999 কোঅর্ডিনেট আমি রূপান্তর করে নেব)।")
            if (history.isNotEmpty()) {
                appendLine("এরপর পর্যন্ত যা করা হয়েছে:")
                history.takeLast(12).forEach { appendLine("- $it") }
            }
            appendLine(
                "নিয়ম: প্রতিবার একটি বা দুটি অ্যাকশন দাও। পাসওয়ার্ড/OTP/পেমেন্ট কনফার্মের অ্যাকশন দিও না — " +
                    "ওসব ব্যবহারকারী নিজে করবে। লক্ষ্য পূরণ হয়ে গেলে কোনো অ্যাকশন না দিয়ে লিখে দাও DONE: <কী হয়েছে>।"
            )
        }
        val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray()
                    .put(JSONObject().put("text", instruction))
                    .put(JSONObject().put("inlineData", JSONObject()
                        .put("mimeType", "image/jpeg")
                        .put("data", b64)))
                )
            }))
            put("tools", JSONArray().put(JSONObject().put(
                "computerUse", JSONObject().put("environment", "ENVIRONMENT_MOBILE")
            )))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("thinkingConfig", JSONObject().put("thinkingLevel", "LOW"))
            })
        }

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
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        lastError = "$model → HTTP ${resp.code}: ${errorOf(text)}"
                        // 404/400 = this key does not have that model; try the next name.
                        if (resp.code == 404 || resp.code == 400) return@use
                        ErrorCenter.log("api", "computer_use কল ব্যর্থ", lastError)
                        return Reply(emptyList(), "", model, lastError, false)
                    }
                    return parse(text, model)
                }
            } catch (t: Throwable) {
                lastError = "$model → ${t.message}"
                ErrorCenter.log("api", "computer_use নেটওয়ার্ক ত্রুটি", lastError)
            }
        }
        return Reply(emptyList(), "", models().firstOrNull() ?: "", lastError.ifBlank { "কোনো মডেল সাড়া দেয়নি" }, false)
    }

    private fun parse(raw: String, model: String): Reply {
        return try {
            val json = JSONObject(raw)
            val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
            val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
            val actions = mutableListOf<Action>()
            val textOut = StringBuilder()
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    val fc = part.optJSONObject("functionCall")
                    if (fc != null) {
                        val name = fc.optString("name")
                        val args = fc.optJSONObject("args") ?: fc.optJSONObject("arguments") ?: JSONObject()
                        actions.add(Action(name, args, args.optString("intent")))
                        continue
                    }
                    val t = part.optString("text")
                    if (t.isNotBlank()) textOut.appendLine(t)
                }
            }
            val done = textOut.contains("DONE:", true) ||
                candidate?.optString("finishReason") == "STOP" && actions.isEmpty()
            Reply(actions, textOut.toString().trim(), model, "", done)
        } catch (t: Throwable) {
            ErrorCenter.log("api", "computer_use রেসপন্স পড়া যায়নি", t.message)
            Reply(emptyList(), "", model, "রেসপন্স পড়া যায়নি (${t.message})", false)
        }
    }

    private fun errorOf(body: String): String = try {
        val o = JSONObject(body)
        o.optJSONObject("error")?.optString("message")?.take(220) ?: body.take(220)
    } catch (t: Throwable) {
        body.take(220)
    }

    /** Human/model readable capability line. */
    fun status(): String = buildString {
        appendLine("computer_use ক্লায়েন্ট: ${if (prefs.isConfigured) "API কী আছে" else "API কী নেই"}")
        appendLine("মডেল তালিকা: ${models().joinToString(", ")}")
        append("ব্যাকআপ পথ: অ্যাক্সেসিবিলিটি ডাম্প + গ্রিড ট্যাপ (v2 থেকেই আছে)")
    }

    companion object {
        val FALLBACKS = listOf(
            "gemini-2.5-computer-use",
            "gemini-3-pro-computer-use",
            "gemini-2.5-pro"
        )
    }
}
