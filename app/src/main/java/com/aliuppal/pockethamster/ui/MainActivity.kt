package com.aliuppal.pockethamster.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.aliuppal.pockethamster.AppSettings
import com.aliuppal.pockethamster.companion.CompanionEngine
import com.aliuppal.pockethamster.overlay.PetOverlayService
import com.aliuppal.pockethamster.pet.PetRepository
import com.aliuppal.pockethamster.sense.UsageWatcher
import com.aliuppal.pockethamster.sense.Weather
import com.aliuppal.pockethamster.wellness.StepsSync
import com.aliuppal.pockethamster.widget.PetWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_CHAT = "open_chat"
        private val TABS = listOf("🐹" to "Pet", "💬" to "Chat", "👗" to "Closet", "💰" to "Budget")
    }

    private var tab by mutableIntStateOf(0)
    private var overlayOn by mutableStateOf(false)
    private var usageGranted by mutableStateOf(false)
    private var stepsAvailable by mutableStateOf(false)
    private var stepsGranted by mutableStateOf(false)
    private var weatherOn by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.getBooleanExtra(EXTRA_OPEN_CHAT, false) == true) tab = 1
        setContent { HamsterTheme { App() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_CHAT, false)) tab = 1
    }

    override fun onResume() {
        super.onResume()
        PetRepository.refresh()
        syncOverlay()
        refreshPermissions()
        // Health Connect only allows reads while the app is in front, so sync steps here.
        lifecycleScope.launch {
            runCatching { StepsSync.sync(this@MainActivity) }
            stepsGranted = runCatching { StepsSync.hasPermission(this@MainActivity) }.getOrDefault(false)
            runCatching { CompanionEngine.tick(this@MainActivity, overlayActive = PetOverlayService.running) }
        }
    }

    private fun refreshPermissions() {
        usageGranted = UsageWatcher.hasAccess(this)
        stepsAvailable = StepsSync.available(this)
        if (Weather.enabled && Weather.hasPermission(this)) Weather.captureLocation(this)
        weatherOn = Weather.enabled && Weather.hasPermission(this)
    }

    /** Runs on every resume, so returning from the permission screen turns the hamster on. */
    private fun syncOverlay() {
        val allowed = Settings.canDrawOverlays(this)
        if (AppSettings.overlayEnabled && !allowed) AppSettings.overlayEnabled = false
        if (AppSettings.overlayEnabled) PetOverlayService.start(this)
        overlayOn = AppSettings.overlayEnabled
    }

    private fun setOverlay(on: Boolean) {
        AppSettings.overlayEnabled = on
        if (!on) {
            PetOverlayService.stop(this)
            overlayOn = false
        } else if (Settings.canDrawOverlays(this)) {
            PetOverlayService.start(this)
            overlayOn = true
        } else {
            Toast.makeText(this, "Allow \"Display over other apps\" for Pocket Hamster", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
    }

    private fun pinWidget() {
        val mgr = AppWidgetManager.getInstance(this)
        if (mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(ComponentName(this, PetWidgetProvider::class.java), null, null)
        } else {
            Toast.makeText(this, "Long-press your home screen → Widgets → Pocket Hamster", Toast.LENGTH_LONG).show()
        }
    }

    private fun openHealthConnectStore() {
        val uri = Uri.parse("market://details?id=com.google.android.apps.healthdata")
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            .onFailure { Toast.makeText(this, "Install Health Connect from the Play Store", Toast.LENGTH_LONG).show() }
    }

    @Composable
    private fun App() {
        val state by PetRepository.state.collectAsStateWithLifecycle()
        var showSettings by remember { mutableStateOf(false) }
        var showWelcome by remember { mutableStateOf(!AppSettings.onboarded) }
        val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        val stepsPermission = rememberLauncherForActivityResult(StepsSync.permissionContract()) {
            lifecycleScope.launch {
                runCatching { StepsSync.sync(this@MainActivity) }
                stepsGranted = runCatching { StepsSync.hasPermission(this@MainActivity) }.getOrDefault(false)
            }
        }
        val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Weather.captureLocation(this@MainActivity)
                if (!Weather.enabled) Toast.makeText(this@MainActivity, "Couldn't get a location yet; it'll retry next time.", Toast.LENGTH_LONG).show()
            }
            weatherOn = Weather.enabled && granted
        }

        fun enableOverlay(on: Boolean) {
            if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            setOverlay(on)
        }

        // Keep context reactions fresh in the app even when the floating hamster is off.
        LaunchedEffect(Unit) {
            while (true) {
                delay(15_000)
                PetRepository.refresh()
                runCatching { CompanionEngine.tick(this@MainActivity, overlayActive = PetOverlayService.running) }
            }
        }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    TABS.forEachIndexed { i, (icon, label) ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(icon) }, label = { Text(label) })
                    }
                }
            },
        ) { inner ->
            Box(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner)) {
                when (tab) {
                    0 -> PetScreen(
                        state = state,
                        overlayOn = overlayOn,
                        onToggleOverlay = ::enableOverlay,
                        onAddWidget = ::pinWidget,
                        onOpenSettings = { showSettings = true },
                    )
                    1 -> ChatScreen(state)
                    2 -> ClosetScreen(state)
                    else -> BudgetScreen(state.name)
                }
            }
        }

        if (showSettings) {
            SettingsScreen(
                pet = state,
                perms = PermissionState(
                    usageGranted = usageGranted,
                    stepsAvailable = stepsAvailable,
                    stepsGranted = stepsGranted,
                    weatherOn = weatherOn,
                    requestUsage = { startActivity(UsageWatcher.settingsIntent()) },
                    requestSteps = {
                        if (stepsAvailable) stepsPermission.launch(StepsSync.PERMISSIONS) else openHealthConnectStore()
                    },
                    setWeather = { on ->
                        if (on) {
                            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        } else {
                            Weather.disable()
                            weatherOn = false
                        }
                    },
                ),
            ) { showSettings = false }
        }

        if (showWelcome) {
            fun close() {
                AppSettings.onboarded = true
                showWelcome = false
            }
            AlertDialog(
                onDismissRequest = ::close,
                icon = { PetCanvas(state, Modifier.size(110.dp)) },
                title = { Text("Meet ${state.name}!") },
                text = {
                    Text(
                        "Your new hamster lives with you: it roams your home screen over your apps, reminds you to drink water, " +
                            "earns acorns for outfits and even keeps your budget. Android will ask you to allow " +
                            "\"Display over other apps\" so it can appear on your home screen."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { close(); enableOverlay(true) }) { Text("Put on home screen") }
                },
                dismissButton = { TextButton(onClick = ::close) { Text("Maybe later") } },
            )
        }
    }
}
