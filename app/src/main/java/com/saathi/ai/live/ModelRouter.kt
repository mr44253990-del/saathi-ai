package com.saathi.ai.live

import android.content.Context
import com.saathi.ai.core.ErrorCenter
import com.saathi.ai.data.SecurePrefs
import java.util.concurrent.ConcurrentHashMap

/**
 * Adaptive model router (Phase 14 and 18).
 *
 * Picks one model per kind of work instead of sending everything to the biggest
 * model: a quick lookup stays on flash, reasoning and code go to pro, computer-use and
 * TTS use their dedicated endpoints. Recent failures recorded through [recordFailure]
 * temporarily demote a model, so a bad key or an overloaded endpoint degrades to the
 * next candidate rather than failing the task.
 *
 * It also keeps a record of latency per model, which is what makes the Gemini hub feel
 * faster: the UI can show real numbers instead of guessing.
 */
object ModelRouter {

    enum class Kind { LIVE, CHAT, FAST, REASON, CODE, VISION, COMPUTER, TTS, RESEARCH, IMAGE, SUMMARY }

    private val failures = ConcurrentHashMap<String, Int>()
    private val lastFailAt = ConcurrentHashMap<String, Long>()
    private val latency = ConcurrentHashMap<String, MutableList<Long>>()

    private const val DEMOTE_WINDOW_MS = 10 * 60_000L
    private const val MAX_SAMPLES = 24

    fun kindOf(name: String): Kind = when (name.trim().lowercase()) {
        "live", "voice", "কথা" -> Kind.LIVE
        "fast", "quick", "ছোট" -> Kind.FAST
        "reason", "think", "বিশ্লেষণ" -> Kind.REASON
        "code", "কোড" -> Kind.CODE
        "vision", "image-read", "ছবি-পড়া" -> Kind.VISION
        "computer", "screen", "স্ক্রিন" -> Kind.COMPUTER
        "tts", "speech", "কণ্ঠ" -> Kind.TTS
        "research", "খোঁজ" -> Kind.RESEARCH
        "image", "ছবি" -> Kind.IMAGE
        "summary", "সারসংক্ষেপ" -> Kind.SUMMARY
        else -> Kind.CHAT
    }

    /** The model to use for this kind of work, honouring user prefs and failures. */
    fun pick(kind: Kind, context: Context): String {
        val prefs = SecurePrefs(context)
        val candidates = candidatesFor(kind, prefs)
        return candidates.firstOrNull { !demoted(it) } ?: candidates.firstOrNull() ?: prefs.model
    }

    fun pick(kindName: String, context: Context): String = pick(kindOf(kindName), context)

    private fun candidatesFor(kind: Kind, prefs: SecurePrefs): List<String> = when (kind) {
        Kind.LIVE -> listOf(prefs.model, "gemini-2.5-flash-live", "gemini-2.0-flash-live")
        Kind.CHAT -> listOf(prefs.model, "gemini-2.5-flash", "gemini-2.5-pro")
        Kind.FAST -> listOf("gemini-2.5-flash", "gemini-2.0-flash", prefs.model)
        Kind.REASON -> listOf("gemini-3-pro", "gemini-2.5-pro", "gemini-2.5-flash")
        Kind.CODE -> listOf("gemini-3-pro", "gemini-2.5-pro", "gemini-2.5-flash")
        Kind.VISION -> listOf("gemini-2.5-flash", "gemini-3-pro", "gemini-2.5-pro")
        Kind.COMPUTER -> listOf(prefs.computerUseModel, "gemini-2.5-computer-use", "gemini-3-pro-computer-use")
        Kind.TTS -> listOf(prefs.ttsModel, "gemini-2.5-flash-preview-tts", "gemini-2.5-pro-preview-tts")
        Kind.RESEARCH -> listOf("gemini-2.5-flash", "gemini-3-pro", "gemini-2.5-pro")
        Kind.IMAGE -> listOf("gemini-2.5-flash-image", "gemini-2.0-flash-preview-image-generation")
        Kind.SUMMARY -> listOf("gemini-2.5-flash", prefs.model)
    }

    /** All candidates for a kind, in priority order (used by callers that retry). */
    fun candidates(kind: Kind, context: Context): List<String> = candidatesFor(kind, SecurePrefs(context))

