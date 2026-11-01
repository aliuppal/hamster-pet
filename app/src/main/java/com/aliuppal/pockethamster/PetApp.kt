package com.aliuppal.pockethamster

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import com.aliuppal.pockethamster.chat.ChatStore
import com.aliuppal.pockethamster.overlay.PetOverlayService
import com.aliuppal.pockethamster.pet.PetRepository

class PetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        PetRepository.init(this)
        ChatStore.init(this)
        val channel = NotificationChannel(CHANNEL_ID, "Floating hamster", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Keeps your hamster walking on the home screen"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "floating_pet"
    }
}

/** Brings the floating hamster back after a reboot or app update, if the user had it switched on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (AppSettings.overlayEnabled && Settings.canDrawOverlays(context)) {
            PetOverlayService.start(context)
        }
    }
}

object AppSettings {
    const val DEFAULT_MODEL = "google/gemma-4-31b-it:free"
    // OpenRouter accepts at most 3 ids in `models`; these are tried after the user's choice.
    val FREE_FALLBACKS = listOf("google/gemma-4-26b-a4b-it:free", "nvidia/nemotron-3.5-lightning:free")

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    var overlayEnabled: Boolean
        get() = prefs.getBoolean("overlay", false)
        set(v) = prefs.edit().putBoolean("overlay", v).apply()

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) = prefs.edit().putBoolean("onboarded", v).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(v) = prefs.edit().putString("api_key", v).apply()

    var model: String
        get() = prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = prefs.edit().putString("model", v).apply()
}
