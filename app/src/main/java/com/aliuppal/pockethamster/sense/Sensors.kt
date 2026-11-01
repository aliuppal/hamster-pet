package com.aliuppal.pockethamster.sense

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

data class DeviceSnapshot(
    val ringerQuiet: Boolean = false,
    val brightnessMax: Boolean = false,
    val batteryPct: Int = 100,
    val charging: Boolean = false,
    val hour: Int = 12,
)

/** Cheap device-state reads, polled every few seconds by the floating hamster. */
object DeviceSignals {
    fun read(context: Context): DeviceSnapshot {
        val audio = context.getSystemService(AudioManager::class.java)
        val battery = context.getSystemService(BatteryManager::class.java)
        val brightness = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(0)
        return DeviceSnapshot(
            ringerQuiet = audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL,
            brightnessMax = brightness >= 250, // the setting runs 0–255
            batteryPct = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100,
            charging = battery?.isCharging ?: false,
            hour = LocalTime.now().hour,
        )
    }
}

/**
 * Reads app usage on the device (needs the user to grant Usage access). Nothing leaves the phone:
 * we only look at which app is in front, for how long, and today's total screen time.
 */
object UsageWatcher {
    data class Session(val pkg: String, val startedAt: Long, val social: Boolean)

    private const val RESUMED = 1 // UsageEvents.Event.ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
    private const val PAUSED = 2 // UsageEvents.Event.ACTIVITY_PAUSED / MOVE_TO_BACKGROUND

    private val KNOWN_SOCIAL = setOf(
        "com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
        "com.facebook.katana", "com.facebook.lite", "com.twitter.android", "com.snapchat.android",
        "com.reddit.frontpage", "com.pinterest", "com.tumblr", "com.linkedin.android",
        "com.instagram.barcelona", "org.joinmastodon.android", "com.bereal.ft",
    )
    private val socialCache = HashMap<String, Boolean>()

    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun settingsIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /**
     * The current foreground streak. Hopping between social apps counts as one streak, and short
     * gaps (a notification shade, a phone call) under a minute don't reset it.
     */
    fun currentSession(context: Context, now: Long = System.currentTimeMillis()): Session? {
        if (!hasAccess(context)) return null
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val events = usm.queryEvents(now - 3 * 60 * 60 * 1000L, now)
        val e = UsageEvents.Event()
        var key: String? = null
        var pkg: String? = null
        var start = 0L
        var pausedAt = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                RESUMED -> {
                    val k = if (isSocial(context, e.packageName)) "social" else e.packageName
                    val continuing = k == key && (pausedAt == 0L || e.timeStamp - pausedAt < 60_000L)
                    if (!continuing) start = e.timeStamp
                    key = k
                    pkg = e.packageName
                    pausedAt = 0L
                }
                PAUSED -> if (e.packageName == pkg) pausedAt = e.timeStamp
            }
        }
        val current = pkg ?: return null
        if (pausedAt != 0L && now - pausedAt > 60_000L) return null
        return Session(current, start, key == "social")
    }

    /** Total foreground time today, not counting the home screen or this app. */
    fun screenTimeToday(context: Context): Long {
        if (!hasAccess(context)) return -1L
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return -1L
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val launcher = context.packageManager
            .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        return usm.queryAndAggregateUsageStats(start, System.currentTimeMillis())
            .filterKeys { it != launcher && it != context.packageName }
            .values.sumOf { it.totalTimeInForeground }
    }

    fun isSocial(context: Context, pkg: String): Boolean = socialCache.getOrPut(pkg) {
        pkg in KNOWN_SOCIAL || runCatching {
            context.packageManager.getApplicationInfo(pkg, 0).category == ApplicationInfo.CATEGORY_SOCIAL
        }.getOrDefault(false)
    }
}

/**
 * Rain check via Open-Meteo (free, no key). Only an approximate location, rounded to about 10 km,
 * is stored and sent; it is captured while the app is open so no background location is needed.
 */
object Weather {
    private const val STALE_MS = 30 * 60 * 1000L
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
    }

    val enabled get() = prefs.contains("lat")
    val raining get() = enabled && prefs.getBoolean("raining", false)

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun captureLocation(context: Context) {
        if (!hasPermission(context)) return
        val lm = context.getSystemService(LocationManager::class.java) ?: return
        val loc = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstNotNullOfOrNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() } ?: return
        prefs.edit()
            .putFloat("lat", (loc.latitude * 10).roundToInt() / 10f)
            .putFloat("lon", (loc.longitude * 10).roundToInt() / 10f)
            .putLong("checkedAt", 0L)
            .apply()
    }

    fun disable() = prefs.edit().clear().apply()

    suspend fun refreshIfStale() {
        if (!enabled || System.currentTimeMillis() - prefs.getLong("checkedAt", 0L) < STALE_MS) return
        prefs.edit().putLong("checkedAt", System.currentTimeMillis()).apply()
        val lat = prefs.getFloat("lat", 0f)
        val lon = prefs.getFloat("lon", 0f)
        val raining = withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=precipitation,weather_code")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                try {
                    val current = conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }.getJSONObject("current")
                    val code = current.optInt("weather_code")
                    current.optDouble("precipitation", 0.0) > 0.05 || code in 51..67 || code in 80..82 || code in 95..99
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        } ?: return
        prefs.edit().putBoolean("raining", raining).apply()
    }
}
