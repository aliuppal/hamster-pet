package com.aliuppal.pockethamster.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class ChatViewModel : ViewModel() {
    val messages: StateFlow<List<ChatMessage>> = ChatStore.messages
    private val _typing = MutableStateFlow(false)
    val typing: StateFlow<Boolean> = _typing
    private var warnedOffline = false

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || _typing.value) return
        ChatStore.add(ChatMessage(fromPet = false, text = clean))
        _typing.value = true
        viewModelScope.launch {
            val reply = PetBrain.respond(clean, ChatStore.messages.value)
            // The offline brain is instant; a short pause makes it feel like the hamster is thinking.
            if (!reply.usedAi) delay(500L + Random.nextLong(700))
            if (reply.aiError != null && !warnedOffline) {
                warnedOffline = true
                ChatStore.add(ChatMessage(fromPet = true, system = true, text = "AI brain unavailable (${reply.aiError}), using the offline brain."))
            }
            ChatStore.add(ChatMessage(fromPet = true, text = reply.text))
            _typing.value = false
        }
    }

    fun clear() = ChatStore.clear()
}
