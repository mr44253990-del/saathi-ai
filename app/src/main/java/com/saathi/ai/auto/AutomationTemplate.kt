package com.saathi.ai.auto

import android.content.Context
import com.saathi.ai.core.ApprovalGate
import com.saathi.ai.core.ErrorCenter
import com.saathi.ai.data.TaskHistory
import com.saathi.ai.task.TaskEngine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * User-created reusable automations (Phase 18).
 *
 * A template is a name plus the ordered steps that really ran (or that the user
 * described). Templates live in `filesDir/jimi-automations.json`, can be exported as
 * text and re-imported, and are executed through the normal task engine — so they get
 * the same approval gate, error centre and kill switch as any other work. Nothing here
 * bypasses a permission check: a template that needs a blocked action stops there.
 */

data class AutomationTemplate(
    val id: String,
    val name: String,
    val goal: String,
    val steps: List<String>,
    val createdAt: Long = System.currentTimeMillis(),
    var runs: Int = 0,
    var lastResult: String = "",
    var lastRunAt: Long = 0L
) {
    val risky: Boolean get() = steps.any { ApprovalGate.classify(it, "").ordinal >= ApprovalGate.Risk.HIGH.ordinal }
}

class TemplateStore(private val context: Context) {

    private val file: File get() = File(context.filesDir, "jimi-automations.json")

