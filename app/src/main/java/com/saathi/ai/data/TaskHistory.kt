package com.saathi.ai.data

import android.content.Context
import com.saathi.ai.core.ErrorCenter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Long-term task memory (Phase 11).
 *
 * Every finished run is written here with its goal, the steps that actually ran,
 * the verdict and the error lines. Two things depend on it:
 *  - the user can read, search and delete their own history (nothing is hidden),
 *  - [failurePatterns] feeds the recovery agent, so a step that keeps failing the
 *    same way is recognised instead of being retried blindly.
 *
 * Storage: `filesDir/jimi-task-history.json`, capped at [MAX] entries. Reports are
 * truncated on the way in so a long chat cannot grow this file without limit.
 */
class TaskHistory(private val context: Context) {

    data class Record(
        val id: String,
        val goal: String,
        val status: String,
        val report: String,
        val steps: List<String>,
        val errors: List<String>,
        val artifacts: List<String>,
        val startedAt: Long,
        val endedAt: Long
    ) {
        val seconds: Int get() = ((endedAt - startedAt) / 1000).toInt()
    }

    private val file: File get() = File(context.filesDir, "jimi-task-history.json")

    fun all(): List<Record> = try {
        if (!file.exists()) emptyList()
        else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Record(
                    id = o.optString("id"),
                    goal = o.optString("goal"),
                    status = o.optString("status"),
                    report = o.optString("report"),
                    steps = o.optJSONArray("steps")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                    errors = o.optJSONArray("errors")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                    artifacts = o.optJSONArray("artifacts")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                    startedAt = o.optLong("startedAt"),
                    endedAt = o.optLong("endedAt")
                )
            }
        }
    } catch (t: Throwable) {
        ErrorCenter.logThrow("file", "টাস্ক ইতিহাস পড়া যায়নি", t)
        emptyList()
    }

    /** Appends one finished run. Called by the task engine, never from the UI thread. */
    fun add(
        goal: String,
        status: String,
        report: String,
        steps: List<String> = emptyList(),
        errors: List<String> = emptyList(),
        artifacts: List<String> = emptyList(),
        startedAt: Long = System.currentTimeMillis(),
        endedAt: Long = System.currentTimeMillis()
    ): String {
        val id = "t${endedAt}"
        val list = all().toMutableList()
        list.add(
            0,
            Record(
                id = id,
                goal = goal.take(200),
                status = status.take(20),
                report = report.take(REPORT_CHARS),
                steps = steps.take(40).map { it.take(160) },
                errors = errors.take(12).map { it.take(220) },
                artifacts = artifacts.take(12).map { it.take(220) },
                startedAt = startedAt,
                endedAt = endedAt
            )
        )
        save(list.take(MAX))
        return id
    }

    private fun save(list: List<Record>) {
        try {
            val arr = JSONArray()
            list.forEach { r ->
                arr.put(JSONObject().apply {
                    put("id", r.id); put("goal", r.goal); put("status", r.status)
                    put("report", r.report)
                    put("steps", JSONArray(r.steps)); put("errors", JSONArray(r.errors))
                    put("artifacts", JSONArray(r.artifacts))
                    put("startedAt", r.startedAt); put("endedAt", r.endedAt)
                })
            }
            file.writeText(arr.toString())
        } catch (t: Throwable) {
            ErrorCenter.logThrow("file", "টাস্ক ইতিহাস লেখা যায়নি", t)
        }
    }

    fun recent(n: Int = 8): List<Record> = all().take(n)

    fun get(id: String): Record? = all().firstOrNull { it.id == id }

    fun search(query: String, limit: Int = 12): List<Record> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return recent(limit)
        return all().filter {
            it.goal.lowercase().contains(q) || it.report.lowercase().contains(q) ||
                it.steps.any { s -> s.lowercase().contains(q) }
        }.take(limit)
    }

    /** What keeps breaking — used by the recovery agent to avoid the same dead end. */
    fun failurePatterns(): List<String> {
        val counts = LinkedHashMap<String, Int>()
        all().take(60).forEach { r ->
            r.errors.forEach { e ->
                val key = e.lowercase().take(90)
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        return counts.entries.filter { it.value > 1 }.sortedByDescending { it.value }
            .map { "• ${it.key} — ${it.value} বার" }
    }

    /** Tasks similar to a new goal, so the engine can reuse a known-good plan. */
    fun similar(goal: String, limit: Int = 3): List<Record> {
        val words = goal.lowercase().split(Regex("[\\s,।;]+")).filter { it.length > 3 }.toSet()
        if (words.isEmpty()) return emptyList()
        return all().mapNotNull { r ->
            val rw = r.goal.lowercase().split(Regex("[\\s,।;]+")).filter { it.length > 3 }.toSet()
            val hits = words.intersect(rw).size
            if (hits > 0) r to hits else null
        }.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    fun stats(): String {
        val list = all()
        if (list.isEmpty()) return "এখনো কোনো টাস্কের ইতিহাস নেই"
        val done = list.count { it.status.contains("DONE") }
        val part = list.count { it.status.contains("PARTIAL") }
        val failed = list.count { it.status.contains("FAILED") || it.status.contains("BLOCKED") }
        val avg = if (list.isNotEmpty()) list.sumOf { it.seconds } / list.size else 0
        return buildString {
            appendLine("টাস্ক ইতিহাস: ${list.size}টি সংরক্ষিত")
            appendLine("• সম্পূর্ণ: $done · আংশিক: $part · ব্যর্থ/আটকে: $failed")
            appendLine("• গড় সময়: ${avg} সেকেন্ড")
            val patterns = failurePatterns()
            if (patterns.isNotEmpty()) {
                appendLine("• বারবার যেসব সমস্যা:")
                patterns.take(5).forEach { appendLine("  $it") }
            }
        }
    }

    fun report(limit: Int = 10): String {
        val list = recent(limit)
        if (list.isEmpty()) return "টাস্ক ইতিহাস খালি"
        val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.US)
        return buildString {
            appendLine("সাম্প্রতিক টাস্ক (${list.size}টি):")
            list.forEachIndexed { i, r ->
                appendLine("${i + 1}. [${r.status}] ${r.goal}")
                appendLine("   ${fmt.format(Date(r.endedAt))} · ${r.seconds} সে. · ধাপ ${r.steps.size}")
                val first = r.report.lines().firstOrNull { it.isNotBlank() }?.take(140)
                if (first != null) appendLine("   ফল: $first")
            }
        }
    }

    /** User-controlled deletion — history is theirs, not ours. */
    fun delete(id: String): String {
        val list = all().toMutableList()
        val before = list.size
        list.removeAll { it.id == id }
        if (list.size == before) return "ব্যর্থ: এই আইডিতে কোনো রেকর্ড নেই — $id"
        save(list)
        return "সফল: টাস্ক ইতিহাস থেকে ১টি রেকর্ড মুছে ফেলা হয়েছে"
    }

    fun clear(): String {
        if (all().isEmpty()) return "ইতিহাস আগেই খালি ছিল"
        save(emptyList())
        return "সফল: পুরো টাস্ক ইতিহাস মুছে ফেলা হয়েছে"
    }

    fun count(): Int = all().size

    companion object {
        private const val MAX = 120
        private const val REPORT_CHARS = 1200
    }
}
