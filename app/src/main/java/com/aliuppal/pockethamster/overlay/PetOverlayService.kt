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
import com.aliuppal.pockethamster.pet.Mood
import com.aliuppal.pockethamster.pet.PetRepository
import com.aliuppal.pockethamster.ui.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Keeps a small always-on-top window with the hamster in it. The hamster walks along the bottom of
 * the screen, can be dragged around (and falls back down when let go), hops when tapped and opens
 * the chat on double-tap.
 */
class PetOverlayService : LifecycleService() {

    companion object {
        private const val ACTION_HIDE = "com.aliuppal.pockethamster.HIDE_OVERLAY"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, PetOverlayService::class.java))

        fun stop(context: Context) {
            context.stopService(Intent(context, PetOverlayService::class.java))
        }
    }

    private enum class Mode { IDLE, WALK, DRAG, FALL }

    private lateinit var wm: WindowManager
    private lateinit var view: FloatingPetView
    private lateinit var params: WindowManager.LayoutParams
    private var attached = false

    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Float) = v * density

    private var mode = Mode.FALL
    private var px = 0f
    private var py = 0f
    private var vy = 0f
    private var targetX = 0f
    private var maxX = 0f
    private var floorY = 0f
    private var idleUntil = 0L
    private var nextChatter = 0L
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
            dp(140f).toInt(), dp(150f).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        measureScreen()
        px = maxX * 0.75f
        py = floorY * 0.4f
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
        setupTouch()
        view.post { view.say(PetLines.tap(PetRepository.state.value.mood)) }

        lifecycleScope.launch {
            PetRepository.state.collect { s -> view.mood = s.mood }
        }
        lifecycleScope.launch {
            while (isActive) {
                delay(60_000)
                PetRepository.refresh()
            }
        }
        nextChatter = System.currentTimeMillis() + 15_000
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
        mode = Mode.FALL
    }

    override fun onDestroy() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        if (attached) {
            runCatching { wm.removeView(view) }
            attached = false
        }
        super.onDestroy()
    }

    private fun step(frameTimeNanos: Long) {
        val dt = if (lastFrameNanos == 0L) 0f else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceAtMost(0.05f)
        lastFrameNanos = frameTimeNanos
        t += dt
        val now = System.currentTimeMillis()
        val state = PetRepository.state.value
        val lazy = state.mood == Mood.SLEEPING || state.mood == Mood.TIRED

        when (mode) {
            Mode.IDLE -> if (now > idleUntil && !lazy) {
                targetX = Random.nextFloat() * maxX
                mode = Mode.WALK
            }
            Mode.WALK -> {
                val speed = dp(if (state.mood == Mood.HAPPY) 48f else 34f)
                val dir = if (targetX > px) 1f else -1f
                view.facingLeft = dir < 0
                px += dir * speed * dt
                if (abs(targetX - px) <= speed * dt || lazy) {
                    mode = Mode.IDLE
                    idleUntil = now + Random.nextLong(3_000, 9_000)
                }
            }
            Mode.FALL -> {
                vy += dp(2600f) * dt
                py += vy * dt
                if (py >= floorY) {
                    py = floorY
                    vy = 0f
                    mode = Mode.IDLE
                    idleUntil = now + 1_500
                }
            }
            Mode.DRAG -> Unit
        }

        if (now > nextChatter) {
            PetLines.chatter(state.mood, state.name)?.let { view.say(it) }
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
        val y = py.toInt()
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
                PetRepository.pet()
                view.say(PetLines.tap(PetRepository.state.value.mood), 2_500)
                mode = Mode.FALL
                vy = -dp(520f) // little hop
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
                        view.say("Wheee!", 1_500)
                    }
                    if (dragging) {
                        px = (startX + dx).coerceIn(0f, maxX)
                        py = (startY + dy).coerceIn(0f, floorY)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                    dragging = false
                    mode = Mode.FALL
                    vy = 0f
                }
            }
            true
        }
    }

    private fun measureScreen() {
        val width: Int
        val height: Int
        val bottomInset: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            width = metrics.bounds.width()
            height = metrics.bounds.height()
            bottomInset = insets.bottom
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            width = dm.widthPixels
            height = dm.heightPixels
            @SuppressLint("DiscouragedApi", "InternalInsetResource")
            val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            bottomInset = if (id > 0) resources.getDimensionPixelSize(id) else 0
        }
        maxX = (width - params.width).toFloat().coerceAtLeast(0f)
        floorY = (height - bottomInset - params.height).toFloat().coerceAtLeast(0f)
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