    fun all(): List<AutomationTemplate> = try {
        if (!file.exists()) emptyList()
        else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AutomationTemplate(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    goal = o.optString("goal"),
                    steps = o.optJSONArray("steps")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                    createdAt = o.optLong("createdAt"),
                    runs = o.optInt("runs"),
                    lastResult = o.optString("lastResult"),
                    lastRunAt = o.optLong("lastRunAt")
                )
            }
        }
    } catch (t: Throwable) {
        ErrorCenter.logThrow("file", "অটোমেশন টেমপ্লেট পড়া যায়নি", t)
        emptyList()
    }

    private fun save(list: List<AutomationTemplate>) {
        try {
            val arr = JSONArray()
            list.forEach { t ->
                arr.put(JSONObject().apply {
                    put("id", t.id); put("name", t.name); put("goal", t.goal)
                    put("steps", JSONArray(t.steps))
                    put("createdAt", t.createdAt); put("runs", t.runs)
                    put("lastResult", t.lastResult.take(300)); put("lastRunAt", t.lastRunAt)
                })
            }
            file.writeText(arr.toString())
        } catch (t: Throwable) {
            ErrorCenter.logThrow("file", "অটোমেশন টেমপ্লেট লেখা যায়নি", t)
        }
    }

    /** Saves a new template (or replaces one with the same name). */
    fun save(name: String, goal: String, steps: List<String>): String {
        val n = name.trim().take(60)
        if (n.isBlank()) return "ব্যর্থ: টেমপ্লেটের নাম দাও"
        val clean = steps.map { it.trim() }.filter { it.isNotBlank() }.take(20)
        if (clean.isEmpty()) return "ব্যর্থ: অন্তত একটি ধাপ দাও"
        val list = all().toMutableList()
        val existing = list.indexOfFirst { it.name.equals(n, true) }
        val t = AutomationTemplate(
            id = if (existing >= 0) list[existing].id else "a${System.currentTimeMillis()}",
            name = n,
            goal = goal.ifBlank { clean.joinToString(" তারপর ") }.take(300),
            steps = clean,
            createdAt = if (existing >= 0) list[existing].createdAt else System.currentTimeMillis(),
            runs = if (existing >= 0) list[existing].runs else 0
        )
        if (existing >= 0) list[existing] = t else list.add(0, t)
        save(list)
        val warn = if (t.risky) "\nসতর্কতা: কিছু ধাপ সংবেদনশীল — চালানোর সময় অনুমতি চাওয়া হবে।" else ""
        return "সফল: অটোমেশন «$n» সংরক্ষিত হয়েছে (${clean.size} ধাপ)$warn"
    }

    /** Queues the template on the task engine; sensitive steps still need approval. */
    fun run(nameOrId: String, priority: Int = 3): String {
        val t = all().firstOrNull { it.id == nameOrId || it.name.equals(nameOrId.trim(), true) }
            ?: return "ব্যর্থ: «$nameOrId» নামে কোনো অটোমেশন নেই"
        val goal = buildString {
            append(t.goal.ifBlank { t.name })
            append(" (ধাপ: ")
            append(t.steps.joinToString(" → ") { it.take(40) })
            append(")")
        }
        val out = TaskEngine.start(context, goal.take(600), priority, budgetSteps = (t.steps.size + 4).coerceIn(4, 30))
        mark(t.id, out)
        return "সফল: অটোমেশন «${t.name}» চালু হয়েছে (${t.runs + 1}তম বার)\n$out"
    }

    private fun mark(id: String, result: String) {
        val list = all().toMutableList()
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return
        val t = list[i]
        list[i] = t.copy(runs = t.runs + 1, lastResult = result.take(200), lastRunAt = System.currentTimeMillis())
        save(list)
    }

    /** Builds a template out of a run that already succeeded — real macro recording. */
    fun fromHistory(runId: String, name: String = ""): String {
        val r = TaskHistory(context).get(runId)
            ?: return "ব্যর্থ: $runId আইডির কোনো টাস্ক ইতিহাসে নেই"
        val steps = r.steps.filter { it.isNotBlank() }.take(20)
        if (steps.isEmpty()) return "আংশিক: ওই টাস্কের ধাপগুলো সংরক্ষিত হয়নি, তাই টেমপ্লেট বানানো যায়নি"
        return save(name.ifBlank { r.goal.take(40) }, r.goal, steps)
    }

    fun list(limit: Int = 25): String {
        val all = all()
        if (all.isEmpty()) {
            return "কোনো অটোমেশন নেই। «টেমপ্লেট বানাও: নাম — ধাপ ১, ধাপ ২» বললে সংরক্ষিত হবে, " +
                "অথবা শেষ হওয়া কোনো টাস্ক থেকে «এটাকে টেমপ্লেট বানাও» বলো।"
        }
        return buildString {
            appendLine("অটোমেশন টেমপ্লেট: ${all.size}টি")
            all.take(limit).forEach { t ->
                appendLine("• ${t.name} — ${t.steps.size} ধাপ · ${t.runs} বার চালানো${if (t.risky) " · সংবেদনশীল ধাপ আছে" else ""}")
                if (t.lastResult.isNotBlank()) appendLine("  শেষ ফল: ${t.lastResult.take(90)}")
            }
            append("ফাইল: ${file.absolutePath}")
        }
    }

    fun detail(nameOrId: String): String {
        val t = all().firstOrNull { it.id == nameOrId || it.name.equals(nameOrId.trim(), true) }
            ?: return "ব্যর্থ: পাওয়া যায়নি — $nameOrId"
        return buildString {
            appendLine("নাম: ${t.name} (আইডি ${t.id})")
            appendLine("লক্ষ্য: ${t.goal}")
            appendLine("ধাপ:")
            t.steps.forEachIndexed { i, s -> appendLine("${i + 1}. $s") }
            appendLine("চালানো হয়েছে: ${t.runs} বার")
            if (t.lastResult.isNotBlank()) appendLine("শেষ ফল: ${t.lastResult}")
            if (t.risky) appendLine("সতর্কতা: সংবেদনশীল ধাপ আছে — প্রতিবার অনুমতি লাগবে")
        }
    }

    /** Plain-text export the user can keep, share or edit by hand. */
    fun export(nameOrId: String = ""): String {
        val list = if (nameOrId.isBlank()) all()
        else all().filter { it.id == nameOrId || it.name.equals(nameOrId.trim(), true) }
        if (list.isEmpty()) return "ব্যর্থ: রপ্তানি করার মতো কিছু নেই"
        val text = buildString {
            appendLine("# Jimi অটোমেশন (এক্সপোর্ট)")
            list.forEach { t ->
                appendLine()
                appendLine("## ${t.name}")
                appendLine("goal: ${t.goal}")
                t.steps.forEach { appendLine("step: $it") }
            }
        }
        val out = File(context.getExternalFilesDir(null) ?: context.filesDir, "docs").apply { mkdirs() }
        val f = File(out, "jimi-automations-${System.currentTimeMillis() / 1000}.txt")
        return try {
            f.writeText(text)
            "সফল: অটোমেশন এক্সপোর্ট হয়েছে\nফাইল: ${f.absolutePath}"
        } catch (t2: Throwable) {
            "ব্যর্থ: এক্সপোর্ট লেখা যায়নি (${t2.message})"
        }
    }

    /** Imports templates from exported text (name / goal / step lines). */
    fun import(text: String): String {
        var name = ""; var goal = ""; val steps = mutableListOf<String>()
        var count = 0
        fun flush() {
            if (name.isNotBlank() && steps.isNotEmpty()) {
                save(name, goal, steps.toList())
                count++
            }
            name = ""; goal = ""; steps.clear()
        }
        text.lines().forEach { raw ->
            val l = raw.trim()
            when {
                l.startsWith("## ") -> { flush(); name = l.removePrefix("## ").trim() }
                l.startsWith("goal:") -> goal = l.removePrefix("goal:").trim()
                l.startsWith("step:") -> steps.add(l.removePrefix("step:").trim())
            }
        }
        flush()
        return if (count > 0) "সফল: $count টি অটোমেশন ইমপোর্ট হয়েছে"
        else "ব্যর্থ: ইমপোর্ট করার মতো বৈধ টেমপ্লেট পাওয়া যায়নি (## নাম / goal: / step: ফরম্যাট দরকার)"
    }

    fun delete(nameOrId: String): String {
        val list = all().toMutableList()
        val before = list.size
        list.removeAll { it.id == nameOrId || it.name.equals(nameOrId.trim(), true) }
        if (list.size == before) return "ব্যর্থ: পাওয়া যায়নি — $nameOrId"
        save(list)
        return "সফল: অটোমেশন মুছে ফেলা হয়েছে"
    }

    /** Suggests a saved template when a new goal looks like an old one. */
    fun suggest(goal: String): AutomationTemplate? {
        val words = goal.lowercase().split(Regex("[\\s,।;]+")).filter { it.length > 3 }.toSet()
        if (words.isEmpty()) return null
        return all().mapNotNull { t ->
            val tw = (t.name + " " + t.goal).lowercase().split(Regex("[\\s,।;]+")).filter { it.length > 3 }.toSet()
            val hits = words.intersect(tw).size
            if (hits >= 2) t to hits else null
        }.maxByOrNull { it.second }?.first
    }

    fun count(): Int = all().size
}

