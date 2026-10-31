package com.saathi.ai.task

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The to-do board behind "big task" mode.
 *
 * When the user asks for something large ("সব ছবি ব্যাকআপ নাও, তারপর রিচার্জ করো,
 * তারপর আমাকে জানাও"), Jimi must not just talk about it: it writes a task list,
 * works through it in the background, and puts a tick next to each item as it
 * finishes. This class is that list — persistent (JSON on disk), observable
 * (StateFlow, so the bubble and the Tasks screen update live), and honest
 * (a task is DONE only when [TaskRunnerService] could verify it).
 */
class TaskBoard(private val context: Context) {

    enum class Status { PENDING, RUNNING, DONE, FAILED, BLOCKED }

    data class Task(
        val id: String,
        val title: String,
        val detail: String,
        val status: Status,
        val note: String,
        val attempts: Int,
        val createdAt: Long,
        val updatedAt: Long
    ) {
        val tick: String
            get() = when (status) {
                Status.DONE -> "[ok]"
                Status.RUNNING -> "[>>]"
                Status.FAILED -> "[x]"
                Status.BLOCKED -> "[!]"
                Status.PENDING -> "[ ]"
            }
    }

    private val file: File
        get() = File(context.filesDir, "jimi-tasks.json")

    fun all(): List<Task> {
        if (!loaded) load()
        return tasks.value
    }

    fun add(title: String, detail: String = ""): Task {
        if (!loaded) load()
        val t = Task(
            id = "t" + System.currentTimeMillis().toString(36) + (tasks.value.size + 1),
            title = title.trim().take(140),
            detail = detail.trim().take(600),
            status = Status.PENDING,
            note = "",
            attempts = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        tasks.value = tasks.value + t
        save()
        return t
    }

    /** Adds a whole plan at once — the model calls this for multi-step work. */
    fun addPlan(steps: List<String>): String {
        if (steps.isEmpty()) return "তালিকা খালি"
        steps.forEach { add(it) }
        return "${steps.size}টি কাজ তালিকায় যোগ করেছি — ব্যাকগ্রাউন্ডে চলবে, শেষ হলে টিক পড়বে।"
    }

    fun update(id: String, status: Status, note: String = ""): Task? {
        if (!loaded) load()
        var changed: Task? = null
        tasks.value = tasks.value.map {
            if (it.id == id) {
                changed = it.copy(
                    status = status,
                    note = note.ifBlank { it.note },
                    attempts = if (status == Status.FAILED || status == Status.BLOCKED) it.attempts + 1 else it.attempts,
                    updatedAt = System.currentTimeMillis()
                )
                changed!!
            } else it
        }
        save()
        return changed
    }

    fun nextPending(): Task? = all().firstOrNull { it.status == Status.PENDING }

    fun running(): Task? = all().firstOrNull { it.status == Status.RUNNING }

    fun remove(id: String): String {
        val before = all().size
        tasks.value = all().filterNot { it.id == id }
        save()
        return if (tasks.value.size < before) "কাজটা তালিকা থেকে সরিয়েছি" else "ওই আইডি পাওয়া যায়নি"
    }

    fun clearFinished(): String {
        val n = all().count { it.status == Status.DONE }
        tasks.value = all().filterNot { it.status == Status.DONE }
        save()
        return "${n}টি শেষ হওয়া কাজ তালিকা থেকে মুছেছি"
    }

    fun clearAll(): String {
        tasks.value = emptyList()
        save()
        return "পুরো তালিকা খালি করেছি"
    }

    /** 0..100 — drives the progress ring in the bubble and the Tasks screen. */
    fun progressPercent(): Int {
        val list = all()
        if (list.isEmpty()) return 0
        val done = list.count { it.status == Status.DONE }
        return (done * 100) / list.size
    }

    fun progressText(): String {
        val list = all()
        if (list.isEmpty()) return "তালিকা খালি"
        val done = list.count { it.status == Status.DONE }
        val failed = list.count { it.status == Status.FAILED }
        val running = list.count { it.status == Status.RUNNING }
        return "$done/${list.size} শেষ" +
            (if (running > 0) " · চলছে $running" else "") +
            (if (failed > 0) " · আটকেছে $failed" else "")
    }

    /** Text form handed to the model so it always knows what is left. */
    fun boardText(limit: Int = 24): String {
        val list = all()
        if (list.isEmpty()) return "টাস্ক তালিকা খালি। বড় কাজ দিলে এখানে লিখে ব্যাকগ্রাউন্ডে চালাব।"
        return buildString {
            appendLine("টাস্ক বোর্ড — ${progressText()}")
            list.take(limit).forEach { t ->
                appendLine("${t.tick} ${t.title}" + if (t.note.isNotBlank()) " — ${t.note.take(90)}" else "")
            }
            val left = list.size - limit
            if (left > 0) appendLine("…আরও ${left}টি")
        }
    }

    // ------------------------------------------------------------- persistence

    private fun load() {
        loaded = true
        val text = runCatching { file.readText() }.getOrDefault("")
        if (text.isBlank()) return
        runCatching {
            val arr = JSONArray(text)
            val out = ArrayList<Task>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    Task(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        detail = o.optString("detail"),
                        status = runCatching { Status.valueOf(o.optString("status")) }
                            .getOrDefault(Status.PENDING),
                        note = o.optString("note"),
                        attempts = o.optInt("attempts"),
                        createdAt = o.optLong("createdAt"),
                        updatedAt = o.optLong("updatedAt")
                    )
                )
            }
            tasks.value = out
        }
    }

    private fun save() {
        runCatching {
            val arr = JSONArray()
            tasks.value.forEach { t ->
                arr.put(
                    JSONObject()
                        .put("id", t.id)
                        .put("title", t.title)
                        .put("detail", t.detail)
                        .put("status", t.status.name)
                        .put("note", t.note)
                        .put("attempts", t.attempts)
                        .put("createdAt", t.createdAt)
                        .put("updatedAt", t.updatedAt)
                )
            }
            file.writeText(arr.toString())
        }
    }

    companion object {
        /** Shared across UI + service so both see the same list instantly. */
        private val tasks = MutableStateFlow<List<Task>>(emptyList())

        @Volatile
        private var loaded = false

        /** Live list for Compose screens (`collectAsState`). */
        val live = tasks
    }
}
