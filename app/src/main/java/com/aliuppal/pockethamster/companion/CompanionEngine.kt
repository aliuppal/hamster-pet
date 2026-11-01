package com.aliuppal.pockethamster.companion

import android.content.Context
import android.content.SharedPreferences
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aliuppal.pockethamster.chat.ChatMessage
import com.aliuppal.pockethamster.chat.ChatStore
import com.aliuppal.pockethamster.cosmetics.Item
import com.aliuppal.pockethamster.cosmetics.Look
import com.aliuppal.pockethamster.cosmetics.Wardrobe
import com.aliuppal.pockethamster.overlay.PetOverlayService
import com.aliuppal.pockethamster.pet.Mood
import com.aliuppal.pockethamster.pet.PetRepository
import com.aliuppal.pockethamster.pet.PetState
import com.aliuppal.pockethamster.reminders.Category
import com.aliuppal.pockethamster.reminders.Notifier
import com.aliuppal.pockethamster.reminders.ReminderGate
import com.aliuppal.pockethamster.reminders.ReminderSettings
import com.aliuppal.pockethamster.sense.DeviceSignals
import com.aliuppal.pockethamster.sense.DeviceSnapshot
import com.aliuppal.pockethamster.sense.UsageWatcher
import com.aliuppal.pockethamster.sense.Weather
import com.aliuppal.pockethamster.wellness.Wellness
import com.aliuppal.pockethamster.wellness.WellnessState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * The "lives with you" brain: senses the phone, decides what the hamster wears and says, and
 * fires the few notifications that are worth it. Ticked every few seconds by the floating
 * hamster, and every 15 minutes by [CompanionWorker] when it's off.
 */
object CompanionEngine {
    data class Live(
        val device: DeviceSnapshot = DeviceSnapshot(),
        val session: UsageWatcher.Session? = null,
        val raining: Boolean = false,
        val at: Long = 0L,
    )

    private const val BREAK_AFTER_MS = 30 * 60 * 1000L
    private const val PET_CARE_GAP_MS = 6 * 60 * 60 * 1000L

    private lateinit var prefs: SharedPreferences
    private val _live = MutableStateFlow(Live())
    val live: StateFlow<Live> = _live
    private val _celebrations = MutableStateFlow(0L)
    /** Timestamp of the latest happy moment; the overlay does a hop dance when it changes. */
    val celebrations: StateFlow<Long> = _celebrations
    private val lines = ConcurrentLinkedQueue<String>()

