package com.aliuppal.pockethamster.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliuppal.pockethamster.budget.Budget
import com.aliuppal.pockethamster.budget.Expense
import com.aliuppal.pockethamster.budget.ExpenseCategory

@Composable
fun BudgetScreen(petName: String) {
    val state by Budget.state.collectAsStateWithLifecycle()
    val summary = remember(state.expenses) { Budget.summary(state.expenses) }
    var monthView by rememberSaveable { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showLimit by remember { mutableStateOf(false) }
    val from = if (monthView) Budget.monthStart() else Budget.weekStart()
    val shown = state.expenses.filter { it.time >= from }
    val byCategory = if (monthView) summary.byCategoryMonth else summary.byCategoryWeek
    val periodTotal = if (monthView) summary.month else summary.week

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Budget", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Tip: tell $petName “spent 12 on lunch” in Chat and it's logged for you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TotalCard("This week", summary.week, Modifier.weight(1f))
                    TotalCard("This month", summary.month, Modifier.weight(1f))
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Monthly budget", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { showLimit = true }) { Text(if (state.monthlyLimitCents > 0) "Edit" else "Set") }
                        }
                        if (state.monthlyLimitCents > 0) {
                            val frac = summary.month.toFloat() / state.monthlyLimitCents
                            LinearProgressIndicator(
                                progress = { frac.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(10.dp),
                                color = if (frac > 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                strokeCap = StrokeCap.Round,
                                gapSize = 0.dp,
                                drawStopIndicator = {},
                            )
                            val left = state.monthlyLimitCents - summary.month
                            Text(
                                if (left >= 0) "${Budget.format(left)} left of ${Budget.format(state.monthlyLimitCents)}"
                                else "Over by ${Budget.format(-left)}. $petName says: maybe skip the fancy seeds? 🌻",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            Text("Set a monthly limit to see how much is left.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !monthView, onClick = { monthView = false }, label = { Text("This week") })
                    FilterChip(selected = monthView, onClick = { monthView = true }, label = { Text("This month") })
                }
            }
            if (byCategory.isNotEmpty()) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("By category", style = MaterialTheme.typography.titleSmall)
                        byCategory.forEach { (cat, cents) ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row {
                                    Text("${cat.emoji} ${cat.label}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text(Budget.format(cents), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                                LinearProgressIndicator(
                                    progress = { if (periodTotal > 0) cents.toFloat() / periodTotal else 0f },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    strokeCap = StrokeCap.Round,
                                    gapSize = 0.dp,
                                    drawStopIndicator = {},
                                )
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    if (monthView) "Expenses this month" else "Expenses this week",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (shown.isEmpty()) item {
                Text(
                    "Nothing logged yet. Tap “Add expense” or tell $petName in Chat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(shown, key = { it.id }) { e ->
                ExpenseRow(e)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Add expense") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (showAdd) AddExpenseDialog { showAdd = false }
    if (showLimit) LimitDialog(state.monthlyLimitCents) { showLimit = false }
}

@Composable
private fun TotalCard(label: String, cents: Long, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                Budget.format(cents),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ExpenseRow(e: Expense) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(e.category.emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.note.ifBlank { e.category.label }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${e.category.label} · ${Budget.dateLabel(e.time)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(Budget.format(e.amountCents), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = { Budget.delete(e.id) }) { Icon(Icons.Default.Delete, contentDescription = "Delete expense") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddExpenseDialog(onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<ExpenseCategory?>(null) }
    var yesterday by remember { mutableStateOf(false) }
    val cents = Budget.parseAmount(amount)
    val chosen = category ?: ExpenseCategory.guess(note)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(12) },
                    label = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amount.isNotEmpty() && cents == null,
                )
                OutlinedTextField(note, { note = it.take(60) }, label = { Text("What for? (optional)") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExpenseCategory.entries.forEach { c ->
                        FilterChip(selected = chosen == c, onClick = { category = c }, label = { Text("${c.emoji} ${c.label}") })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !yesterday, onClick = { yesterday = false }, label = { Text("Today") })
                    FilterChip(selected = yesterday, onClick = { yesterday = true }, label = { Text("Yesterday") })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = cents != null,
                onClick = {
                    val time = System.currentTimeMillis() - if (yesterday) 24 * 60 * 60 * 1000L else 0L
                    cents?.let { Budget.add(Expense(amountCents = it, category = chosen, note = note.trim(), time = time)) }
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LimitDialog(current: Long, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) (current / 100).toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly budget") },
        text = {
            OutlinedTextField(
                text, { text = it.filter { c -> c.isDigit() || c == '.' }.take(10) },
                label = { Text("Limit per month (0 = none)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                Budget.setMonthlyLimit(Budget.parseAmount(text) ?: 0L)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
