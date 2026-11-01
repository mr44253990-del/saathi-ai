package com.saathi.ai.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralised error monitoring (Phase 15).
 *
 * Every module reports here instead of swallowing failures. Each entry carries a
 * *guessed* root cause and a concrete next step, so the UI, the voice reply and
 * the model all speak about the same problem in the same words.
 *
 * Two hard rules:
 *  - **No infinite retries.** [retryBudget] hands out a bounded number of
 *    attempts per key with exponential backoff; when the budget is gone the
 *    caller must report failure, not loop.
 *  - **Nothing is invented.** A cause is only stated when a rule matched;
 *    otherwise the entry says "কারণ নির্ণয় করা যায়নি" and shows the raw detail.
 */
object ErrorCenter {

    /** Known subsystems — the dashboard groups by these. */
    val MODULES = listOf(
        "browser", "api", "permission", "file", "model", "voice", "service",
        "shizuku", "terminal", "vision", "task", "media", "audio", "system", "agent", "other"
    )

    enum class Severity { INFO, WARNING, ERROR, FATAL }

    data class Entry(
        val at: Long,
        val module: String,
        val severity: Severity,
        val message: String,
        val detail: String,
        val cause: String,
        val solution: String,
        val resolved: Boolean = false
    ) {
        fun time(): String = fmt.format(Date(at))
        fun line(): String = "[${time()}] ${module.uppercase()} ${sev()}: $message" +
            if (detail.isNotBlank()) " — ${detail.take(180)}" else ""
        fun sev(): String = when (severity) {
            Severity.INFO -> "তথ্য"
            Severity.WARNING -> "সতর্কতা"
            Severity.ERROR -> "ত্রুটি"
            Severity.FATAL -> "মারাত্মক"
        }
    }

    private const val MAX_ENTRIES = 500
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val entries = ArrayDeque<Entry>()
    private val _flow = MutableStateFlow<List<Entry>>(emptyList())
    val flow: StateFlow<List<Entry>> = _flow.asStateFlow()

    private val retryMap = HashMap<String, RetryState>()
    private data class RetryState(var attempts: Int = 0, var lastAt: Long = 0L)

    /** Total errors seen since process start (for the "something is wrong" chip). */
    @Volatile var totalErrors: Int = 0
        private set

    @Volatile var lastErrorAt: Long = 0L
        private set

    // ------------------------------------------------------------------ logging

    fun log(module: String, message: String, detail: String? = null, severity: Severity = Severity.ERROR) {
        val mod = MODULES.firstOrNull { module.equals(it, true) } ?: "other"
        val d = detail?.take(600) ?: ""
        val (cause, solution) = analyse(mod, message, d)
        val e = Entry(System.currentTimeMillis(), mod, severity, message.take(400), d, cause, solution)
        synchronized(entries) {
            entries.addLast(e)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
            _flow.value = entries.toList().reversed()
        }
        if (severity == Severity.ERROR || severity == Severity.FATAL) {
            totalErrors++
            lastErrorAt = e.at
        }
    }

    fun info(module: String, message: String) = log(module, message, null, Severity.INFO)
    fun warn(module: String, message: String, detail: String? = null) =
        log(module, message, detail, Severity.WARNING)
    fun fatal(module: String, message: String, detail: String? = null) =
        log(module, message, detail, Severity.FATAL)

    /** Logs a throwable with its stack head, so crashes leave a trace. */
    fun logThrow(module: String, what: String, t: Throwable) {
        log(module, what, "${t.javaClass.simpleName}: ${t.message} | ${t.stackTrace.getOrNull(0)}",
            Severity.ERROR)
    }

    // ------------------------------------------------------- root cause analysis

