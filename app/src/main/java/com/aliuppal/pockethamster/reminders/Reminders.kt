package com.aliuppal.pockethamster.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aliuppal.pockethamster.R
import com.aliuppal.pockethamster.companion.CompanionEngine
import com.aliuppal.pockethamster.ui.MainActivity
import com.aliuppal.pockethamster.wellness.Wellness
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Category(val label: String, val description: String) {
    HEALTH("Health", "Hydration reminders and step rewards"),
    PRODUCTIVITY("Productivity", "Break nudges after long scrolling, focus mode and the 10 PM wind-down"),
    PET_CARE("Pet care", "Hunger alerts, the morning greeting and battery quips"),
}

/** What the user has switched on. Every reminder goes through [ReminderGate] before it fires. */
object ReminderSettings {
    const val SLEEP_START = 22
    const val SLEEP_END = 7
    private const val AWAKE_START = 8

    private lateinit var prefs: SharedPreferences
    private val _version = MutableStateFlow(0)
    /** Bumped on every change so the UI can recompose. */
    val version: StateFlow<Int> = _version

    fun init(context: Context) {
        prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    }

    fun enabled(c: Category) = prefs.getBoolean("on_${c.name}", true)
    fun setEnabled(c: Category, on: Boolean) = edit { putBoolean("on_${c.name}", on) }

    var hydrationIntervalMin: Int
        get() = prefs.getInt("hydration_min", 90)
        set(v) = edit { putInt("hydration_min", v) }

    /** Daily screen-time goal. Staying under it slows the hamster's stat decay. */
    var screenLimitMin: Int
        get() = prefs.getInt("limit_min", 240)
        set(v) = edit { putInt("limit_min", v) }

    fun isNight(hour: Int) = hour >= SLEEP_START || hour < SLEEP_END
    fun isAwakeHours(hour: Int) = hour in AWAKE_START until SLEEP_START

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _version.value++
    }
}

/**
 * Smart frequency: three dismissals in a row of a category's notification makes the hamster
 * back off that category for four hours.
 */
object ReminderGate {
    private const val BACKOFF_MS = 4 * 60 * 60 * 1000L
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("reminder_gate", Context.MODE_PRIVATE)
    }

    fun allowed(c: Category, now: Long = System.currentTimeMillis()) =
        ReminderSettings.enabled(c) && now >= prefs.getLong("backoff_${c.name}", 0L)

    fun backedOff(c: Category, now: Long = System.currentTimeMillis()) = now < prefs.getLong("backoff_${c.name}", 0L)

    fun dismissed(c: Category) {
        val count = prefs.getInt("dismiss_${c.name}", 0) + 1
        if (count >= 3) {
            prefs.edit()
                .putInt("dismiss_${c.name}", 0)
                .putLong("backoff_${c.name}", System.currentTimeMillis() + BACKOFF_MS)
                .apply()
            CompanionEngine.say("Okay, I'll let you be for a while!", toChat = true)
        } else {
            prefs.edit().putInt("dismiss_${c.name}", count).apply()
        }
    }

    fun acted(c: Category) = prefs.edit().putInt("dismiss_${c.name}", 0).apply()
}

object Notifier {
    private const val CHANNEL = "reminders"
    const val ID_HYDRATION = 10
    const val ID_BREAK = 11
    const val ID_PET = 12
    const val ID_REWARD = 13
    const val ID_FOCUS = 14

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Hamster reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Hydration, breaks and pet care. Choose categories in the app's Reminder settings."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun hydration(context: Context, petName: String) {
        val drank = PendingIntent.getBroadcast(
            context, 1, Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_DRANK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        post(
            context, ID_HYDRATION, Category.HEALTH, "Time to hydrate! 💧",
            "$petName is thirsty too. Tap “I drank water” when you've had a sip.",
        ) { addAction(0, "I drank water", drank) }
    }

    fun simple(context: Context, id: Int, category: Category, title: String, text: String) =
        post(context, id, category, title, text) {}

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    private fun post(
        context: Context, id: Int, category: Category, title: String, text: String,
        extra: NotificationCompat.Builder.() -> Unit,
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, 200 + id, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Swiping the notification away counts as a dismissal for smart frequency.
        val dismissed = PendingIntent.getBroadcast(
            context, 300 + id,
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_DISMISSED)
                .putExtra(ReminderReceiver.EXTRA_CATEGORY, category.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pet)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setDeleteIntent(dismissed)
            .setAutoCancel(true)
            .apply(extra)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DRANK = "com.aliuppal.pockethamster.DRANK_WATER"
        const val ACTION_DISMISSED = "com.aliuppal.pockethamster.REMINDER_DISMISSED"
        const val EXTRA_CATEGORY = "category"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DRANK -> Wellness.logWater()
            ACTION_DISMISSED -> intent.getStringExtra(EXTRA_CATEGORY)
                ?.let { runCatching { Category.valueOf(it) }.getOrNull() }
                ?.let(ReminderGate::dismissed)
        }
    }
}
