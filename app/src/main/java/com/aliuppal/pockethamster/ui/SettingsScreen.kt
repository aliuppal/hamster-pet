package com.aliuppal.pockethamster.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliuppal.pockethamster.AppSettings
import com.aliuppal.pockethamster.budget.Budget
import com.aliuppal.pockethamster.budget.Currency
import com.aliuppal.pockethamster.pet.PetRepository
import com.aliuppal.pockethamster.pet.PetState
import com.aliuppal.pockethamster.reminders.Category
import com.aliuppal.pockethamster.reminders.ReminderGate
import com.aliuppal.pockethamster.reminders.ReminderSettings
import kotlin.math.roundToInt

/** What the settings screen needs from the activity to ask for permissions. */
class PermissionState(
    val usageGranted: Boolean,
    val stepsAvailable: Boolean,
    val stepsGranted: Boolean,
    val weatherOn: Boolean,
    val requestUsage: () -> Unit,
    val requestSteps: () -> Unit,
    val setWeather: (Boolean) -> Unit,
)

private enum class Disclosure(val title: String, val body: String) {
    USAGE(
        "Let the hamster notice your screen time?",
        "Pocket Hamster uses Android's Usage access to see which app is open and your total screen time today. " +
            "It uses this only on your phone: to hold up a “Let's take a break!” sign after 30+ minutes in social apps, " +
            "and to slow your hamster's stat decay when you stay under your daily goal. " +
            "App names and times are never uploaded, shared or sold. You can turn this off any time in Settings › Usage access.",
    ),
    STEPS(
        "Share your step count?",
        "Pocket Hamster reads today's total steps from Health Connect, read-only. Every 1,000 steps cheers your hamster up, " +
            "and 5,000 steps finds a rare golden seed. Step data stays on your phone and is never uploaded or shared.",
    ),
    WEATHER(
        "Check for rain near you?",
        "To give your hamster an umbrella when it rains, Pocket Hamster reads your approximate location (rounded to about 10 km) " +
            "while the app is open and sends only those rounded coordinates to Open-Meteo, a free weather service. " +
            "No account, no tracking, and you can switch it off any time.",
    ),
}

