package com.aliuppal.pockethamster.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliuppal.pockethamster.chat.PetAction
import com.aliuppal.pockethamster.chat.PetBrain
import com.aliuppal.pockethamster.chat.PetLines
import com.aliuppal.pockethamster.companion.CompanionEngine
import com.aliuppal.pockethamster.cosmetics.Look
import com.aliuppal.pockethamster.cosmetics.Wardrobe
import com.aliuppal.pockethamster.pet.PetRenderer
import com.aliuppal.pockethamster.pet.PetRepository
import com.aliuppal.pockethamster.pet.PetState
import com.aliuppal.pockethamster.reminders.ReminderSettings
import com.aliuppal.pockethamster.wellness.Wellness
import com.aliuppal.pockethamster.wellness.WellnessState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/** The animated hamster, drawn with the same renderer as the overlay and widget. */
@Composable
fun PetCanvas(state: PetState, modifier: Modifier = Modifier, look: Look = Look(), hop: () -> Float = { 0f }) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - start) / 1e9f }
    }
    Canvas(modifier.semantics { contentDescription = "${state.name} the hamster, ${state.mood.label}" }) {
        val l = min(size.width, size.height)
        drawIntoCanvas {
            // Framed a little low and small so a held umbrella or sign still fits.
            PetRenderer.draw(
                it.nativeCanvas, size.width / 2f, size.height * 0.56f - hop() * 0.15f * l, l * 0.72f,
                state.mood, t, eating = state.isEating(), look = look,
            )
        }
    }
}

@Composable
fun PetScreen(
    state: PetState,
    overlayOn: Boolean,
    onToggleOverlay: (Boolean) -> Unit,
    onAddWidget: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val look by remember { CompanionEngine.lookFlow() }.collectAsStateWithLifecycle(CompanionEngine.currentLook())
    val wellness by Wellness.state.collectAsStateWithLifecycle()
    val wardrobe by Wardrobe.state.collectAsStateWithLifecycle()
    val celebration by CompanionEngine.celebrations.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val hop = remember { Animatable(0f) }
    var bubble by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(bubble) {
        if (bubble != null) {
            delay(3_000)
            bubble = null
        }
    }
    fun bounce() = scope.launch {
        hop.animateTo(1f, tween(130))
        hop.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(celebration) {
        if (celebration == 0L) return@LaunchedEffect
        repeat(3) {
            hop.animateTo(1f, tween(140))
            hop.animateTo(0f, tween(160))
        }
    }
    fun act(action: PetAction) {
        bubble = PetLines.forOutcome(PetBrain.perform(action))
        bounce()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Feeling ${state.mood.label.lowercase()} · 🌰 ${wardrobe.acorns}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
        }

        // Habitat
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFFFE9D2), Color(0xFFFFD9B5))))
                .clickable(remember { MutableInteractionSource() }, indication = null) {
                    if (Wellness.isThirsty()) {
                        Wellness.logWater()
                        bubble = "Hydrated! 💧 +${Wellness.WATER_ACORNS} 🌰"
                    } else {
                        PetRepository.pet()
                        bubble = PetLines.tap(state.mood)
                        bounce()
                    }
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(Color(0xFFF3C892)),
            )
            PetCanvas(
                state,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(250.dp),
                look = look,
                hop = { hop.value },
            )
            (bubble ?: if (Wellness.isThirsty()) "Time to hydrate! Tap me when you've had a sip. 💧" else null)?.let {
                Surface(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp, start = 24.dp, end = 24.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 2.dp,
                ) {
                    Text(
                        it,
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = Color(0xFF3B2618),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatRow("Food", state.fullness)
                StatRow("Happiness", state.happiness)
                StatRow("Energy", state.energy)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { act(PetAction.FEED) }, Modifier.weight(1f)) { Text("🌻 Feed") }
            FilledTonalButton(onClick = { act(PetAction.PLAY) }, Modifier.weight(1f)) { Text("🎡 Play") }
            FilledTonalButton(
                onClick = { act(if (state.sleeping) PetAction.WAKE else PetAction.SLEEP) },
                Modifier.weight(1f),
            ) { Text(if (state.sleeping) "☀️ Wake" else "🌙 Sleep") }
        }

        TodayCard(wellness, wardrobe.goldenSeeds, onOpenSettings)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("On your home screen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Floating hamster", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${state.name} roams your screen. Tap to pet, double-tap to chat, drag to move: it stays where you drop it.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = overlayOn, onCheckedChange = onToggleOverlay)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Home screen widget", style = MaterialTheme.typography.bodyLarge)
                        Text("Stats with Feed, Play and Chat buttons.", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.width(12.dp))
                    FilledTonalButton(onClick = onAddWidget) { Text("Add") }
                }
            }
        }
    }
}

@Composable
private fun TodayCard(w: WellnessState, goldenSeeds: Int, onOpenSettings: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            TodayRow("💧", "Water", "${w.glassesToday} glass${if (w.glassesToday == 1) "" else "es"} · +${Wellness.WATER_ACORNS} 🌰 each") {
                FilledTonalButton(onClick = { Wellness.logWater() }) { Text("I drank water") }
            }

            if (w.stepsToday >= 0) {
                TodayRow(
                    "👟", "Steps",
                    "%,d / %,d".format(w.stepsToday, Wellness.GOLDEN_SEED_STEPS) + if (w.goldenSeedToday) " · golden seed found ✨" else "",
                ) {}
                LinearProgressIndicator(
                    progress = { (w.stepsToday.toFloat() / Wellness.GOLDEN_SEED_STEPS).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            } else {
                TodayRow("👟", "Steps", "Connect Health Connect for golden seeds") {
                    OutlinedButton(onClick = onOpenSettings) { Text("Connect") }
                }
            }

            if (w.screenTimeMs >= 0) {
                val limit = ReminderSettings.screenLimitMin * 60_000L
                val under = w.screenTimeMs <= limit
                TodayRow(
                    "📱", "Screen time",
                    "${hm(w.screenTimeMs)} of ${hm(limit)} goal" + if (under) " · slower decay 🐢" else "",
                ) {}
            } else {
                TodayRow("📱", "Screen time", "Allow usage access for break nudges") {
                    OutlinedButton(onClick = onOpenSettings) { Text("Allow") }
                }
            }

            if (w.focusing(now)) {
                val left = ((w.focusUntil - now) / 60_000L).coerceAtLeast(1)
                TodayRow("🎯", "Focus", "$left min left · glasses on 👓") {
                    OutlinedButton(onClick = { Wellness.stopFocus() }) { Text("Stop") }
                }
            } else {
                TodayRow("🎯", "Focus", "25 minutes, +${Wellness.FOCUS_ACORNS} 🌰 when done") {
                    FilledTonalButton(onClick = { Wellness.startFocus() }) { Text("Start") }
                }
            }
            if (goldenSeeds > 0) Text("✨ $goldenSeeds golden seed${if (goldenSeeds == 1) "" else "s"} collected", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TodayRow(emoji: String, title: String, subtitle: String, action: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action()
    }
}

private fun hm(ms: Long): String {
    val m = ms / 60_000L
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

@Composable
private fun StatRow(label: String, value: Float) {
    val color = when {
        value < 30f -> Color(0xFFD9534F)
        value < 60f -> Color(0xFFE8A33D)
        else -> Color(0xFF5BAE6A)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text("${value.roundToInt()}%", style = MaterialTheme.typography.labelLarge)
        }
        LinearProgressIndicator(
            progress = { value / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}
