package com.aliuppal.pockethamster.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aliuppal.pockethamster.chat.ChatMessage
import com.aliuppal.pockethamster.chat.ChatViewModel
import com.aliuppal.pockethamster.pet.PetState

@Composable
fun ChatScreen(state: PetState, vm: ChatViewModel = viewModel()) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val typing by vm.typing.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, typing) { listState.animateScrollToItem(0) }

    fun send(text: String = input) {
        if (text.isBlank() || typing) return
        vm.send(text)
        input = ""
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PetCanvas(state, Modifier.size(52.dp))
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(state.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (typing) "typing…" else state.mood.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (messages.isNotEmpty()) {
                IconButton(onClick = vm::clear) { Icon(Icons.Default.Delete, contentDescription = "Clear chat") }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (typing) item(key = "typing") { Bubble("• • •", fromPet = true) }
            items(messages.asReversed(), key = { it.id }) { MessageRow(it) }
            if (messages.isEmpty() && !typing) item(key = "empty") {
                Text(
                    "Say hi to ${state.name}! Chatting cheers your hamster up, and you can offer snacks or games in words too.",
                    Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (messages.size < 2 && !typing) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(listOf("Hi ${state.name}!", "Want a sunflower seed?", "Let's play!", "Tell me a joke", "How are you?")) {
                    AssistChip(onClick = { send(it) }, label = { Text(it) })
                }
            }
        }

        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Say something to ${state.name}…") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            Spacer(Modifier.size(8.dp))
            FilledIconButton(onClick = { send() }, enabled = input.isNotBlank() && !typing) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun MessageRow(m: ChatMessage) {
    if (m.system) {
        Text(
            m.text,
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Bubble(m.text, m.fromPet)
    }
}

@Composable
private fun Bubble(text: String, fromPet: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (fromPet) Alignment.CenterStart else Alignment.CenterEnd) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp, topEnd = 20.dp,
                bottomStart = if (fromPet) 4.dp else 20.dp,
                bottomEnd = if (fromPet) 20.dp else 4.dp,
            ),
            color = if (fromPet) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
            contentColor = if (fromPet) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
