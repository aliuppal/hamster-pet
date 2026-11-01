package com.aliuppal.pockethamster.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import com.aliuppal.pockethamster.cosmetics.Look
import com.aliuppal.pockethamster.pet.Mood
import com.aliuppal.pockethamster.pet.PetRenderer

/** The hamster plus an optional speech bubble, drawn inside the small overlay window. */
class FloatingPetView(context: Context) : View(context) {
    var mood = Mood.OK
    var walking = false
    var eating = false
    var facingLeft = false
    var t = 0f
    var look = Look()

    private val density = resources.displayMetrics.density
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3B2618.toInt()
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12.5f, resources.displayMetrics)
    }
    private val bubbleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val bubbleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0xFF5A3A22.toInt()
    }
    private val rect = RectF()
    private val tail = Path()
    private var bubble: StaticLayout? = null
    private var bubbleUntil = 0L

    fun say(text: String, durationMs: Long = 3_500) {
        val maxW = (width.takeIf { it > 0 } ?: (140 * density).toInt()) - (20 * density).toInt()
        bubble = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, maxW)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(3)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        bubbleUntil = System.currentTimeMillis() + durationMs
    }

    override fun onDraw(canvas: Canvas) {
        val petSize = width * 0.62f
        val cx = width / 2f
        val cy = height - petSize * 0.55f
        canvas.save()
        // Signs have writing on them, so never mirror while holding one.
        if (facingLeft && look.sign == null) canvas.scale(-1f, 1f, cx, cy)
        PetRenderer.draw(canvas, cx, cy, petSize, mood, t, walking, eating, look)
        canvas.restore()

        val layout = bubble ?: return
        if (System.currentTimeMillis() > bubbleUntil) {
            bubble = null
            return
        }
        val pad = 7 * density
        val lineW = (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }
        val bw = lineW + pad * 2
        val bh = layout.height + pad * 2
        val left = (width - bw) / 2f
        val top = 2 * density
        rect.set(left, top, left + bw, top + bh)
        val r = 12 * density
        canvas.drawRoundRect(rect, r, r, bubbleFill)
        canvas.drawRoundRect(rect, r, r, bubbleStroke)
        tail.reset()
        tail.moveTo(cx - 6 * density, rect.bottom - 1)
        tail.lineTo(cx, rect.bottom + 8 * density)
        tail.lineTo(cx + 6 * density, rect.bottom - 1)
        canvas.drawPath(tail, bubbleFill)
        canvas.drawLine(cx - 6 * density, rect.bottom, cx, rect.bottom + 8 * density, bubbleStroke)
        canvas.drawLine(cx, rect.bottom + 8 * density, cx + 6 * density, rect.bottom, bubbleStroke)
        canvas.save()
        canvas.translate(cx - layout.width / 2f, top + pad)
        layout.draw(canvas)
        canvas.restore()
    }
}
