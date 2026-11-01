package com.saathi.ai.data

import android.content.Context
import com.saathi.ai.core.ErrorCenter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unified memory search (Phase 11).
 *
 * One query, four stores: remembered facts, chat history, task history and files on
 * disk. Results are ranked by a transparent score (exact phrase > word overlap >
 * recency) and every hit says where it came from, so the user can verify rather than
 * trust.
 */
class MemorySearch(private val context: Context) {

    data class Hit(val source: String, val title: String, val snippet: String, val score: Int, val whenText: String) {
        fun line(): String = "[$source] $title — $snippet ($whenText)"
    }

    fun search(query: String, limit: Int = 12): List<Hit> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val words = q.lowercase().split(Regex("[\\s,।;?]+")).filter { it.length > 2 }.toSet()
        val hits = mutableListOf<Hit>()
        val fmt = SimpleDateFormat("dd MMM yy", Locale.US)
        try {
            MemoryStore(context).all().forEach { e ->
                val hay = "${e.key} ${e.value}".lowercase()
                val s = score(hay, q, words, kind = 5)
                if (s > 0) hits.add(Hit("মেমোরি", e.key.take(40), e.value.take(90), s, fmt.format(Date(e.createdAt))))
            }
            ChatStore(context).chats().forEach { c ->
                val hay = (c.title + " " + c.messages.joinToString(" ") { it.text }).lowercase()
                val s = score(hay, q, words, kind = 3)
                if (s > 0) {
                    val best = c.messages.firstOrNull { it.text.lowercase().contains(q.lowercase()) }
                        ?: c.messages.lastOrNull()
                    hits.add(
                        Hit(
                            "কথা", c.title.take(40),
                            (best?.text ?: "").take(90), s, fmt.format(Date(c.created))
                        )
                    )
                }
            }
            TaskHistory(context).all().forEach { r ->
                val hay = "${r.goal} ${r.report} ${r.steps.joinToString(" ")}".lowercase()
                val s = score(hay, q, words, kind = 4)
                if (s > 0) {
                    hits.add(
                        Hit(
                            "টাস্ক", "[${r.status}] ${r.goal.take(40)}",
                            r.report.lines().firstOrNull { it.isNotBlank() }?.take(90) ?: "",
                            s, fmt.format(Date(r.endedAt))
                        )
                    )
                }
            }
            com.saathi.ai.access.FileTools(context).find(q, limit).forEach { f ->
                // FileTools.Entry carries no timestamp, so read it from the file itself.
                val at = java.io.File(f.path).lastModified()
                hits.add(Hit("ফাইল", f.name.take(50), f.path.take(90), 6, fmt.format(Date(at))))
            }
        } catch (t: Throwable) {
            ErrorCenter.logThrow("system", "মেমোরি সার্চ ব্যর্থ", t)
        }
        return hits.sortedWith(compareByDescending<Hit> { it.score }.thenByDescending { it.whenText })
            .distinctBy { it.title + it.snippet }
            .take(limit)
    }

    private fun score(hay: String, q: String, words: Set<String>, kind: Int): Int {
        var s = 0
        if (hay.contains(q.lowercase())) s += 8 + kind
        words.forEach { if (hay.contains(it)) s += 2 }
        if (s > 0 && words.isNotEmpty()) s += (words.count { hay.contains(it) } * 100 / words.size) / 25
        return s
    }

    fun report(query: String, limit: Int = 10): String {
        val hits = search(query, limit)
        if (hits.isEmpty()) {
            return "কোনো ফল পাওয়া যায়নি — «$query» মেমোরি, কথা, টাস্ক ইতিহাস বা ফাইলে নেই।"
        }
        return buildString {
            appendLine("«$query» — ${hits.size}টি ফল:")
            hits.forEachIndexed { i, h ->
                appendLine("${i + 1}. [${h.source}] ${h.title}")
                appendLine("   ${h.snippet}")
                appendLine("   সময়: ${h.whenText} · স্কোর: ${h.score}")
            }
        }
    }

    /** Everything the app remembers, in one readable page (for the memory dashboard). */
    fun overview(): String {
        val mem = MemoryStore(context)
        val hist = TaskHistory(context)
        val proj = ProjectWorkspace(context)
        val chats = runCatching { ChatStore(context).chats().size }.getOrDefault(0)
        return buildString {
            appendLine("মেমোরি ড্যাশবোর্ড")
            appendLine("• মনে রাখা তথ্য: ${mem.all().size}টি · রিমাইন্ডার: ${mem.reminders().size}টি")
            appendLine("• কথা (চ্যাট): $chatsটি সেশন")
            appendLine("• টাস্ক ইতিহাস: ${hist.count()}টি")
            appendLine("• প্রজেক্ট ওয়ার্কস্পেস: ${proj.count()}টি")
            append(hist.stats())
        }
    }

    /** Deletion is per-store and explicit — the user stays in control. */
    fun forget(kind: String, id: String): String = when (kind.lowercase()) {
        "memory", "fact", "মেমোরি" -> {
            MemoryStore(context).forget(id)
            "সফল: তথ্যটি মুছে ফেলা হয়েছে"
        }
        "task", "history", "টাস্ক" -> TaskHistory(context).delete(id)
        "chat", "কথা" -> {
            ChatStore(context).delete(id)
            "সফল: চ্যাট সেশন মুছে ফেলা হয়েছে"
        }
        "project", "প্রজেক্ট" -> ProjectWorkspace(context).remove(id)
        else -> "আটকে: «$kind» ধরনের কিছু মুছব না — memory / task / chat / project লেখো"
    }

    fun forgetAll(what: String): String = when (what.lowercase()) {
        "memory" -> {
            MemoryStore(context).forgetAll()
            "সফল: সব মনে-রাখা তথ্য মুছে ফেলা হয়েছে"
        }
        "tasks" -> TaskHistory(context).clear()
        "chats" -> {
            ChatStore(context).clearAll()
            "সফল: সব চ্যাট মুছে ফেলা হয়েছে"
        }
        "all" -> {
            MemoryStore(context).forgetAll()
            TaskHistory(context).clear()
            ChatStore(context).clearAll()
            "সফল: মেমোরি, টাস্ক ইতিহাস ও চ্যাট — সব মুছে ফেলা হয়েছে"
        }
        else -> "আটকে: কী মুছব তা স্পষ্ট করো — memory / tasks / chats / all"
    }
}
