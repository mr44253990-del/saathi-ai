package com.saathi.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Conversation history.
 *
 * Every finished exchange is appended here, so "নতুন চ্যাট" (new chat) can start
 * clean while old conversations stay browsable and searchable. Kept in plain
 * SharedPreferences JSON — a handful of KB, no database needed.
 */
class ChatStore(context: Context) {

    private val prefs = context.getSharedPreferences("jimi_chats", Context.MODE_PRIVATE)

    data class Message(val role: String, val text: String, val ts: Long)
    data class Chat(val id: String, val title: String, val created: Long, val messages: List<Message>)

    fun chats(): List<Chat> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val msgsArr = o.optJSONArray("messages") ?: JSONArray()
                val msgs = (0 until msgsArr.length()).map { j ->
                    val m = msgsArr.getJSONObject(j)
                    Message(
                        m.optString("role", "user"),
                        m.optString("text", ""),
                        m.optLong("ts", 0L)
                    )
                }
                Chat(
                    o.optString("id"),
                    o.optString("title", "নতুন চ্যাট"),
                    o.optLong("created", 0L),
                    msgs
                )
            }
        }.getOrDefault(emptyList()).sortedByDescending { it.created }
    }

    fun newChat(): Chat {
        val chat = Chat(
            id = System.currentTimeMillis().toString(36),
            title = "নতুন চ্যাট",
            created = System.currentTimeMillis(),
            messages = emptyList()
        )
        save((chats() + chat).take(40))
        return chat
    }

    fun append(chatId: String, role: String, text: String) {
        if (text.isBlank()) return
        val list = chats().toMutableList()
        val idx = list.indexOfFirst { it.id == chatId }
        if (idx < 0) return
        val chat = list[idx]
        val msgs = chat.messages + Message(role, text.trim().take(1200), System.currentTimeMillis())
        val title = if (chat.title == "নতুন চ্যাট" && role == "user") {
            text.trim().take(32)
        } else chat.title
        list[idx] = chat.copy(title = title, messages = msgs.takeLast(200))
        save(list)
    }

    fun delete(chatId: String) {
        save(chats().filterNot { it.id == chatId })
    }

    fun clearAll() {
        prefs.edit().remove(KEY).apply()
    }

    fun search(query: String): List<Chat> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return chats()
        return chats().filter { c ->
            c.title.lowercase().contains(q) ||
                c.messages.any { it.text.lowercase().contains(q) }
        }
    }

    private fun save(list: List<Chat>) {
        val arr = JSONArray()
        list.forEach { c ->
            val msgs = JSONArray()
            c.messages.forEach { m ->
                msgs.put(
                    JSONObject()
                        .put("role", m.role)
                        .put("text", m.text)
                        .put("ts", m.ts)
                )
            }
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("title", c.title)
                    .put("created", c.created)
                    .put("messages", msgs)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "chats"
    }
}