@Composable
fun SettingsScreen(pet: PetState, perms: PermissionState, onDismiss: () -> Unit) {
    // Read so the reminder controls recompose whenever a setting changes.
    val version by ReminderSettings.version.collectAsStateWithLifecycle()
    val budget by Budget.state.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(pet.name) }
    var apiKey by remember { mutableStateOf(AppSettings.apiKey) }
    var model by remember { mutableStateOf(AppSettings.model) }
    var disclosure by remember { mutableStateOf<Disclosure?>(null) }
    val modelOk = model.trim().endsWith(":free")

    fun save() {
        if (name.isNotBlank()) PetRepository.rename(name.trim())
        AppSettings.apiKey = apiKey
        if (modelOk) AppSettings.model = model.trim()
        onDismiss()
    }

    Dialog(onDismissRequest = ::save, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding()) {
                Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close without saving") }
                    Text("Settings", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = ::save) { Text("Save") }
                }
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Section("Your hamster")
                    OutlinedTextField(name, { name = it.take(20) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    Section("Reminder settings")
                    Text(
                        "Pick what ${pet.name} may remind you about. Swipe a reminder away three times in a row and ${pet.name} backs off that category for 4 hours.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Category.entries.forEach { c ->
                        ToggleRow(
                            c.label,
                            c.description + if (ReminderGate.backedOff(c)) " · resting for now" else "",
                            ReminderSettings.enabled(c) && version >= 0,
                        ) { ReminderSettings.setEnabled(c, it) }
                    }
                    Text("Hydration reminder every", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(60 to "1 h", 90 to "1.5 h", 120 to "2 h").forEach { (m, label) ->
                            FilterChip(
                                selected = ReminderSettings.hydrationIntervalMin == m,
                                onClick = { ReminderSettings.hydrationIntervalMin = m },
                                label = { Text(label) },
                            )
                        }
                    }
                    val limitH = ReminderSettings.screenLimitMin / 60f
                    Text("Daily screen-time goal: ${limitH.roundToInt()} h", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = limitH,
                        onValueChange = { ReminderSettings.screenLimitMin = (it.roundToInt() * 60).coerceIn(60, 600) },
                        valueRange = 1f..10f,
                        steps = 8,
                    )
                    Text("Stay under it and ${pet.name}'s stats drop 40% slower.", style = MaterialTheme.typography.bodySmall)

                    Section("Budget")
                    Text("Currency", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Currency.entries.forEach { c ->
                            FilterChip(
                                selected = budget.currency == c,
                                onClick = { Budget.setCurrency(c) },
                                label = { Text(c.label) },
                            )
                        }
                    }
                    Text(
                        "Changes how amounts are shown, e.g. ${Budget.format(125_000)}. Saved amounts aren't converted.",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Section("Privacy & permissions")
                    Text(
                        "Everything below is optional and off until you allow it. Data stays on your phone unless noted.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    PermissionRow(
                        "📱 Screen-time reactions", "Break sign after long scrolling; slower decay under your goal.",
                        perms.usageGranted, "Allow",
                    ) { disclosure = Disclosure.USAGE }
                    PermissionRow(
                        "👟 Steps (Health Connect)",
                        if (perms.stepsAvailable) "Steps cheer ${pet.name} up; 5,000 finds a golden seed." else "Install or update Health Connect to use this.",
                        perms.stepsGranted, if (perms.stepsAvailable) "Connect" else "Get it",
                    ) { if (perms.stepsAvailable) disclosure = Disclosure.STEPS else perms.requestSteps() }
                    ToggleRow("☂️ Rain umbrella", "Uses approximate location to check the weather.", perms.weatherOn) { on ->
                        if (on) disclosure = Disclosure.WEATHER else perms.setWeather(false)
                    }
                    Text(
                        "Always on: buzzes while charging in the day (stops when unplugged), sleepy eyes at night, sleeps when charging at night, sunglasses at full brightness. These only read device settings.",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Section("Smarter chat (optional)")
                    Text(
                        "Without a key, ${pet.name} uses a built-in offline brain. Add an OpenRouter key for AI replies. Only free models (ids ending in :free) are allowed. Budget answers never use AI.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        apiKey, { apiKey = it.trim() },
                        label = { Text("OpenRouter API key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        model, { model = it },
                        label = { Text("Model") },
                        singleLine = true,
                        isError = !modelOk,
                        supportingText = { if (!modelOk) Text("Must be a free model ending in :free") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.padding(bottom = 24.dp))
                }
            }
        }
    }

    disclosure?.let { d ->
        AlertDialog(
            onDismissRequest = { disclosure = null },
            title = { Text(d.title) },
            text = { Text(d.body) },
            confirmButton = {
                TextButton(onClick = {
                    disclosure = null
                    when (d) {
                        Disclosure.USAGE -> perms.requestUsage()
                        Disclosure.STEPS -> perms.requestSteps()
                        Disclosure.WEATHER -> perms.setWeather(true)
                    }
                }) { Text("Agree & continue") }
            },
            dismissButton = { TextButton(onClick = { disclosure = null }) { Text("No thanks") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PermissionRow(title: String, subtitle: String, granted: Boolean, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        if (granted) Text("On ✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        else FilledTonalButton(onClick = onClick) { Text(action) }
    }
}

/** Shown by Health Connect ("Why does this app need access?"). */
class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HamsterTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Pocket Hamster privacy", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Disclosure.entries.forEach {
                            Text(it.title, style = MaterialTheme.typography.titleMedium)
                            Text(it.body, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("Chat and budget", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Chats and expenses are stored only on your phone. If you add an OpenRouter key, your chat messages " +
                                "(never your expenses) are sent to OpenRouter to generate replies.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { finish() }) { Text("Close") }
                    }
                }
            }
        }
    }
}
