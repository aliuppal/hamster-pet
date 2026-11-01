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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliuppal.pockethamster.AppSettings
import com.aliuppal.pockethamster.overlay.PetOverlayService
import com.aliuppal.pockethamster.pet.PetRepository
import com.aliuppal.pockethamster.pet.PetState
import com.aliuppal.pockethamster.widget.PetWidgetProvider
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_CHAT = "open_chat"
    }

    private var tab by mutableIntStateOf(0)
    private var overlayOn by mutableStateOf(false)

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

    @Composable
    private fun App() {
        val state by PetRepository.state.collectAsStateWithLifecycle()
        var showSettings by remember { mutableStateOf(false) }
        var showWelcome by remember { mutableStateOf(!AppSettings.onboarded) }
        val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

        fun enableOverlay(on: Boolean) {
            if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            setOverlay(on)
        }

        LaunchedEffect(Unit) {
            while (true) {
                delay(30_000)
                PetRepository.refresh()
            }
        }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("🐹") }, label = { Text("Pet") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("💬") }, label = { Text("Chat") })
                }
            },
        ) { inner ->
            Box(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner)) {
                if (tab == 0) {
                    PetScreen(
                        state = state,
                        overlayOn = overlayOn,
                        onToggleOverlay = ::enableOverlay,
                        onAddWidget = ::pinWidget,
                        onOpenSettings = { showSettings = true },
                    )
                } else {
                    ChatScreen(state)
                }
            }
        }

        if (showSettings) SettingsDialog(state) { showSettings = false }

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
                        "Your new hamster can live on your home screen, scampering along the bottom of the screen " +
                            "over your apps. Android will ask you to allow \"Display over other apps\" for that. " +
                            "You can also add a widget later from the Pet tab."
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

@Composable
private fun SettingsDialog(state: PetState, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(state.name) }
    var key by remember { mutableStateOf(AppSettings.apiKey) }
    var model by remember { mutableStateOf(AppSettings.model) }
    val modelOk = model.trim().endsWith(":free")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(20) }, label = { Text("Hamster name") }, singleLine = true)
                Text("Smarter chat (optional)", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Without a key, your hamster uses a built-in offline brain. Add an OpenRouter key for AI replies. " +
                        "Only free models (ids ending in :free) are allowed.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    key, { key = it.trim() },
                    label = { Text("OpenRouter API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    model, { model = it },
                    label = { Text("Model") },
                    singleLine = true,
                    isError = !modelOk,
                    supportingText = { if (!modelOk) Text("Must be a free model ending in :free") },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = modelOk && name.isNotBlank(),
                onClick = {
                    PetRepository.rename(name.trim())
                    AppSettings.apiKey = key
                    AppSettings.model = model.trim()
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