    /**
     * Chooses a kind from the shape of the request, so a single entry point can serve
     * both voice and text without the caller guessing.
     */
    fun route(goal: String, detail: String = ""): Kind {
        val g = (goal + " " + detail).lowercase()
        return when {
            g.contains("computer use") || g.contains("স্ক্রিন দেখে") || g.contains("বাটনে চাপ") -> Kind.COMPUTER
            g.contains("কোড") || g.contains("python") || g.contains("script") || g.contains("bug") -> Kind.CODE
            g.contains("রিপোর্ট") || g.contains("গবেষণা") || g.contains("research") || g.contains("তুলনা") -> Kind.RESEARCH
            g.contains("সারসংক্ষেপ") || g.contains("summary") || g.contains("সংক্ষেপে") -> Kind.SUMMARY
            g.contains("ছবি") || g.contains("image") || g.contains("ocr") || g.contains("লেখা বের") -> Kind.VISION
            g.contains("কণ্ঠ") || g.contains("tts") || g.contains("শোনাবে") || g.contains("বলে দাও") -> Kind.TTS
            g.length < 40 && !g.contains("কেন") && !g.contains("বিশ্লেষণ") -> Kind.FAST
            else -> Kind.CHAT
        }
    }

    /** Cheap vs deep: how many steps to budget, based on the routed kind. */
    fun budgetFor(kind: Kind): Int = when (kind) {
        Kind.FAST, Kind.SUMMARY -> 6
        Kind.CHAT, Kind.TTS -> 10
        Kind.VISION, Kind.CODE, Kind.RESEARCH -> 14
        Kind.COMPUTER, Kind.LIVE, Kind.IMAGE, Kind.REASON -> 18
    }

    fun recordSuccess(model: String, ms: Long) {
        failures.remove(model)
        lastFailAt.remove(model)
        val list = latency.getOrPut(model) { mutableListOf() }
        synchronized(list) {
            list.add(ms)
            while (list.size > MAX_SAMPLES) list.removeAt(0)
        }
    }

    fun recordFailure(model: String, why: String = "") {
        failures[model] = (failures[model] ?: 0) + 1
        lastFailAt[model] = System.currentTimeMillis()
        ErrorCenter.info("model", "মডেল ডিমোট: $model", why.take(120))
    }

    /** A model stays demoted for [DEMOTE_WINDOW_MS] after its third failure. */
    fun demoted(model: String): Boolean {
        val at = lastFailAt[model] ?: return false
        if (System.currentTimeMillis() - at > DEMOTE_WINDOW_MS) {
            failures.remove(model); lastFailAt.remove(model)
            return false
        }
        return (failures[model] ?: 0) >= 3
    }

    fun averageLatency(model: String): Long {
        val list = latency[model] ?: return -1
        synchronized(list) { return if (list.isEmpty()) -1 else list.sum() / list.size }
    }

    /** Frees caches other modules hold, so an idle app does not keep memory busy. */
    fun freeMemory(context: Context): String {
        val parts = mutableListOf<String>()
        runCatching {
            parts.add(com.saathi.ai.web.WebBrowser.closeAll(context))
            parts.add(com.saathi.ai.web.WebBrowser.clearShots(context))
        }.onFailure { parts.add("ব্রাউজার ক্যাশ ছাড়া যায়নি: ${it.message}") }
        runCatching { latency.clear() }.onFailure { }
        return "সফল: অব্যবহৃত রিসোর্স ছাড়া হয়েছে\n" + parts.joinToString("\n") { "• $it" }
    }

    fun status(context: Context): String {
        val prefs = SecurePrefs(context)
        return buildString {
            appendLine("মডেল রাউটার")
            appendLine("• লাইভ/কথা: ${prefs.model}")
            appendLine("• ছোট দ্রুত কাজ: ${pick(Kind.FAST, context)}")
            appendLine("• গভীর বিশ্লেষণ/কোড: ${pick(Kind.REASON, context)}")
            appendLine("• স্ক্রিন কম্পিউটার-ইউজ: ${pick(Kind.COMPUTER, context)}")
            appendLine("• কণ্ঠ (TTS): ${pick(Kind.TTS, context)} · ভয়েস: ${prefs.ttsVoice}")
            appendLine("• ছবি পড়া: ${pick(Kind.VISION, context)} · গবেষণা: ${pick(Kind.RESEARCH, context)}")
            val demotedNow = failures.keys.filter { demoted(it) }
            appendLine("• এখন ডিমোট করা মডেল: ${if (demotedNow.isEmpty()) "নেই" else demotedNow.joinToString(", ")}")
            val lat = latency.entries.filter { averageLatency(it.key) > 0 }
                .sortedBy { averageLatency(it.key) }
            if (lat.isNotEmpty()) {
                appendLine("• গড় লেটেন্সি:")
                lat.take(6).forEach { appendLine("  - ${it.key}: ${averageLatency(it.key)} ms (${it.value.size} নমুনা)") }
            } else {
                appendLine("• গড় লেটেন্সি: এখনো কোনো মাপ নেই (কল হলেই জমা হবে)")
            }
            append("নিয়ম: একই মডেল ৩ বার ব্যর্থ হলে ১০ মিনিটের জন্য পরের বিকল্পে চলে যায়")
        }
    }

    /** Clears the demotion table — used after the user fixes the API key. */
    fun reset() {
        failures.clear()
        lastFailAt.clear()
    }
}
