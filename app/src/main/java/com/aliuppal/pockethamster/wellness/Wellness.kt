package com.aliuppal.pockethamster.wellness

import android.content.Context
import android.content.SharedPreferences
import com.aliuppal.pockethamster.companion.CompanionEngine
import com.aliuppal.pockethamster.cosmetics.Wardrobe
import com.aliuppal.pockethamster.pet.PetRepository
import com.aliuppal.pockethamster.reminders.Category
import com.aliuppal.pockethamster.reminders.Notifier
import com.aliuppal.pockethamster.reminders.ReminderGate
import com.aliuppal.pockethamster.reminders.ReminderSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.LocalTime

data class WellnessState(
    val glassesToday: Int = 0,
    val lastWaterAt: Long = 0L,
    val rewardUntil: Long = 0L,
    val stepsToday: Long = -1L, // -1 = not synced yet
    val goldenSeedToday: Boolean = false,
    val focusUntil: Long = 0L,
    val screenTimeMs: Long = -1L, // -1 = no usage access
) {
    fun focusing(now: Long = System.currentTimeMillis()) = now < focusUntil
    fun rewardActive(now: Long = System.currentTimeMillis()) = now < rewardUntil
}

/** Real-world habits that feed back into the hamster: water, steps, focus and screen time. */
object Wellness {
    const val WATER_ACORNS = 20
    const val FOCUS_ACORNS = 15
    const val GOLDEN_SEED_STEPS = 5_000L
    private const val REWARD_MS = 10 * 60 * 1000L
    private const val HYDRATED_BUFF_MS = 60 * 60 * 1000L

    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context
    private val _state = MutableStateFlow(WellnessState())
    val state: StateFlow<WellnessState> = _state

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("wellness", Context.MODE_PRIVATE)
        if (!prefs.contains("lastWaterAt")) prefs.edit().putLong("lastWaterAt", System.currentTimeMillis()).apply()
        rollover()
        _state.value = load()
    }

    /** Thirsty once the hydration interval has passed, during waking hours, unless Health is muted. */
    fun isThirsty(now: Long = System.currentTimeMillis()): Boolean {
        if (!ReminderGate.allowed(Category.HEALTH, now)) return false
        if (!ReminderSettings.isAwakeHours(LocalTime.now().hour)) return false
        return now - _state.value.lastWaterAt >= ReminderSettings.hydrationIntervalMin * 60_000L
    }

    @Synchronized
    fun logWater() {
        rollover()
        val now = System.currentTimeMillis()
        prefs.edit()
            .putInt("glasses", prefs.getInt("glasses", 0) + 1)
            .putLong("lastWaterAt", now)
            .putLong("rewardUntil", now + REWARD_MS)
            .apply()
        _state.value = load()
        Wardrobe.addAcorns(WATER_ACORNS)
        PetRepository.hydrated()
        ReminderGate.acted(Category.HEALTH)
        Notifier.cancel(appContext, Notifier.ID_HYDRATION)
        CompanionEngine.celebrate("Hydrated! 💧 +$WATER_ACORNS 🌰")
    }

    /** Credits new steps to the hamster and hands out today's golden seed at 5,000 steps. */
    @Synchronized
    fun updateSteps(steps: Long) {
        rollover()
        val creditedK = prefs.getLong("stepsK", 0L)
        val newK = steps / 1000
        if (newK > creditedK) PetRepository.walked((newK - creditedK).toInt())
        prefs.edit().putLong("steps", steps).putLong("stepsK", maxOf(creditedK, newK)).apply()
        val today = LocalDate.now().toString()
        if (steps >= GOLDEN_SEED_STEPS && prefs.getString("seedDay", "") != today) {
            prefs.edit().putString("seedDay", today).apply()
            Wardrobe.findGoldenSeed()
            val name = PetRepository.state.value.name
            val line = "You walked 5,000 steps today! $name found a rare golden seed. ✨"
            CompanionEngine.celebrate(line, toChat = true)
            if (ReminderGate.allowed(Category.HEALTH)) {
                Notifier.simple(appContext, Notifier.ID_REWARD, Category.HEALTH, "Golden seed found! ✨", "$line +50 🌰")
            }
        }
        _state.value = load()
    }

    fun startFocus(minutes: Int = 25) {
        prefs.edit().putLong("focusUntil", System.currentTimeMillis() + minutes * 60_000L).putBoolean("focusAnnounced", false).apply()
        _state.value = load()
    }

    fun stopFocus() {
        prefs.edit().putLong("focusUntil", 0L).putBoolean("focusAnnounced", true).apply()
        _state.value = load()
    }

    /** True exactly once when a focus session runs to the end; pays out acorns. */
    @Synchronized
    fun consumeFocusFinished(now: Long = System.currentTimeMillis()): Boolean {
        val until = prefs.getLong("focusUntil", 0L)
        if (until == 0L || now < until || prefs.getBoolean("focusAnnounced", true)) return false
        prefs.edit().putBoolean("focusAnnounced", true).apply()
        Wardrobe.addAcorns(FOCUS_ACORNS)
        _state.value = load()
        return true
    }

    fun setScreenTime(ms: Long) {
        if (_state.value.screenTimeMs == ms) return
        prefs.edit().putLong("screenMs", ms).apply()
        _state.value = load()
    }

    /**
     * Screen-time accountability: staying under the daily goal slows the hamster's decay, and a
     * recent glass of water slows it a little more. Never speeds it up.
     */
    fun decayMultiplier(now: Long): Float {
        val s = _state.value
        var m = 1f
        if (s.screenTimeMs >= 0 && s.screenTimeMs <= ReminderSettings.screenLimitMin * 60_000L) m *= 0.6f
        if (now - s.lastWaterAt < HYDRATED_BUFF_MS) m *= 0.8f
        return m
    }

    private fun rollover() {
        val today = LocalDate.now().toString()
        if (prefs.getString("day", "") != today) {
            prefs.edit().putString("day", today).putInt("glasses", 0).putLong("steps", -1L).putLong("stepsK", 0L).apply()
        }
    }

    private fun load() = WellnessState(
        glassesToday = prefs.getInt("glasses", 0),
        lastWaterAt = prefs.getLong("lastWaterAt", 0L),
        rewardUntil = prefs.getLong("rewardUntil", 0L),
        stepsToday = prefs.getLong("steps", -1L),
        goldenSeedToday = prefs.getString("seedDay", "") == LocalDate.now().toString(),
        focusUntil = prefs.getLong("focusUntil", 0L),
        screenTimeMs = prefs.getLong("screenMs", -1L),
    )
}
