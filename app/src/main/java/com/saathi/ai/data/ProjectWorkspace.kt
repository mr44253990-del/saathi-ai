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
 * AI project workspace (Phase 18).
 *
 * A project is a named bundle of notes, files and related tasks, so work that spans
 * days keeps its context instead of starting from zero. Selecting a project makes its
 * notes part of the prompt (through [ContextSummarizer.workingContext]) — that is the
 * "smart context awareness" requirement, implemented with plain local JSON.
 *
 * Storage: `filesDir/jimi-projects.json`.
 */
class ProjectWorkspace(private val context: Context) {

    data class Project(
        val id: String,
        val name: String,
        var note: String,
        val files: MutableList<String> = mutableListOf(),
        val tasks: MutableList<String> = mutableListOf(),
        val createdAt: Long = System.currentTimeMillis(),
        var updatedAt: Long = System.currentTimeMillis()
    )

    private val file: File get() = File(context.filesDir, "jimi-projects.json")
    private val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.US)

    fun all(): List<Project> = try {
        if (!file.exists()) emptyList()
        else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Project(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    note = o.optString("note"),
                    files = o.optJSONArray("files")?.let { a -> (0 until a.length()).map { a.getString(it) } }
                        ?.toMutableList() ?: mutableListOf(),
                    tasks = o.optJSONArray("tasks")?.let { a -> (0 until a.length()).map { a.getString(it) } }
                        ?.toMutableList() ?: mutableListOf(),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                )
            }
        }
    } catch (t: Throwable) {
        ErrorCenter.logThrow("file", "প্রজেক্ট পড়া যায়নি", t)
        emptyList()
    }

    private fun save(list: List<Project>) {
        try {
            val arr = JSONArray()
            list.forEach { p ->
                arr.put(JSONObject().apply {
                    put("id", p.id); put("name", p.name); put("note", p.note)
                    put("files", JSONArray(p.files)); put("tasks", JSONArray(p.tasks))
                    put("createdAt", p.createdAt); put("updatedAt", p.updatedAt)
                })
            }
            file.writeText(arr.toString())
        } catch (t: Throwable) {
            ErrorCenter.logThrow("file", "প্রজেক্ট লেখা যায়নি", t)
        }
    }

    private fun persist(p: Project) {
        val list = all().toMutableList()
        val i = list.indexOfFirst { it.id == p.id }
        p.updatedAt = System.currentTimeMillis()
        if (i >= 0) list[i] = p else list.add(0, p)
        save(list)
    }

    fun create(name: String, note: String = ""): String {
        val clean = name.trim().take(60)
        if (clean.isBlank()) return "ব্যর্থ: প্রজেক্টের নাম দাও"
        if (all().any { it.name.equals(clean, true) }) return "আংশিক: «$clean» নামে প্রজেক্ট আগেই আছে — সেটিতেই যোগ করছি"
        val p = Project(id = "p${System.currentTimeMillis()}", name = clean, note = note.take(4000))
        persist(p)
        selectedId = p.id
        return "সফল: প্রজেক্ট «$clean» তৈরি হয়েছে এবং নির্বাচিত হয়েছে (আইডি ${p.id})"
    }

    fun open(nameOrId: String): String {
        val q = nameOrId.trim().lowercase()
        val p = all().firstOrNull { it.id == q || it.name.lowercase().contains(q) }
            ?: return "ব্যর্থ: «$nameOrId» নামে কোনো প্রজেক্ট নেই"
        selectedId = p.id
        return "সফল: প্রজেক্ট «${p.name}» নির্বাচিত হয়েছে\n${brief(p)}"
    }

    fun current(): Project? {
        val list = all()
        return list.firstOrNull { it.id == selectedId } ?: list.firstOrNull()
    }

    /** Short block injected into the prompt when a project is active. */
    fun currentBrief(maxChars: Int = 900): String {
        val p = current() ?: return ""
        val sb = StringBuilder()
        sb.appendLine("বর্তমান প্রজেক্ট: ${p.name}")
        if (p.note.isNotBlank()) sb.appendLine("নোট: ${p.note.take(600)}")
        if (p.files.isNotEmpty()) sb.appendLine("ফাইল: ${p.files.take(6).joinToString(", ") { File(it).name }}")
        if (p.tasks.isNotEmpty()) sb.appendLine("সংশ্লিষ্ট টাস্ক: ${p.tasks.take(4).joinToString(", ")}")
        return sb.toString().take(maxChars)
    }

    fun addNote(text: String): String {
        val p = current() ?: return "ব্যর্থ: আগে একটি প্রজেক্ট নির্বাচন করো"
        if (text.isBlank()) return "ব্যর্থ: নোট খালি"
        p.note = (p.note + "\n[${fmt.format(Date())}] " + text.take(600)).take(6000)
        persist(p)
        return "সফল: প্রজেক্ট «${p.name}»-এ নোট যোগ হয়েছে"
    }

    fun addFile(path: String): String {
        val p = current() ?: return "ব্যর্থ: আগে একটি প্রজেক্ট নির্বাচন করো"
        if (!File(path).exists()) return "ব্যর্থ: ফাইলটি পাওয়া যায়নি — $path"
        if (p.files.contains(path)) return "আংশিক: ফাইলটি আগেই এই প্রজেক্টে আছে"
        p.files.add(path)
        if (p.files.size > 40) p.files.removeAt(0)
        persist(p)
        return "সফল: ফাইল যোগ হয়েছে (${File(path).name}) · মোট ${p.files.size}টি"
    }

    fun attachTask(label: String): String {
        val p = current() ?: return "ব্যর্থ: আগে একটি প্রজেক্ট নির্বাচন করো"
        p.tasks.add(label.take(120))
        if (p.tasks.size > 30) p.tasks.removeAt(0)
        persist(p)
        return "সফল: টাস্ক প্রজেক্টের সঙ্গে যুক্ত হয়েছে"
    }

    fun remove(id: String): String {
        val list = all().toMutableList()
        val before = list.size
        list.removeAll { it.id == id || it.name.equals(id, true) }
        if (list.size == before) return "ব্যর্থ: প্রজেক্ট পাওয়া যায়নি — $id"
        save(list)
        if (selectedId == id) selectedId = list.firstOrNull()?.id
        return "সফল: প্রজেক্ট মুছে ফেলা হয়েছে (ফাইলগুলো ডিস্কে আছে, শুধু তালিকা থেকে সরানো হয়েছে)"
    }

    fun list(limit: Int = 20): String {
        val all = all()
        if (all.isEmpty()) return "কোনো প্রজেক্ট নেই — «প্রজেক্ট বানাও: নাম» বললে শুরু হবে"
        return buildString {
            appendLine("প্রজেক্ট ওয়ার্কস্পেস: ${all.size}টি")
            all.take(limit).forEach { p ->
                val mark = if (p.id == selectedId) "[চলমান] " else ""
                appendLine("$mark• ${p.name} — ফাইল ${p.files.size}, টাস্ক ${p.tasks.size}, হালনাগাদ ${fmt.format(Date(p.updatedAt))}")
            }
        }
    }

    fun brief(p: Project): String = buildString {
        appendLine("নাম: ${p.name} (আইডি ${p.id})")
        appendLine("তৈরি: ${fmt.format(Date(p.createdAt))} · হালনাগাদ: ${fmt.format(Date(p.updatedAt))}")
        if (p.note.isNotBlank()) {
            appendLine("নোট:")
            p.note.lines().takeLast(8).forEach { appendLine("  $it") }
        }
        if (p.files.isNotEmpty()) {
            appendLine("ফাইল (${p.files.size}):")
            p.files.take(10).forEach { appendLine("  • $it") }
        }
        if (p.tasks.isNotEmpty()) {
            appendLine("টাস্ক (${p.tasks.size}):")
            p.tasks.take(8).forEach { appendLine("  • $it") }
        }
    }

    fun detail(nameOrId: String = ""): String {
        val p = if (nameOrId.isBlank()) current() else all().firstOrNull { it.id == nameOrId || it.name == nameOrId }
        return p?.let { brief(it) } ?: "ব্যর্থ: প্রজেক্ট পাওয়া যায়নি"
    }

    fun count(): Int = all().size

    companion object {
        @Volatile var selectedId: String? = null
            private set

        /** Called by the dashboard when the user picks a project from the list. */
        fun select(id: String?) { selectedId = id }
    }
}
