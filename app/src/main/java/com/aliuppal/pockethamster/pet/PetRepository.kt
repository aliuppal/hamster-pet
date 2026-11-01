package com.aliuppal.pockethamster.pet

import android.content.Context
import android.content.SharedPreferences
import com.aliuppal.pockethamster.wellness.Wellness
import com.aliuppal.pockethamster.widget.PetWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Mood(val label: String) {
    HAPPY("Happy"), OK("Content"), HUNGRY("Hungry"), TIRED("Sleepy"), SAD("Lonely"), SLEEPING("Sleeping")
}

data class PetState(
    val name: String = "Peanut",
    val fullness: Float = 80f,
    val happiness: Float = 80f,
    val energy: Float = 80f,
    val sleeping: Boolean = false,
    val lastUpdate: Long = System.currentTimeMillis(),
    val lastFedAt: Long = 0L,
) {
    val mood: Mood
        get() = when {
            sleeping -> Mood.SLEEPING
            fullness < 30 -> Mood.HUNGRY
            energy < 20 -> Mood.TIRED
            happiness < 30 -> Mood.SAD
            fullness > 60 && happiness > 60 && energy > 40 -> Mood.HAPPY
            else -> Mood.OK
        }

    fun isEating(now: Long = System.currentTimeMillis()) = now - lastFedAt < 6_000
}

/** Single source of truth for the hamster. Stats decay lazily from the elapsed time on every update. */
object PetRepository {
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context
    private val _state = MutableStateFlow(PetState())
    val state: StateFlow<PetState> = _state

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("pet", Context.MODE_PRIVATE)
        _state.value = load()
        refresh()
    }

    fun refresh() = update { it }

    /** Returns false if the hamster is already stuffed. */
    fun feed(): Boolean {
        if (state.value.fullness >= 95f && !state.value.sleeping) return false
        update {
            it.copy(
                fullness = it.fullness + 25f, happiness = it.happiness + 3f,
                sleeping = false, lastFedAt = System.currentTimeMillis(),
            )
        }
        return true
    }

    /** Returns false if the hamster is too tired (or asleep) to play. */
    fun play(): Boolean {
        val s = state.value
        if (s.sleeping || s.energy < 12f) return false
        update { it.copy(happiness = it.happiness + 20f, energy = it.energy - 12f, fullness = it.fullness - 5f) }
        return true
    }

    fun sleep() = update { it.copy(sleeping = true) }
    fun wake() = update { it.copy(sleeping = false) }
    fun pet() = update { it.copy(happiness = it.happiness + 4f) }
    fun chatted() = update { it.copy(happiness = it.happiness + 2f) }
    fun rename(name: String) = update { it.copy(name = name.ifBlank { "Peanut" }) }
    fun hydrated() = update { it.copy(happiness = it.happiness + 8f) }

    /** Real-world steps: every 1,000 lifts happiness and energy a little. */
    fun walked(thousands: Int) = update {
        it.copy(happiness = it.happiness + 3f * thousands, energy = it.energy + 2f * thousands)
    }

    /** Sleep that the phone started (plugged in at night), so unplugging can undo it. */
    fun autoSleep() {
        prefs.edit().putBoolean("autoSlept", true).apply()
        sleep()
    }

    fun autoSlept() = prefs.getBoolean("autoSlept", false)

    fun autoWake() {
        prefs.edit().putBoolean("autoSlept", false).apply()
        if (state.value.sleeping) wake()
    }

    @Synchronized
    private fun update(change: (PetState) -> PetState) {
        val now = System.currentTimeMillis()
        val next = change(decay(_state.value, now)).clamped()
        _state.value = next
        save(next)
        PetWidgetProvider.refreshAll(appContext)
    }

    private fun decay(s: PetState, now: Long): PetState {
        val elapsed = ((now - s.lastUpdate).coerceAtLeast(0L)) / 3_600_000f
        if (elapsed <= 0f) return s
        // Healthy screen time and recent water slow the decay; sleep recovery isn't affected.
        val hours = if (s.sleeping) elapsed else elapsed * Wellness.decayMultiplier(now)
        return if (s.sleeping) {
            val energy = s.energy + 20f * hours
            s.copy(
                energy = energy, fullness = s.fullness - 3f * hours,
                sleeping = energy < 100f, lastUpdate = now,
            )
        } else {
            val loneliness = if (s.fullness < 30f) 8f else 4f
            s.copy(
                fullness = s.fullness - 6f * hours, energy = s.energy - 3f * hours,
                happiness = s.happiness - loneliness * hours, lastUpdate = now,
            )
        }
    }

    private fun PetState.clamped() = copy(
        fullness = fullness.coerceIn(0f, 100f),
        happiness = happiness.coerceIn(0f, 100f),
        energy = energy.coerceIn(0f, 100f),
    )

    private fun load() = PetState(
        name = prefs.getString("name", "Peanut") ?: "Peanut",
        fullness = prefs.getFloat("fullness", 80f),
        happiness = prefs.getFloat("happiness", 80f),
        energy = prefs.getFloat("energy", 80f),
        sleeping = prefs.getBoolean("sleeping", false),
        lastUpdate = prefs.getLong("lastUpdate", System.currentTimeMillis()),
        lastFedAt = prefs.getLong("lastFedAt", 0L),
    )

    private fun save(s: PetState) = prefs.edit()
        .putString("name", s.name)
        .putFloat("fullness", s.fullness)
        .putFloat("happiness", s.happiness)
        .putFloat("energy", s.energy)
        .putBoolean("sleeping", s.sleeping)
        .putLong("lastUpdate", s.lastUpdate)
        .putLong("lastFedAt", s.lastFedAt)
        .apply()
}
