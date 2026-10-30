package com.saathi.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything Jimi remembers, in one place.
 *
 * Three kinds of memory:
 *  • **facts** — "আমার বাসা সাভারে", saved by the `remember` tool
 *  • **preferences** — theme, voice, language, habits
 *  • **reminders** — medicine / tasks with a time, shown on the Memory page
 *
 * The old implementation was a single newline-joined string, which made it
 * impossible to show, edit or delete individual memories. This is a small JSON
 * store with an id + timestamp per entry.
 */
class MemoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("jimi_memory", Context.MODE_PRIVATE)

    data class Entry(
        val id: String,
        val key: String,
        val value: String,
        val createdAt: Long,
        val kind: String = "fact"
    )

    // ---------------------------------------------------------------- facts

    fun all(): List<Entry> {
        val raw = prefs.getString(KEY_FACTS, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(
                    id = o.optString("id"),
                    key = o.optString("key"),
                    value = o.optString("value"),
                    createdAt = o.optLong("ts"),
                    kind = o.optString("kind", "fact")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun remember(key: String, value: String, kind: String = "fact"): Entry {
        val list = all().toMutableList()
        // same key replaces the old value — memories should not pile up duplicates
        list.removeAll { it.key.equals(key, ignoreCase = true) }
        val entry = Entry(
            id = System.currentTimeMillis().toString(36) + (0..999).random(),
            key = key.trim().take(60),
            value = value.trim().take(400),
            createdAt = System.currentTimeMillis(),
            kind = kind
        )
        list.add(entry)
        save(list)
        return entry
    }

    fun forget(id: String) {
        save(all().filterNot { it.id == id })
    }

    fun forgetAll() {
        prefs.edit().remove(KEY_FACTS).apply()
    }

    private fun save(list: List<Entry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("key", it.key)
                    .put("value", it.value)
                    .put("ts", it.createdAt)
                    .put("kind", it.kind)
            )
        }
        prefs.edit().putString(KEY_FACTS, arr.toString()).apply()
    }

    /** Prompt block handed to the model at session start. */
    fun promptBlock(): String {
        val facts = all()
        if (facts.isEmpty()) return ""
        val lines = facts.joinToString("\n") { "• ${it.key}: ${it.value}" }
        return "\n\nব্যবহারকারীর সম্পর্কে যা তুমি আগে মনে রেখেছ:\n$lines"
    }

    // ---------------------------------------------------------------- reminders

    data class Reminder(val id: String, val text: String, val time: String, val createdAt: Long)

    fun reminders(): List<Reminder> {
        val raw = prefs.getString(KEY_REMINDERS, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Reminder(
                    o.optString("id"),
                    o.optString("text"),
                    o.optString("time"),
                    o.optLong("ts")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun addReminder(text: String, time: String) {
        val list = reminders().toMutableList()
        list.add(
            Reminder(
                System.currentTimeMillis().toString(36),
                text.trim().take(120),
                time.trim().take(30),
                System.currentTimeMillis()
            )
        )
        saveReminders(list)
    }

    fun removeReminder(id: String) {
        saveReminders(reminders().filterNot { it.id == id })
    }

    private fun saveReminders(list: List<Reminder>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("text", it.text)
                    .put("time", it.time).put("ts", it.createdAt)
            )
        }
        prefs.edit().putString(KEY_REMINDERS, arr.toString()).apply()
    }

    // ---------------------------------------------------------------- niceties

    fun humanDate(ts: Long): String =
        SimpleDateFormat("d MMM, hh:mm a", Locale("bn", "BD")).format(Date(ts))

    fun count(): Int = all().size + reminders().size

    companion object {
        private const val KEY_FACTS = "facts"
        private const val KEY_REMINDERS = "reminders"
    }
}