/**
 * Live action recorder.
 *
 * [record] is called by the automation path (ActionMapper) whenever a real gesture or
 * key action is executed, so a recorded macro is a transcript of what actually
 * happened on the device — not a guess. Recording is off by default and everything
 * captured stays in memory until the user saves it as a template.
 */
object MacroRecorder {

    @Volatile var recording: Boolean = false
        private set

    private val captured = mutableListOf<String>()
    private var startedAt = 0L

    fun start(): String {
        captured.clear()
        startedAt = System.currentTimeMillis()
        recording = true
        return "রেকর্ডিং শুরু — এখন যা যা করাবে সেটা ধাপ হিসেবে জমা হবে। থামাতে «রেকর্ডিং থামাও» বলো।"
    }

    fun stop(): String {
        recording = false
        val secs = if (startedAt > 0) (System.currentTimeMillis() - startedAt) / 1000 else 0
        return if (captured.isEmpty()) "রেকর্ডিং থেমেছে — কোনো ধাপ জমা হয়নি ($secs সেকেন্ড)"
        else "রেকর্ডিং থেমেছে — ${captured.size}টি ধাপ জমা হয়েছে ($secs সেকেন্ড)"
    }

    /** Called from the execution path; ignored unless recording is on. */
    fun record(action: String, detail: String = "") {
        if (!recording) return
        val line = "$action${if (detail.isBlank()) "" else " — ${detail.take(80)}"}"
        synchronized(captured) { if (captured.size < 40) captured.add(line) }
    }

    fun steps(): List<String> = synchronized(captured) { captured.toList() }

    fun preview(): String {
        val s = steps()
        if (s.isEmpty()) return "এখনো কিছু রেকর্ড হয়নি"
        return buildString {
            appendLine("রেকর্ড হওয়া ধাপ (${s.size}):")
            s.forEachIndexed { i, l -> appendLine("${i + 1}. $l") }
        }
    }

    /** Turns the recording into a reusable template. */
    fun saveAs(context: Context, name: String): String {
        val s = steps()
        if (s.isEmpty()) return "ব্যর্থ: রেকর্ড করা কোনো ধাপ নেই — আগে কিছু চালাও"
        val out = TemplateStore(context).save(name, s.joinToString(" তারপর "), s)
        captured.clear()
        recording = false
        return out
    }

    /** Builds a template from a natural-language description (offline splitter). */
    fun fromText(context: Context, name: String, description: String): String {
        val steps = splitSteps(description)
        if (steps.isEmpty()) return "ব্যর্থ: বর্ণনা থেকে কোনো ধাপ বের করা যায়নি"
        return TemplateStore(context).save(name, description, steps)
    }

    /** Same connector rules the planner uses, kept local so this never depends on it. */
    private fun splitSteps(text: String): List<String> {
        val parts = text.split(Regex("(এবং|তারপর|এরপর|পরে|শেষে|,|;|\\.|।|\n)"))
        return parts.map { it.trim() }.filter { it.length > 3 }.take(20)
    }

    fun status(): String = buildString {
        append(if (recording) "রেকর্ডিং চলছে" else "রেকর্ডিং বন্ধ")
        append(" · জমা ধাপ: ${steps().size}")
    }
}