    /**
     * Rule-based cause/solution lookup. Rules are ordered most specific first;
     * the first match wins. Every rule pairs a cause with something the user or
     * Jimi can actually do next.
     */
    private fun analyse(module: String, message: String, detail: String): Pair<String, String> {
        val hay = "$message $detail".lowercase()
        fun has(vararg w: String) = w.any { hay.contains(it) }

        return when {
            has("429", "rate limit", "resource_exhausted") ->
                "API রেট লিমিট" to "কিছুক্ষণ অপেক্ষা করে আবার চেষ্টা করো; কাজ ছোট ভাগে ভাগো"
            has("401", "403", "api key not valid", "permission denied (apikey)", "invalid api key") ->
                "API কী ভুল বা মেয়াদ শেষ" to "সেটিংসে গিয়ে Gemini API কী আবার বসাও"
            has("404", "models/", "not found") && module == "api" ->
                "এই মডেল নামটি এই কী-তে নেই" to "সেটিংসে অন্য মডেল বেছে নাও (Model Manager)"
            has("400", "invalid_argument") ->
                "রিকোয়েস্টের গঠন মডেল মানেনি" to "প্রম্পট/প্যারামিটার ছোট করে আবার চেষ্টা করো"
            has("timeout", "timed out", "সময় শেষ") ->
                "সময়সীমা শেষ" to "নেটওয়ার্ক পরীক্ষা করো; টাইমআউট বাড়ানো বা কাজ ভাগ করা যেতে পারে"
            has("unknownhost", "no address associated", "unreachable", "failed to connect", "network") ->
                "ইন্টারনেট সংযোগ নেই" to "ওয়াইফাই/ডেটা চালু আছে কি না দেখো; অফলাইন মোডে সীমিত কাজ চলবে"
            has("websocket", "closed", "goaway", "resumption") ->
                "লাইভ সেশন বন্ধ হয়ে গেছে" to "সেশন আবার শুরু করো; অটো-রিকানেক্ট চালু থাকলে নিজেই যুক্ত হবে"
            has("securityexception", "permission", "অনুমতি") ->
                "অনুমতি নেই" to "PermissionCenter-এর নির্দেশনা অনুযায়ী অনুমতি দাও; Shizuku/রুট থাকলে গভীর পথ খোলে"
            has("shizuku", "pingbinder", "binder") ->
                "Shizuku সংযুক্ত নয়" to "Shizuku অ্যাপ খুলে Start চাপো, তারপর Jimi-কে আবার অনুমতি দাও"
            has("su: not found", "no root", "রুট নেই") ->
                "রুট অ্যাক্সেস নেই" to "রুট ছাড়া Shizuku বা অ্যাক্সেসিবিলিটি পথ ব্যবহার করো"
            has("flag_secure", "secure window", "স্ক্রিনশট নেওয়া যায়নি") ->
                "স্ক্রিনটি FLAG_SECURE দিয়ে সুরক্ষিত" to "এই অ্যাপের স্ক্রিনশট তোলা নিরাপত্তার কারণে অসম্ভব; অ্যাক্সেসিবিলিটি ট্রি পড়ে এগোও"
            has("enoent", "no such file", "ফাইল নেই") ->
                "ফাইল পাওয়া যায়নি" to "পাথ যাচাই করো; স্টোরেজ অনুমতি আছে কি না দেখো"
            has("eacces", "read-only", "permission denied") && module == "file" ->
                "ফাইলে লেখার অনুমতি নেই" to "অ্যাপের নিজের ফোল্ডারে লেখো, বা SAF/রুট পথ ব্যবহার করো"
            has("enospc", "space", "স্টোরেজ কম") ->
                "স্টোরেজ শেষ" to "HealthInsights-এর সেলফ-ফিক্স চালাও; বড় ফাইল সরিয়ে ফেলো"
            has("battery", "ডুজেড", "doze", "optimization") ->
                "ব্যাটারি অপটিমাইজেশন কাজ থামিয়ে দিয়েছে" to "সেটিংস থেকে Jimi-কে অপটিমাইজেশনের বাইরে রাখো"
            has("foreground service", "fgs", "startforeground") ->
                "ফোরগ্রাউন্ড সার্ভিস শুরু করা যায়নি" to "নোটিফিকেশন পারমিশন ও FGS টাইপ ঠিক আছে কি না দেখো"
            has("accessibility", "instance == null", "অ্যাক্সেসিবিলিটি বন্ধ") ->
                "অ্যাক্সেসিবিলিটি সার্ভিস বন্ধ" to "সেটিংস → Accessibility → Jimi চালু করো (রুট/Shizuku থাকলে নিজেই চালু করতে পারে)"
            has("webview", "renderer", "chromium") ->
                "WebView রেন্ডারার সমস্যা" to "Android System WebView আপডেট করো; ট্যাবটি আবার লোড দাও"
            has("javascript", "evaluate") ->
                "পেজে JavaScript চালানো যায়নি" to "পেজটি JS বন্ধ করে দিতে পারে; স্ক্রিনশট ভিশন পথে যাও"
            has("ocr", "text recognition") ->
                "লেখা শনাক্ত করা যায়নি" to "ছবি আরও পরিষ্কার/উজ্জ্বল করে আবার চেষ্টা করো"
            has("tts", "speech", "audio/pcm") ->
                "অডিও তৈরি করা যায়নি" to "API কী ও মডেল নাম পরীক্ষা করো; বিকল্প হিসেবে লোকাল TextToSpeech আছে"
            has("cancelled", "বাতিল") ->
                "কাজটি বাতিল করা হয়েছে" to "এটা ত্রুটি নয় — ব্যবহারকারী বা বাজেট থামিয়েছে"
            has("budget", "max attempts", "retry limit") ->
                "রিট্রাই বাজেট শেষ" to "আর স্বয়ংক্রিয় চেষ্টা হবে না; ব্যবহারকারীকে বিকল্প পথ বা অনুমতি চাও"
            else -> "কারণ নির্ণয় করা যায়নি" to "ডিটেইল পড়ে দেখো; প্রয়োজনে ডায়াগনস্টিকস চালাও"
        }
    }

    // ---------------------------------------------------------- retry management