    private var lastSessionCheck = 0L
    private var lastScreenCheck = 0L
    private var lastThirstyLine = 0L

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("companion", Context.MODE_PRIVATE)
    }

    // ------------------------------------------------------------------ speech

    fun say(line: String, toChat: Boolean = false) {
        lines += line
        if (toChat) ChatStore.add(ChatMessage(fromPet = true, text = line))
    }

    fun celebrate(line: String, toChat: Boolean = false) {
        _celebrations.value = System.currentTimeMillis()
        say(line, toChat)
    }

    fun nextLine(): String? = lines.poll()

    // ------------------------------------------------------------------ looks

    fun breakDue(live: Live, now: Long): Boolean {
        val s = live.session ?: return false
        return s.social && now - s.startedAt >= BREAK_AFTER_MS && ReminderGate.allowed(Category.PRODUCTIVITY, now)
    }

    /**
     * What the hamster wears right now. A Closet try-on wins outright; otherwise context layers
     * on top of the equipped outfit: sleep mask > focus glasses > sunglasses for the face,
     * hydration kit > water reward > break sign > umbrella for the hands.
     */
    fun look(pet: PetState, wardrobe: Wardrobe.State, wellness: WellnessState, live: Live, now: Long = System.currentTimeMillis()): Look {
        wardrobe.preview?.let { return it.copy(shiver = live.device.ringerQuiet) }
        var l = wardrobe.equipped
        l = when {
            pet.sleeping || pet.energy < 20f -> l.copy(face = Item.SLEEP_MASK)
            wellness.focusing(now) -> l.copy(face = Item.GLASSES)
            live.device.brightnessMax -> l.copy(face = Item.SUNGLASSES)
            else -> l
        }
        l = when {
            Wellness.isThirsty(now) -> l.copy(body = Item.BACKPACK, prop = Item.WATER_BOTTLE)
            wellness.rewardActive(now) -> l.copy(prop = Item.WATER_BOTTLE)
            breakDue(live, now) -> l.copy(
                prop = Item.BREAK_SIGN,
                sign = if (wellness.focusing(now)) "Back to\nfocus!" else "Let's take\na break!",
            )
            live.raining -> l.copy(prop = Item.UMBRELLA)
            else -> l
        }
        return l.copy(shiver = live.device.ringerQuiet)
    }

    fun currentLook() = look(PetRepository.state.value, Wardrobe.state.value, Wellness.state.value, _live.value)

    fun lookFlow(): Flow<Look> = combine(PetRepository.state, Wardrobe.state, Wellness.state, _live) { p, w, well, live ->
        look(p, w, well, live)
    }

    // ------------------------------------------------------------------ sensing + rules

    suspend fun tick(context: Context, overlayActive: Boolean) {
        val now = System.currentTimeMillis()
        val device = DeviceSignals.read(context)
        var session = _live.value.session
        if (now - lastSessionCheck > 30_000L || !overlayActive) {
            lastSessionCheck = now
            session = UsageWatcher.currentSession(context, now)
        }
        if (now - lastScreenCheck > 5 * 60_000L) {
            lastScreenCheck = now
            Wellness.setScreenTime(UsageWatcher.screenTimeToday(context))
        }
        Weather.refreshIfStale()
        val live = Live(device, session, Weather.raining, now)
        _live.value = live
        runRules(context, live, overlayActive, now)
    }

    private fun runRules(context: Context, live: Live, overlayActive: Boolean, now: Long) {
        val pet = PetRepository.state.value
        val d = live.device
        val today = LocalDate.now().toString()

        // Plugged in at night: the hamster curls up too, and wakes when you unplug.
        val nightCharging = d.charging && ReminderSettings.isNight(d.hour)
        if (nightCharging && !pet.sleeping) {
            PetRepository.autoSleep()
            say("Plugged in for the night... sleepy time 💤")
        } else if (!nightCharging && PetRepository.autoSlept()) {
            PetRepository.autoWake()
        }

        if (ReminderGate.allowed(Category.PET_CARE, now)) {
            if (d.hour in 8..9 && once("morning", today)) say("Good morning! ☀️")
            if (d.batteryPct in 1..15 && !d.charging && once("battery", "low")) {
                say("My energy is low, and so is your battery! 🔋")
            }
            if (!overlayActive && (pet.mood == Mood.HUNGRY || pet.mood == Mood.SAD) &&
                now - prefs.getLong("petCareAt", 0L) > PET_CARE_GAP_MS
            ) {
                prefs.edit().putLong("petCareAt", now).apply()
                val text = if (pet.mood == Mood.HUNGRY) "${pet.name}'s tummy is rumbling. Got a sunflower seed?"
                else "${pet.name} misses you. Come say hi?"
                Notifier.simple(context, Notifier.ID_PET, Category.PET_CARE, "${pet.name} needs you", text)
            }
        }
        if (d.batteryPct > 20 || d.charging) prefs.edit().remove("battery").apply()

        if (ReminderGate.allowed(Category.PRODUCTIVITY, now)) {
            if (d.hour == 22 && once("winddown", today)) say("Time to wind down 🌙")
            val s = live.session
            if (breakDue(live, now) && s != null && once("break", s.startedAt.toString())) {
                say("Let's take a break! 🌿 You've been scrolling for 30 minutes.")
                if (!overlayActive) {
                    Notifier.simple(
                        context, Notifier.ID_BREAK, Category.PRODUCTIVITY, "Let's take a break! 🌿",
                        "${pet.name} noticed 30 minutes of scrolling. Stretch, sip some water, look outside?",
                    )
                }
            }
            if (Wellness.consumeFocusFinished(now)) {
                celebrate("Great focus! 🎉 +${Wellness.FOCUS_ACORNS} 🌰 Take a little break.")
                if (!overlayActive) {
                    Notifier.simple(
                        context, Notifier.ID_FOCUS, Category.PRODUCTIVITY, "Focus session done 🎉",
                        "Nice work! ${pet.name} saved you ${Wellness.FOCUS_ACORNS} acorns.",
                    )
                }
            }
        }

        if (Wellness.isThirsty(now)) {
            val interval = ReminderSettings.hydrationIntervalMin * 60_000L
            if (now - prefs.getLong("hydrationNotifiedAt", 0L) >= interval) {
                prefs.edit().putLong("hydrationNotifiedAt", now).apply()
                Notifier.hydration(context, pet.name)
            }
            if (overlayActive && now - lastThirstyLine > 3 * 60_000L) {
                lastThirstyLine = now
                say("Time to hydrate! Tap me when you've had a sip. 💧")
            }
        }
    }

    /** True the first time it's called for this key/value pair. */
    private fun once(key: String, value: String): Boolean {
        if (prefs.getString(key, null) == value) return false
        prefs.edit().putString(key, value).apply()
        return true
    }
}

/** Keeps reminders and decay going when the floating hamster is switched off. */
class CompanionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        PetRepository.refresh()
        CompanionEngine.tick(applicationContext, overlayActive = PetOverlayService.running)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "companion", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CompanionWorker>(15, TimeUnit.MINUTES).build(),
            )
        }
    }
}
