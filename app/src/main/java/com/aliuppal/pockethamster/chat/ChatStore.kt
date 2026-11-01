package com.aliuppal.pockethamster.chat

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(
    val id: Long = System.nanoTime(),
    val fromPet: Boolean,
    val text: String,
    val time: Long = System.currentTimeMillis(),
    val system: Boolean = false,
)

/** Keeps the last [MAX] messages in SharedPreferences as a JSON array. */
object ChatStore {
    private const val MAX = 80
    private lateinit var prefs: SharedPreferences
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    fun init(context: Context) {
        prefs = context.getSharedPreferences("chat", Context.MODE_PRIVATE)
        _messages.value = load()
    }

    @Synchronized
    fun add(message: ChatMessage) {
        _messages.value = (_messages.value + message).takeLast(MAX)
        save()
    }

    @Synchronized
    fun clear() {
        _messages.value = emptyList()
        save()
    }

    private fun load(): List<ChatMessage> = try {
        val arr = JSONArray(prefs.getString("messages", "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            ChatMessage(
                id = o.getLong("id"), fromPet = o.getBoolean("pet"), text = o.getString("text"),
                time = o.getLong("time"), system = o.optBoolean("sys"),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save() {
        val arr = JSONArray()
        _messages.value.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("pet", it.fromPet).put("text", it.text)
                    .put("time", it.time).put("sys", it.system)
            )
        }
        prefs.edit().putString("messages", arr.toString()).apply()
    }
}