    /**
     * Hands out a retry token. Returns false when the budget is exhausted, so a
     * caller can never loop forever. Backoff: 0.4s, 1.2s, 3.6s (then no more).
     *
     * @param key stable id for the operation, e.g. "vision.screenshot"
     * @param max attempts allowed inside the window (default 3)
     */
    fun retryBudget(key: String, max: Int = 3): Boolean {
        synchronized(retryMap) {
            val st = retryMap.getOrPut(key) { RetryState() }
            val now = System.currentTimeMillis()
            // A key untouched for 2 minutes gets a fresh budget.
            if (now - st.lastAt > 120_000) {
                st.attempts = 0
            }
            if (st.attempts >= max) {
                log("system", "রিট্রাই বাজেট শেষ: $key", "চেষ্টা ${st.attempts} বার", Severity.WARNING)
                return false
            }
            val backoffMs = when (st.attempts) {
                0 -> 0L
                1 -> 400L
                else -> 1_200L * (st.attempts - 1)
            }
            if (backoffMs > 0 && now - st.lastAt < backoffMs) {
                // Too soon: caller should wait rather than hammer the resource.
                return false
            }
            st.attempts++
            st.lastAt = now
            return true
        }
    }

    fun retryState(key: String): Int = synchronized(retryMap) { retryMap[key]?.attempts ?: 0 }
    fun resetRetry(key: String) = synchronized(retryMap) { retryMap.remove(key); Unit }
    fun resetAllRetries() = synchronized(retryMap) { retryMap.clear() }

    // -------------------------------------------------------------------- output

    fun snapshot(limit: Int = 25): List<Entry> = synchronized(entries) { entries.toList().reversed().take(limit) }

    fun forModule(module: String, limit: Int = 20): List<Entry> =
        snapshot(200).filter { it.module == module }.take(limit)

    fun counts(): Map<String, Int> = synchronized(entries) {
        entries.filter { it.severity == Severity.ERROR || it.severity == Severity.FATAL }
            .groupBy { it.module }.mapValues { it.value.size }
    }

    fun unresolved(): List<Entry> = snapshot(200).filter { !it.resolved }

    /** Bangla report used by the model, the dashboard and the voice reply. */
    fun report(limit: Int = 18): String {
        val list = snapshot(limit)
        if (list.isEmpty()) return "কোনো ত্রুটি লগ নেই — সব মডিউল ঠিক আছে"
        val c = counts()
        return buildString {
            appendLine("মোট ত্রুটি লগ: $totalErrors (এই সেশনে), সংরক্ষিত ${entries.size}টি")
            if (c.isNotEmpty()) {
                append("মডিউল অনুযায়ী: ")
                appendLine(c.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key}=${it.value}" })
            }
            appendLine("সাম্প্রতিক:")
            list.forEach { e ->
                appendLine("• ${e.line()}")
                if (e.cause != "কারণ নির্ণয় করা যায়নি") appendLine("   কারণ: ${e.cause}")
                appendLine("   করণীয়: ${e.solution}")
            }
        }
    }

    /** One-line health summary for chips and the bubble. */
    fun headline(): String {
        val recent = snapshot(50).filter {
            it.severity != Severity.INFO && System.currentTimeMillis() - it.at < 10 * 60_000
        }
        return when {
            recent.isEmpty() -> "সব ঠিক আছে — গত ১০ মিনিটে কোনো ত্রুটি নেই"
            else -> "গত ১০ মিনিটে ${recent.size}টি সমস্যা: সর্বশেষ ${recent.first().message}"
        }
    }

    fun clear() {
        synchronized(entries) {
            entries.clear()
            _flow.value = emptyList()
        }
        resetAllRetries()
        totalErrors = 0
    }

    // ------------------------------------------------------------- persistence

    /** Saves the ring buffer so a crash or reboot does not erase the evidence. */
    fun persist(context: Context) {
        try {
            val arr = JSONArray()
            snapshot(MAX_ENTRIES).forEach { e ->
                arr.put(
                    JSONObject()
                        .put("at", e.at).put("module", e.module).put("sev", e.severity.name)
                        .put("msg", e.message).put("detail", e.detail)
                        .put("cause", e.cause).put("solution", e.solution)
                )
            }
            File(context.filesDir, "jimi-errors.json").writeText(arr.toString())
        } catch (_: Throwable) {
        }
    }

    fun load(context: Context) {
        try {
            val f = File(context.filesDir, "jimi-errors.json")
            if (!f.exists()) return
            val arr = JSONArray(f.readText())
            synchronized(entries) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    entries.addFirst(
                        Entry(
                            o.optLong("at"), o.optString("module", "other"),
                            runCatching { Severity.valueOf(o.optString("sev", "ERROR")) }
                                .getOrDefault(Severity.ERROR),
                            o.optString("msg"), o.optString("detail"),
                            o.optString("cause"), o.optString("solution")
                        )
                    )
                }
                while (entries.size > MAX_ENTRIES) entries.removeLast()
                _flow.value = entries.toList().reversed()
            }
        } catch (_: Throwable) {
        }
    }
}
