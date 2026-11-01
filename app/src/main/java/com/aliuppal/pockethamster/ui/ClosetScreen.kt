package com.aliuppal.pockethamster.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliuppal.pockethamster.cosmetics.Fur
import com.aliuppal.pockethamster.cosmetics.Item
import com.aliuppal.pockethamster.cosmetics.Slot
import com.aliuppal.pockethamster.cosmetics.Wardrobe
import com.aliuppal.pockethamster.pet.PetState

/**
 * The shop and wardrobe. Tapping anything is a live try-on: the app, the floating hamster and
 * the widget all show it at once until you wear it, unlock it or leave the Closet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClosetScreen(pet: PetState) {
    val w by Wardrobe.state.collectAsStateWithLifecycle()
    var selectedItem by remember { mutableStateOf<Item?>(null) }
    var selectedFur by remember { mutableStateOf<Fur?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var saveOutfit by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { Wardrobe.preview(null) } }

    fun clear() {
        selectedItem = null
        selectedFur = null
        Wardrobe.preview(null)
    }

    fun tryItem(item: Item) {
        selectedItem = item
        selectedFur = null
        val base = w.equipped
        Wardrobe.preview(if (base[item.slot] == item) base.with(item.slot, null) else base.with(item.slot, item))
    }

    fun tryFur(fur: Fur) {
        selectedFur = fur
        selectedItem = null
        Wardrobe.preview(w.equipped.copy(fur = fur))
    }

    Column(Modifier.fillMaxSize()) {
        // Live preview
        Box(
            Modifier
                .fillMaxWidth()
                .height(210.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFFFFE9D2), Color(0xFFFFD9B5)))),
        ) {
            PetCanvas(pet, Modifier.size(200.dp).align(Alignment.BottomCenter), look = w.shown)
            Surface(
                Modifier.align(Alignment.TopEnd).padding(12.dp),
                shape = RoundedCornerShape(50),
                color = Color.White,
            ) {
                Text(
                    "🌰 ${w.acorns}   ✨ ${w.goldenSeeds}",
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color(0xFF3B2618),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (w.preview != null) {
                Text(
                    "Trying on… also on your home screen",
                    Modifier.align(Alignment.TopStart).padding(12.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF6B4A35),
                )
            }
        }

        // What can be done with the current selection
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val item = selectedItem
                val fur = selectedFur
                when {
                    item != null -> {
                        Text("${item.emoji} ${item.label}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        when {
                            item in w.owned -> {
                                val wearing = w.equipped[item.slot] == item
                                Button(onClick = { Wardrobe.equip(item.slot, if (wearing) null else item); clear() }) {
                                    Text(if (wearing) "Take off" else "Wear")
                                }
                            }
                            item.purchasable() -> Button(
                                enabled = w.acorns >= item.cost,
                                onClick = { Wardrobe.buy(item); clear() },
                            ) { Text("Unlock · ${item.cost} 🌰") }
                            else -> Text("Back in ${item.season?.label}", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = ::clear) { Text("Cancel") }
                    }
                    fur != null -> {
                        Text("${fur.label} fur", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        if (fur in w.ownedFurs) {
                            Button(onClick = { Wardrobe.setFur(fur); clear() }) { Text("Wear") }
                        } else {
                            Button(enabled = w.acorns >= fur.cost, onClick = { Wardrobe.buyFur(fur); clear() }) {
                                Text("Unlock · ${fur.cost} 🌰")
                            }
                        }
                        TextButton(onClick = ::clear) { Text("Cancel") }
                    }
                    else -> Text(
                        info ?: "Tap anything to try it on. Earn 🌰 by drinking water, finishing focus sessions and walking.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Outfits", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { saveOutfit = true }) { Text("Save current") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    w.outfits.forEach { o ->
                        InputChip(
                            selected = false,
                            onClick = {
                                clear()
                                val missing = Wardrobe.applyOutfit(o)
                                info = if (missing.isEmpty()) "Wearing “${o.name}”."
                                else "Wearing “${o.name}”. Unlock ${missing.joinToString { it.label }} to complete it."
                            },
                            label = { Text(o.name) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close, contentDescription = "Delete ${o.name}",
                                    modifier = Modifier.size(16.dp).clickable { Wardrobe.deleteOutfit(o) },
                                )
                            },
                        )
                    }
                }
            }
            item {
                Text("Fur", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Fur.entries.forEach { fur ->
                        FurSwatch(fur, owned = fur in w.ownedFurs, worn = w.equipped.fur == fur, selected = selectedFur == fur) { tryFur(fur) }
                    }
                }
            }
            Slot.entries.forEach { slot ->
                item {
                    Text(slot.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Item.entries.filter { it.slot == slot && it.inShop }.forEach { item ->
                            ItemTile(
                                item,
                                owned = item in w.owned,
                                worn = w.equipped[slot] == item,
                                selected = selectedItem == item,
                            ) { tryItem(item) }
                        }
                    }
                }
            }
        }
    }

    if (saveOutfit) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saveOutfit = false },
            title = { Text("Save outfit") },
            text = {
                OutlinedTextField(name, { name = it.take(24) }, label = { Text("Name, e.g. Work Mode") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = { Wardrobe.saveOutfit(name); saveOutfit = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { saveOutfit = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ItemTile(item: Item, owned: Boolean, worn: Boolean, selected: Boolean, onClick: () -> Unit) {
    val status = when {
        worn -> "Wearing"
        owned -> "Owned"
        !item.purchasable() -> "Back in ${item.season?.label ?: ""}"
        item.cost == 0 -> "Free"
        else -> "${item.cost} 🌰"
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (worn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.width(96.dp),
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(item.emoji, style = MaterialTheme.typography.headlineSmall)
            Text(item.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
            Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun FurSwatch(fur: Fur, owned: Boolean, worn: Boolean, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(72.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(fur.base))
                .border(
                    if (selected || worn) 3.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else if (worn) MaterialTheme.colorScheme.secondary else Color(0x33000000),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(Color(fur.belly)))
        }
        Text(fur.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
        Text(
            when {
                worn -> "Wearing"
                owned -> fur.rarity.label
                else -> "${fur.cost} 🌰"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
