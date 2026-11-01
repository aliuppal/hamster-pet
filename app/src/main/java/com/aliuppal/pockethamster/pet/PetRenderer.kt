package com.aliuppal.pockethamster.pet

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.sin

/**
 * Draws the hamster with plain Canvas calls so the same art is shared by the app (Compose),
 * the floating overlay (View) and the home screen widget (Bitmap).
 *
 * The figure spans roughly cx±0.47·size horizontally and cy−0.42·size … cy+0.51·size vertically.
 */
object PetRenderer {
    private const val FUR = 0xFFF2A65A.toInt()
    private const val CREAM = 0xFFFFF3E2.toInt()
    private const val OUTLINE = 0xFF5A3A22.toInt()
    private const val PINK = 0xFFF7A1A8.toInt()
    private const val NOSE = 0xFFE8707A.toInt()
    private const val BLUSH = 0x88FF8A95.toInt()
    private const val EYE = 0xFF2B1B12.toInt()
    private const val TEAR = 0xFF8EC9F0.toInt()
    private const val SEED = 0xFF4A4038.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = OUTLINE
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        color = OUTLINE
    }
    private val rect = RectF()
    private val path = Path()

    fun draw(
        c: Canvas, cx: Float, cy: Float, size: Float, mood: Mood, t: Float,
        walking: Boolean = false, eating: Boolean = false,
    ) {
        val s = size
        val lw = 0.026f * s
        stroke.strokeWidth = lw
        val breathe = sin(t * 2.2f) * 0.012f * s
        val step = if (walking) sin(t * 12f) else 0f
        val by = cy + 0.08f * s - abs(step) * 0.03f * s // body centre, bounces while walking

        // shadow
        fill.color = 0x30000000
        oval(c, cx, cy + 0.47f * s, 0.34f * s, 0.045f * s, fill)

        // feet (alternate while walking)
        val lift = 0.05f * s
        foot(c, cx - 0.17f * s, cy + 0.43f * s - if (step > 0) step * lift else 0f, s)
        foot(c, cx + 0.17f * s, cy + 0.43f * s - if (step < 0) -step * lift else 0f, s)

        // ears
        for (side in intArrayOf(-1, 1)) {
            val ex = cx + side * 0.27f * s
            val ey = by - 0.30f * s
            fill.color = FUR
            c.drawCircle(ex, ey, 0.12f * s, fill)
            c.drawCircle(ex, ey, 0.12f * s, stroke)
            fill.color = PINK
            c.drawCircle(ex, ey, 0.065f * s, fill)
        }

        // body
        fill.color = FUR
        oval(c, cx, by, 0.44f * s, 0.38f * s + breathe, fill)
        oval(c, cx, by, 0.44f * s, 0.38f * s + breathe, stroke)

        // stuffed cheek pouches
        if (eating) {
            for (side in intArrayOf(-1, 1)) {
                fill.color = CREAM
                c.drawCircle(cx + side * 0.33f * s, by + 0.07f * s, 0.13f * s, fill)
                c.drawCircle(cx + side * 0.33f * s, by + 0.07f * s, 0.13f * s, stroke)
            }
        }

        // cream face and belly
        fill.color = CREAM
        oval(c, cx, by + 0.06f * s, 0.32f * s, 0.30f * s + breathe, fill)

        // head tuft
        stroke.strokeWidth = lw * 0.8f
        path.reset()
        path.moveTo(cx - 0.04f * s, by - 0.37f * s)
        path.quadTo(cx - 0.03f * s, by - 0.45f * s, cx + 0.01f * s, by - 0.47f * s)
        path.moveTo(cx + 0.02f * s, by - 0.37f * s)
        path.quadTo(cx + 0.04f * s, by - 0.44f * s, cx + 0.08f * s, by - 0.44f * s)
        c.drawPath(path, stroke)
        stroke.strokeWidth = lw

        drawEyes(c, cx, by - 0.06f * s, s, mood, t)

        // blush
        fill.color = BLUSH
        oval(c, cx - 0.23f * s, by + 0.07f * s, 0.06f * s, 0.035f * s, fill)
        oval(c, cx + 0.23f * s, by + 0.07f * s, 0.06f * s, 0.035f * s, fill)

        // nose
        fill.color = NOSE
        oval(c, cx, by + 0.025f * s, 0.035f * s, 0.024f * s, fill)

        drawMouth(c, cx, by + 0.06f * s, s, mood)

        // whiskers
        stroke.strokeWidth = lw * 0.45f
        for (side in intArrayOf(-1, 1)) {
            val x0 = cx + side * 0.11f * s
            c.drawLine(x0, by + 0.04f * s, cx + side * 0.30f * s, by + 0.00f * s, stroke)
            c.drawLine(x0, by + 0.06f * s, cx + side * 0.30f * s, by + 0.09f * s, stroke)
        }
        stroke.strokeWidth = lw

        // a sunflower seed held while eating
        if (eating) {
            fill.color = SEED
            oval(c, cx, by + 0.19f * s, 0.035f * s, 0.06f * s, fill)
            fill.color = CREAM
            oval(c, cx, by + 0.19f * s, 0.008f * s, 0.04f * s, fill)
        }

        // little front paws
        for (side in intArrayOf(-1, 1)) {
            fill.color = PINK
            oval(c, cx + side * 0.075f * s, by + 0.23f * s, 0.05f * s, 0.04f * s, fill)
            oval(c, cx + side * 0.075f * s, by + 0.23f * s, 0.05f * s, 0.04f * s, stroke)
        }

        if (mood == Mood.SLEEPING) {
            val drift = (t * 0.6f) % 1f
            text.alpha = (255 * (1f - drift)).toInt()
            text.textSize = 0.13f * s
            c.drawText("z", cx + 0.34f * s, by - 0.34f * s - drift * 0.12f * s, text)
            text.textSize = 0.18f * s
            c.drawText("Z", cx + 0.46f * s, by - 0.48f * s - drift * 0.12f * s, text)
            text.alpha = 255
        }
    }

    private fun drawEyes(c: Canvas, cx: Float, ey: Float, s: Float, mood: Mood, t: Float) {
        val r = 0.06f * s
        val blink = (t % 4.3f) < 0.14f
        for (side in intArrayOf(-1, 1)) {
            val ex = cx + side * 0.15f * s
            when {
                mood == Mood.SLEEPING -> {
                    rect.set(ex - r, ey - r, ex + r, ey + r * 0.6f)
                    c.drawArc(rect, 20f, 140f, false, stroke)
                }
                blink -> c.drawLine(ex - r, ey, ex + r, ey, stroke)
                else -> {
                    fill.color = EYE
                    c.drawCircle(ex, ey, r, fill)
                    fill.color = 0xFFFFFFFF.toInt()
                    c.drawCircle(ex - 0.02f * s, ey - 0.022f * s, 0.022f * s, fill)
                    c.drawCircle(ex + 0.02f * s, ey + 0.02f * s, 0.009f * s, fill)
                    if (mood == Mood.TIRED) {
                        // heavy lids over the top half
                        c.save()
                        c.clipRect(ex - r * 1.2f, ey - r * 1.2f, ex + r * 1.2f, ey)
                        fill.color = CREAM
                        c.drawCircle(ex, ey, r * 1.1f, fill)
                        c.restore()
                        c.drawLine(ex - r, ey, ex + r, ey, stroke)
                    }
                }
            }
            if (mood == Mood.SAD) {
                // worried brows
                c.drawLine(ex - side * 0.06f * s, ey - 0.09f * s, ex + side * 0.04f * s, ey - 0.12f * s, stroke)
            }
        }
        if (mood == Mood.SAD) {
            fill.color = TEAR
            oval(c, cx + 0.19f * s, ey + 0.09f * s, 0.022f * s, 0.032f * s, fill)
        }
    }

    private fun drawMouth(c: Canvas, cx: Float, my: Float, s: Float, mood: Mood) {
        when (mood) {
            Mood.HAPPY, Mood.OK -> {
                val w = if (mood == Mood.HAPPY) 0.055f * s else 0.04f * s
                rect.set(cx - w, my - w * 0.6f, cx, my + w * 0.6f)
                c.drawArc(rect, 0f, 180f, false, stroke)
                rect.set(cx, my - w * 0.6f, cx + w, my + w * 0.6f)
                c.drawArc(rect, 0f, 180f, false, stroke)
            }
            Mood.HUNGRY -> {
                fill.color = NOSE
                oval(c, cx, my + 0.035f * s, 0.03f * s, 0.035f * s, fill)
                oval(c, cx, my + 0.035f * s, 0.03f * s, 0.035f * s, stroke)
            }
            Mood.SAD -> {
                rect.set(cx - 0.05f * s, my + 0.02f * s, cx + 0.05f * s, my + 0.09f * s)
                c.drawArc(rect, 200f, 140f, false, stroke)
            }
            Mood.TIRED -> c.drawLine(cx - 0.03f * s, my + 0.03f * s, cx + 0.03f * s, my + 0.03f * s, stroke)
            Mood.SLEEPING -> c.drawCircle(cx, my + 0.03f * s, 0.016f * s, stroke)
        }
    }

    private fun foot(c: Canvas, x: Float, y: Float, s: Float) {
        fill.color = PINK
        oval(c, x, y, 0.08f * s, 0.045f * s, fill)
        oval(c, x, y, 0.08f * s, 0.045f * s, stroke)
    }

    private fun oval(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, p: Paint) {
        rect.set(x - rx, y - ry, x + rx, y + ry)
        c.drawOval(rect, p)
    }
}
