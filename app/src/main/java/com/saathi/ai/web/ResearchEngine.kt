package com.saathi.ai.web

import android.content.Context
import com.saathi.ai.core.ErrorCenter
import com.saathi.ai.data.SecurePrefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Research engine (Phase 7): several sources, cross-checked, with the links kept.
 *
 * Two paths, tried in order:
 *  1. **Grounded search** — one Gemini call with the `google_search` tool. The
 *     response carries the answer *and* the source chunks (title + uri), which is
 *     the only way to keep citations honest instead of invented.
 *  2. **Browser sweep** — DuckDuckGo HTML → top N result links → open each in the
 *     built-in WebView → extract text → one summarising call over the collected
 *     texts, listing which source said what.
 *
 * Then a second pass explicitly asks the model to compare sources and mark
 * disagreements and stale claims, because a summary that hides a contradiction is
 * worse than no summary. Results are cached for 24 h so repeats are instant.
 */
class ResearchEngine(private val context: Context) {

    private val prefs = SecurePrefs(context)

    data class Source(
        val title: String,
        val url: String,
        val snippet: String,
        val fetchedAt: Long
    ) {
        fun line(): String = "• ${title.ifBlank { url }.take(70)}\n   $url" +
            if (snippet.isNotBlank()) "\n   ${snippet.take(200)}" else ""
    }

