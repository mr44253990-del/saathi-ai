package com.saathi.ai.data

import android.content.Context
import com.saathi.ai.core.ErrorCenter

/**
 * Context compression (Phase 11).
 *
 * Long conversations and long task reports blow up the prompt, which costs latency
 * and makes the model lose the thread. This summariser is deliberately **rule-based
 * and offline**: extractive selection of the lines that carry information (verdicts,
 * numbers, file paths, decisions), newest-last, inside a hard character budget.
 *
 * It never invents content — every sentence in the output exists verbatim in the
 * input, so a summary cannot become a hallucination.
 */
class ContextSummarizer(private val context: Context) {

    data class Brief(val text: String, val keptLines: Int, val droppedLines: Int, val chars: Int) {
        fun line(): String = "সংক্ষেপ: $keptLines লাইন রাখা, $droppedLines বাদ · $chars অক্ষর"
    }

    /** Compresses arbitrary text into [maxChars], keeping the informative lines. */
    fun compress(text: String, maxChars: Int = DEFAULT_CHARS): Brief {
        val lines = text.lines().map { it.trimEnd() }
        val scored = lines.mapIndexed { i, l -> i to score(l) }
        var kept = scored.filter { it.second > 0 }.map { it.first }.toMutableList()
        if (kept.isEmpty()) kept = lines.indices.toMutableList()

        // Always keep the head (what was asked) and the tail (what happened last).
        val mustKeep = (lines.indices.take(2) + lines.indices.takeLast(3)).distinct()
        val selected = (mustKeep + kept).distinct().sorted()

        val sb = StringBuilder()
        var used = 0
        var dropped = 0
        var lastIdx = -2
        selected.forEach { idx ->
            if (idx >= lines.size) return@forEach
            val l = lines[idx]
            if (l.isBlank()) return@forEach
            if (used + l.length + 2 > maxChars) {
                dropped++
                return@forEach
            }
            if (idx > lastIdx + 1 && lastIdx >= 0) sb.appendLine("…")
            sb.appendLine(l)
            used += l.length + 1
            lastIdx = idx
        }
        val out = sb.toString().trim()
        return Brief(
            text = if (out.isBlank()) text.take(maxChars) else out,
            keptLines = out.lines().size,
            droppedLines = lines.size - out.lines().size,
            chars = out.length
        )
    }

    /** How much a line is worth: verdicts, numbers, paths, decisions rank highest. */
    private fun score(line: String): Int {
        if (line.isBlank()) return -1
        val l = line.lowercase()
        var s = 0
        if (line.startsWith("সফল") || line.startsWith("ব্যর্থ") || line.startsWith("আংশিক") ||
            line.startsWith("আটকে")
        ) s += 6
        if (l.startsWith("[ok]") || l.startsWith("[x]") || l.startsWith("[!]")) s += 5
        if (line.startsWith("•") || line.startsWith("-") || line.matches(Regex("^\\d+[.)].*"))) s += 2
        if (l.contains("/storage/") || l.contains("/sdcard/") || l.contains("file://") ||
            l.contains(".jpg") || l.contains(".png") || l.contains(".pdf") || l.contains(".mp4")
        ) s += 3
        if (line.contains(Regex("\\d"))) s += 1
        if (l.contains("error") || l.contains("ব্যর্থ") || l.contains("ত্রুটি") || l.contains("exception")) s += 3
        if (l.contains("অনুমতি") || l.contains("permission") || l.contains("approval")) s += 2
        if (line.length > 400) s -= 2
        if (line.length < 3) s -= 3
        return s
    }

    /** Chat history → a prompt-sized block, oldest first, newest last. */
    fun chatBrief(chatId: String? = null, maxChars: Int = 2600): String {
        return try {
            val store = ChatStore(context)
            val chat = if (chatId != null) store.chats().firstOrNull { it.id == chatId }
            else store.chats().maxByOrNull { it.created }
            if (chat == null || chat.messages.isEmpty()) return ""
            val tail = chat.messages.takeLast(40).joinToString("\n") { m ->
                "${if (m.role == "user") "ব্যবহারকারী" else "Jimi"}: ${m.text.take(400)}"
            }
            compress(tail, maxChars).text
        } catch (t: Throwable) {
            ErrorCenter.log("system", "চ্যাট সংক্ষেপ ব্যর্থ", t.message)
            ""
        }
    }

    /** Everything the next prompt needs to know, assembled from local stores. */
    fun workingContext(goal: String, maxChars: Int = 3400): String {
        val parts = mutableListOf<String>()
        val mem = MemoryStore(context).promptBlock()
        if (mem.isNotBlank()) parts.add(mem)
        val hist = TaskHistory(context).similar(goal, 2).joinToString("\n") { r ->
            "আগের অনুরূপ টাস্ক [${r.status}]: ${r.goal} → ${r.report.lines().firstOrNull()?.take(160)}"
        }
        if (hist.isNotBlank()) parts.add(hist)
        val proj = ProjectWorkspace(context).currentBrief()
        if (proj.isNotBlank()) parts.add(proj)
        val chat = chatBrief(maxChars = 1200)
        if (chat.isNotBlank()) parts.add("সাম্প্রতিক কথা:\n$chat")
        return compress(parts.joinToString("\n"), maxChars).text
    }

    /** Rough token estimate, used by the router to avoid over-long prompts. */
    fun estimateTokens(text: String): Int = (text.length / 3).coerceAtLeast(1)

    fun status(): String = "কনটেক্সট সামারাইজার: অফলাইন, নিয়মভিত্তিক (লাইন স্কোরিং) · ডিফল্ট বাজেট $DEFAULT_CHARS অক্ষর"

    companion object {
        const val DEFAULT_CHARS = 3000
    }
}
