package com.aliuppal.pockethamster.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Choreographer
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.aliuppal.pockethamster.AppSettings
import com.aliuppal.pockethamster.PetApp
import com.aliuppal.pockethamster.R
import com.aliuppal.pockethamster.chat.PetLines
import com.aliuppal.pockethamster.companion.CompanionEngine
import com.aliuppal.pockethamster.pet.Mood
import com.aliuppal.pockethamster.pet.PetRepository
import com.aliuppal.pockethamster.ui.MainActivity
import com.aliuppal.pockethamster.wellness.Wellness
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Keeps a small always-on-top window with the hamster in it. The hamster roams anywhere on the
 * screen, stays wherever you drop it, hops when tapped (or logs a glass of water when it's
 * thirsty) and opens the chat on double-tap. It dresses and talks according to [CompanionEngine].
 */
class PetOverlayService : LifecycleService() {

    companion object {
        private const val ACTION_HIDE = "com.aliuppal.pockethamster.HIDE_OVERLAY"
        private const val NOTIFICATION_ID = 1

        /** Whether the floating hamster is on screen; the background worker defers to it. */
        @Volatile
        var running = false
            private set

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, PetOverlayService::class.java))

        fun stop(context: Context) {
            context.stopService(Intent(context, PetOverlayService::class.java))
        }
    }

    private enum class Mode { IDLE, WALK, DRAG }

    private lateinit var wm: WindowManager
    private lateinit var view: FloatingPetView
    private lateinit var params: WindowManager.LayoutParams
    private var attached = false

    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Float) = v * density

    private var mode = Mode.IDLE
    // Where the hamster stands; hops are drawn as an offset above it.
    private var px = 0f
    private var py = 0f
    private var hopOffset = 0f
    private var hopVelocity = 0f
    private var targetX = 0f
    private var targetY = 0f
    private var minY = 0f
    private var maxX = 0f
    private var maxY = 0f
    private var idleUntil = 0L
    private var celebrateUntil = 0L
    private var nextChatter = 0L
    private var nextSpeech = 0L
    private var lastFrameNanos = 0L
    private var t = 0f

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            step(frameTimeNanos)
            if (attached) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        view = FloatingPetView(this)
        params = WindowManager.LayoutParams(
            dp(140f).toInt(), dp(170f).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        measureScreen()
        px = maxX * 0.75f
        py = maxY
        applyPosition()
        try {
            wm.addView(view, params)
            attached = true
        } catch (e: Exception) {
            // Permission was revoked; nothing to show.
            AppSettings.overlayEnabled = false
            stopSelf()
            return
        }
        running = true
        setupTouch()
        view.post { view.say(PetLines.tap(PetRepository.state.value.mood)) }
        nextSpeech = System.currentTimeMillis() + 3_500

        lifecycleScope.launch { PetRepository.state.collect { s -> view.mood = s.mood } }
        lifecycleScope.launch { CompanionEngine.lookFlow().collect { view.look = it } }
        lifecycleScope.launch {
            var first = true
            CompanionEngine.celebrations.collect {
                if (!first) {
                    celebrateUntil = System.currentTimeMillis() + 2_500
                    hop(620f)
                }
                first = false
            }
        }
        lifecycleScope.launch {
            while (isActive) {
                runCatching { CompanionEngine.tick(this@PetOverlayService, overlayActive = true) }
                delay(3_000)
            }
        }
        lifecycleScope.launch {
            while (isActive) {
                delay(60_000)
                PetRepository.refresh()
            }
        }
        nextChatter = System.currentTimeMillis() + 20_000
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_HIDE) {
            AppSettings.overlayEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        measureScreen()
        px = px.coerceIn(0f, maxX)
        py = py.coerceIn(minY, maxY)
        mode = Mode.IDLE
    }

    override fun onDestroy() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        if (attached) {
            runCatching { wm.removeView(view) }
            attached = false
        }
        super.onDestroy()
    }

    private fun hop(strength: Float) {
        if (hopOffset == 0f) hopVelocity = -dp(strength)
    }

    private fun step(frameTimeNanos: Long) {
        val dt = if (lastFrameNanos == 0L) 0f else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceAtMost(0.05f)
        lastFrameNanos = frameTimeNanos
        t += dt
        val now = System.currentTimeMillis()
        val state = PetRepository.state.value
        val lazy = state.mood == Mood.SLEEPING || state.mood == Mood.TIRED

        when (mode) {
            Mode.IDLE -> if (now > idleUntil && !lazy && now > celebrateUntil) {
                // Wander anywhere on screen, but mostly short trips so it doesn't zoom around.
                targetX = (px + Random.nextFloat() * dp(360f) - dp(180f)).coerceIn(0f, maxX)
                targetY = (py + Random.nextFloat() * dp(300f) - dp(150f)).coerceIn(minY, maxY)
                mode = Mode.WALK
            }
            Mode.WALK -> {
                val speed = dp(if (state.mood == Mood.HAPPY) 48f else 34f)
                val dx = targetX - px
                val dy = targetY - py
                val dist = hypot(dx, dy)
                if (dist <= speed * dt || lazy) {
                    mode = Mode.IDLE
                    idleUntil = now + Random.nextLong(3_000, 9_000)
                } else {
                    px += dx / dist * speed * dt
                    py += dy / dist * speed * dt
                    if (kotlin.math.abs(dx) > 1f) view.facingLeft = dx < 0
                }
            }
            Mode.DRAG -> Unit
        }

        // Hops are a vertical bounce above the spot the hamster is standing on.
        if (hopOffset < 0f || hopVelocity != 0f) {
            hopVelocity += dp(2600f) * dt
            hopOffset += hopVelocity * dt
            if (hopOffset >= 0f) {
                hopOffset = 0f
                hopVelocity = 0f
                if (now < celebrateUntil) hop(560f)
            }
        }

        if (now > nextSpeech) {
            CompanionEngine.nextLine()?.let {
                view.say(it, 4_000)
                nextSpeech = now + 4_500
            }
        }
        if (now > nextChatter) {
            PetLines.chatter(state.mood, state.name)?.let { if (now > nextSpeech) view.say(it) }
            nextChatter = now + Random.nextLong(25_000, 50_000)
        }

        view.t = t
        view.walking = mode == Mode.WALK
        view.eating = state.isEating(now)
        applyPosition()
        view.invalidate()
    }

    private fun applyPosition() {
        val x = px.toInt()
        val y = (py + hopOffset).coerceAtLeast(0f).toInt()
        if (params.x != x || params.y != y) {
            params.x = x
            params.y = y
            if (attached) runCatching { wm.updateViewLayout(view, params) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouch() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (Wellness.isThirsty()) {
                    // Closes the hydration loop: hop dance, +20 acorns, bottle kept as a reward.
                    Wellness.logWater()
                } else {
                    PetRepository.pet()
                    view.say(PetLines.tap(PetRepository.state.value.mood), 2_500)
                    hop(520f)
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                startActivity(
                    Intent(this@PetOverlayService, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_OPEN_CHAT, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                return true
            }
        }).apply { setIsLongpressEnabled(false) }

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false

        view.setOnTouchListener { _, e ->
            detector.onTouchEvent(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = px; startY = py
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && hypot(dx, dy) > slop) {
                        dragging = true
                        mode = Mode.DRAG
                        hopOffset = 0f
                        hopVelocity = 0f
                        view.say("Wheee!", 1_500)
                    }
                    if (dragging) {
                        px = (startX + dx).coerceIn(0f, maxX)
                        py = (startY + dy).coerceIn(minY, maxY)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                    // Stays where it was dropped, then carries on exploring from there.
                    dragging = false
                    mode = Mode.IDLE
                    idleUntil = System.currentTimeMillis() + 4_000
                }
            }
            true
        }
    }

    private fun measureScreen() {
        val width: Int
        val height: Int
        val topInset: Int
        val bottomInset: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            width = metrics.bounds.width()
            height = metrics.bounds.height()
            topInset = insets.top
            bottomInset = insets.bottom
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            width = dm.widthPixels
            height = dm.heightPixels
            @SuppressLint("DiscouragedApi", "InternalInsetResource")
            val nav = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            @SuppressLint("DiscouragedApi", "InternalInsetResource")
            val status = resources.getIdentifier("status_bar_height", "dimen", "android")
            bottomInset = if (nav > 0) resources.getDimensionPixelSize(nav) else 0
            topInset = if (status > 0) resources.getDimensionPixelSize(status) else 0
        }
        maxX = (width - params.width).toFloat().coerceAtLeast(0f)
        minY = topInset.toFloat()
        maxY = (height - bottomInset - params.height).toFloat().coerceAtLeast(minY)
    }

    private fun startInForeground() {
        val name = PetRepository.state.value.name
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val hide = PendingIntent.getService(
            this, 1, Intent(this, PetOverlayService::class.java).setAction(ACTION_HIDE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, PetApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pet)
            .setContentTitle("$name is on your home screen")
            .setContentText("Tap $name to pet, double-tap to chat, drag to move.")
            .setContentIntent(open)
            .addAction(0, "Hide", hide)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }
}