    private val http by lazy {
        OkHttpClient.Builder()
            .callTimeout(80, TimeUnit.SECONDS)
            .readTimeout(80, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val cacheFile: File get() = File(context.filesDir, "jimi-research-cache.json")
    private val stamp = SimpleDateFormat("dd MMM yyyy", Locale.US)

    // ------------------------------------------------------------------ public

    /**
     * Full research run. The report always starts with a verdict token and always
     * ends with the source list — no claim without a link.
     */
    fun research(
        query: String,
        sources: Int = prefs.researchSources,
        seconds: Int = 90,
        onStep: (String) -> Unit = {}
    ): String {
        if (query.isBlank()) return "ব্যর্থ: কী নিয়ে রিসার্চ করব সেটা বলো"
        if (!prefs.isConfigured) {
            return "আটকে: API কী নেই — রিসার্চের জন্য সেটিংসে Gemini কী বসাও। " +
                "(কী ছাড়া আমি শুধু ব্রাউজারে পেজ খুলে দিতে পারি, তথ্য যাচাই করতে পারব না)"
        }
        val deadline = System.currentTimeMillis() + seconds * 1000L
        val key = normalize(query)

        cache(key)?.let { hit ->
            return "সফল: (ক্যাশ থেকে, ${hit.first}) রিসার্চ রিপোর্ট\n\n${hit.second}"
        }

        onStep("রিসার্চ: প্রথমে গ্রাউন্ডেড সার্চ চেষ্টা করছি")
        val grounded = groundedSearch(query, sources)
        val (answer, list) = if (grounded != null && grounded.second.isNotEmpty()) grounded
        else {
            onStep("গ্রাউন্ডিং পাওয়া যায়নি — ব্রাউজার দিয়ে ${sources}টি সোর্স পড়ছি")
            browserSweep(query, sources, deadline, onStep)
        }

        if (list.isEmpty() && answer.isBlank()) {
            ErrorCenter.log("browser", "রিসার্চে কোনো সোর্স পাওয়া যায়নি", query)
            return "ব্যর্থ: কোনো নির্ভরযোগ্য সোর্স পাওয়া যায়নি — নেটওয়ার্ক আছে কি না দেখো, " +
                "অথবা প্রশ্নটা আরেকটু স্পষ্ট করে বলো"
        }

        onStep("সোর্সগুলো মিলিয়ে দেখছি (বিরোধ ও পুরনো তথ্য খুঁজছি)")
        val cross = if (list.size >= 2 && System.currentTimeMillis() < deadline) {
            crossCheck(query, answer, list)
        } else "একটিই সোর্স পাওয়া গেছে — ক্রস-চেক করা সম্ভব হয়নি।"

        val report = buildString {
            appendLine("প্রশ্ন: $query")
            appendLine("তারিখ: ${stamp.format(Date())} · সোর্স: ${list.size}টি")
            appendLine()
            appendLine("── মূল উত্তর ──")
            appendLine(answer.ifBlank { "সোর্স থেকে উত্তর বের করা যায়নি" })
            appendLine()
            appendLine("── ক্রস-চেক ──")
            appendLine(cross)
            appendLine()
            appendLine("── সোর্স ──")
            list.take(sources).forEach { appendLine(it.line()) }
            appendLine()
            append("সীমাবদ্ধা: এটি ওয়েব থেকে নেওয়া তথ্যের সারসংক্ষেপ — গুরুত্বপূর্ণ সিদ্ধান্তের আগে মূল লিংকগুলো নিজে দেখে নাও।")
        }
        put(key, report)
        return "সফল: রিসার্চ সম্পন্ন — ${list.size}টি সোর্স যাচাই করা হয়েছে\n\n$report"
    }

    /** One summarising call over arbitrary text (used by other agents too). */
    fun summarize(text: String, ask: String = "মূল পয়েন্টগুলো বাংলায় সংক্ষেপে লেখো"): String {
        if (text.isBlank()) return "ব্যর্থ: সারসংক্ষেপ করার মতো লেখা নেই"
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put(
                "parts", JSONArray().put(JSONObject().put(
                    "text", "$ask\n\n---\n${text.take(16000)}"
                ))
            )))
            put("generationConfig", JSONObject().put("temperature", 0.3))
        }
        val r = call(body, withSearch = false)
        return r.first.ifBlank { "ব্যর্থ: সারসংক্ষেপ করা যায়নি (${r.second})" }
    }

    fun cacheStats(): String {
        val map = readCache()
        if (map.isEmpty()) return "রিসার্চ ক্যাশ খালি"
        val fresh = map.count { System.currentTimeMillis() - it.value.optLong("at") < DAY }
        return "ক্যাশে ${map.size}টি রিসার্চ আছে, তার মধ্যে $fresh টি ২৪ ঘণ্টার ভিতরের (তাৎক্ষণিক ফিরবে)"
    }

    fun clearCache(): String {
        return try {
            if (cacheFile.delete()) "রিসার্চ ক্যাশ মুছে ফেলা হয়েছে" else "ক্যাশ ফাইল ছিল না"
        } catch (t: Throwable) {
            "ক্যাশ মোছা যায়নি (${t.message})"
        }
    }

    // ------------------------------------------------------------------- paths

    /** Gemini generateContent with the google_search grounding tool. */
    private fun groundedSearch(query: String, sources: Int): Pair<String, List<Source>>? {
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text",
                    "বাংলায় উত্তর দাও এবং প্রতিটি গুরুত্বপূর্ণ তথ্যের পাশে সোর্স উল্লেখ করো। প্রশ্ন: $query")))
            }))
            put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            put("generationConfig", JSONObject().put("temperature", 0.3))
        }
        val (text, err, raw) = callFull(body, withSearch = true)
        if (text.isBlank() && raw == null) {
            ErrorCenter.warn("api", "গ্রাউন্ডেড সার্চ ব্যর্থ", err)
            return null
        }
        val list = mutableListOf<Source>()
        try {
            val candidate = raw?.optJSONArray("candidates")?.optJSONObject(0)
            val gm = candidate?.optJSONObject("groundingMetadata")
            val chunks = gm?.optJSONArray("groundingChunks")
            if (chunks != null) {
                for (i in 0 until chunks.length()) {
                    val web = chunks.optJSONObject(i)?.optJSONObject("web") ?: continue
                    val uri = web.optString("uri")
                    if (uri.isBlank()) continue
                    list.add(
                        Source(
                            web.optString("title"),
                            uri,
                            "",
                            System.currentTimeMillis()
                        )
                    )
                }
            }
            gm?.optJSONArray("webSearchQueries")?.let { q ->
                for (i in 0 until q.length()) ErrorCenter.info("api", "সার্চ কোয়েরি: ${q.optString(i)}")
            }
        } catch (t: Throwable) {
            ErrorCenter.log("api", "গ্রাউন্ডিং মেটাডেটা পড়া যায়নি", t.message)
        }
        if (text.isBlank()) return null
        return text to list.take(sources)
    }

    /** DuckDuckGo → open top N → extract text → summarise with citations. */
    private fun browserSweep(
        query: String,
        sources: Int,
        deadline: Long,
        onStep: (String) -> Unit
    ): Pair<String, List<Source>> {
        WebBrowser.registerKillSwitch()
        val found = mutableListOf<Source>()
        val texts = StringBuilder()

        val searchReport = WebBrowser.search(context, query)
        if (searchReport.contains("ব্যর্থ")) {
            ErrorCenter.log("browser", "রিসার্চ সার্চ পেজ লোড হয়নি", searchReport)
            return "" to emptyList()
        }
        val pairs = parseResultLinks(WebBrowser.text(context, 14000), WebBrowser.links(context, 30))
        onStep("${pairs.size}টি ফলাফল পাওয়া গেছে, সেরা $sources টি পড়ছি")

        pairs.take(sources).forEachIndexed { i, (title, url) ->
            if (System.currentTimeMillis() > deadline) {
                onStep("সময় শেষ — ${found.size}টি সোর্স নিয়েই রিপোর্ট বানাচ্ছি")
                return@forEachIndexed
            }
            onStep("সোর্স ${i + 1}: ${title.take(48)}")
            val opened = WebBrowser.open(context, url)
            if (opened.startsWith("ব্যর্থ")) {
                ErrorCenter.warn("browser", "সোর্স খোলা যায়নি", url)
                return@forEachIndexed
            }
            val body = WebBrowser.text(context, 9000)
            if (body.isBlank()) return@forEachIndexed
            found.add(Source(title, url, body.take(260), System.currentTimeMillis()))
            texts.appendLine("### সোর্স ${found.size}: $title ($url)")
            texts.appendLine(body.take(4000))
            texts.appendLine()
        }
        WebBrowser.clearShots(context)

        if (found.isEmpty()) return "" to emptyList()
        val prompt = buildString {
            appendLine("নিচের সোর্সগুলো থেকে «$query» প্রশ্নের উত্তর বাংলায় লেখো।")
            appendLine("প্রতিটি তথ্যের শেষে [সোর্স N] লিখে দাও কোন সোর্স থেকে এসেছে।")
            appendLine("সোর্সে যা নেই তা নিজে থেকে বলো না; তথ্য কম থাকলে স্পষ্ট করে বলো।")
            appendLine()
            append(texts.toString().take(24000))
        }
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put(
                "parts", JSONArray().put(JSONObject().put("text", prompt))
            )))
            put("generationConfig", JSONObject().put("temperature", 0.2))
        }
        val (answer, err) = call(body, withSearch = false)
        if (answer.isBlank()) {
            ErrorCenter.log("api", "রিসার্চ সামারি ব্যর্থ", err)
            return "সোর্স পড়া হয়েছে কিন্তু সারসংক্ষেপ করা যায়নি ($err)। সোর্সগুলো নিচে দেওয়া আছে।" to found
        }
        return answer to found
    }

    /** Second pass: find disagreements and stale information. */
    private fun crossCheck(query: String, answer: String, sources: List<Source>): String {
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                .put(JSONObject().put("text", buildString {
                    appendLine("প্রশ্ন: $query")
                    appendLine("উত্তর: ${answer.take(3000)}")
                    appendLine("সোর্স তালিকা:")
                    sources.forEachIndexed { i, s -> appendLine("${i + 1}. ${s.title} — ${s.url} — ${s.snippet.take(180)}") }
                    appendLine()
                    appendLine("কাজ: ১) কোন তথ্য একাধিক সোর্স মিলে যাচ্ছে, ২) কোন জায়গায় সোর্সগুলো পরস্পরবিরোধী, " +
                        "৩) কোন দাবি পুরনো তথ্যের উপর দাঁড়িয়ে আছে বা যাচাই করা যায়নি। " +
                        "সংক্ষিপ্ত বাংলায় তিনটি ভাগে লেখো। অমিল না থাকলে স্পষ্ট করে লেখো «কোনো বিরোধ পাওয়া যায়নি»।")
                })))))
            put("generationConfig", JSONObject().put("temperature", 0.1))
        }
        val (out, err) = call(body, withSearch = false)
        return out.ifBlank { "ক্রস-চেক করা যায়নি ($err)" }
    }

    /** Pulls (title, url) pairs out of a DuckDuckGo HTML results page. */
    private fun parseResultLinks(pageText: String, links: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        links.lines().forEach { line ->
            val parts = line.split(" :: ")
            if (parts.size != 2) return@forEach
            val title = parts[0].trim()
            val url = parts[1].trim()
            if (url.startsWith("http") && !url.contains("duckduckgo.com/y.js") &&
                !url.contains("duckduckgo.com/l/?")
            ) {
                out.add(title to url)
            }
        }
        if (out.isEmpty() && pageText.isNotBlank()) {
            // No anchors readable (rare, heavily scripted page) — report honestly.
            ErrorCenter.warn("browser", "ফলাফলের লিংক পড়া যায়নি", pageText.take(200))
        }
        return out.distinctBy { it.second }
    }

    // --------------------------------------------------------------------- api

    private fun call(body: JSONObject, withSearch: Boolean): Pair<String, String> {
        val r = callFull(body, withSearch)
        return r.first to r.second
    }

    /** Returns (text, error, rawJson) trying each model name until one accepts. */
    private fun callFull(
        body: JSONObject,
        withSearch: Boolean
    ): Triple<String, String, JSONObject?> {
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
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        lastError = "$model → HTTP ${resp.code}: ${errorOf(text)}"
                        if (resp.code == 404 || resp.code == 400) return@use
                        ErrorCenter.log("api", "রিসার্চ কল ব্যর্থ", lastError)
                        return Triple("", lastError, null)
                    }
                    val json = JSONObject(text)
                    val parts = json.optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts")
                    val sb = StringBuilder()
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            parts.optJSONObject(i)?.optString("text")?.let { if (it.isNotBlank()) sb.appendLine(it) }
                        }
                    }
                    return Triple(sb.toString().trim(), "", json)
                }
            } catch (t: Throwable) {
                lastError = "$model → ${t.message}"
                ErrorCenter.log("api", "রিসার্চ নেটওয়ার্ক ত্রুটি", lastError)
            }
        }
        return Triple("", lastError.ifBlank { "কোনো মডেল সাড়া দেয়নি" }, null)
    }

    private fun errorOf(body: String): String = try {
        JSONObject(body).optJSONObject("error")?.optString("message")?.take(200) ?: body.take(200)
    } catch (t: Throwable) {
        body.take(200)
    }

    // ------------------------------------------------------------------- cache

    private fun normalize(q: String) = q.trim().lowercase().replace(Regex("\\s+"), " ").take(160)

    private fun readCache(): Map<String, JSONObject> {
        return try {
            if (!cacheFile.exists()) emptyMap()
            else {
                val o = JSONObject(cacheFile.readText())
                o.keys().asSequence().map { it to o.optJSONObject(it)!! }.toMap()
            }
        } catch (t: Throwable) {
            emptyMap()
        }
    }

    private fun cache(key: String): Pair<String, String>? {
        val entry = readCache()[key] ?: return null
        val at = entry.optLong("at")
        if (System.currentTimeMillis() - at > DAY) return null
        return stamp.format(Date(at)) to entry.optString("report")
    }

    private fun put(key: String, report: String) {
        try {
            val all = readCache().toMutableMap()
            all[key] = JSONObject().put("at", System.currentTimeMillis()).put("report", report.take(12000))
            // Keep the cache small: newest 25 only.
            val trimmed = all.entries.sortedByDescending { it.value.optLong("at") }.take(25)
            val out = JSONObject()
            trimmed.forEach { (k, v) -> out.put(k, v) }
            cacheFile.writeText(out.toString())
        } catch (t: Throwable) {
            ErrorCenter.log("browser", "রিসার্চ ক্যাশ লেখা যায়নি", t.message)
        }
    }

    companion object {
        private const val DAY = 24 * 60 * 60_000L

        /** Text models that accept the google_search tool. Tried in order. */
        val MODELS = listOf(
            "gemini-2.5-flash",
            "gemini-3-pro",
            "gemini-2.5-pro"
        )
    }
}
