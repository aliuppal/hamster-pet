package com.aliuppal.pockethamster.pet

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.aliuppal.pockethamster.cosmetics.Fur
import com.aliuppal.pockethamster.cosmetics.Item
import com.aliuppal.pockethamster.cosmetics.Look
import kotlin.math.abs
import kotlin.math.sin

/**
 * Draws the hamster with plain Canvas calls so the same art is shared by the app (Compose),
 * the floating overlay (View) and the home screen widget (Bitmap).
 *
 * Layers, back to front: cape and backpack → fur → face → face accessory → body accessory →
 * hand prop → paws → head accessory. The bare hamster spans roughly cx±0.47·size and
 * cy−0.42·size … cy+0.51·size; an umbrella or sign reaches up to cy−0.72·size.
 */
object PetRenderer {
    private const val OUTLINE = 0xFF5A3A22.toInt()
    private const val PINK = 0xFFF7A1A8.toInt()
    private const val NOSE = 0xFFE8707A.toInt()
    private const val BLUSH = 0x88FF8A95.toInt()
    private const val EYE = 0xFF2B1B12.toInt()
    private const val TEAR = 0xFF8EC9F0.toInt()
    private const val SEED = 0xFF4A4038.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

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
        c: Canvas, cx0: Float, cy: Float, size: Float, petMood: Mood, t: Float,
        walking: Boolean = false, eating: Boolean = false, look: Look = Look(),
    ) {
        val mood = if (look.sleepy && petMood != Mood.SLEEPING) Mood.TIRED else petMood
        val s = size
        val lw = 0.026f * s
        stroke.strokeWidth = lw
        stroke.color = OUTLINE
        val cx = if (look.shiver) cx0 + sin(t * 70f) * 0.012f * s else cx0
        val breathe = sin(t * 2.2f) * 0.012f * s
        val step = if (walking) sin(t * 12f) else 0f
        val by = cy + 0.08f * s - abs(step) * 0.03f * s // body centre, bounces while walking
        val furColor = look.fur.base
        val belly = look.fur.belly
        val prop = if (eating && look.prop == null) Item.SEED else look.prop

        // shadow
        fill.color = 0x30000000
        oval(c, cx, cy + 0.47f * s, 0.34f * s, 0.045f * s, fill)

        // things worn behind the body
        if (look.body == Item.CAPE) drawCapeBack(c, cx, by, s, t)
        if (look.body == Item.BACKPACK) drawBackpackBack(c, cx, by, s)

        // feet (alternate while walking)
        val lift = 0.05f * s
        foot(c, cx - 0.17f * s, cy + 0.43f * s - if (step > 0) step * lift else 0f, s)
        foot(c, cx + 0.17f * s, cy + 0.43f * s - if (step < 0) -step * lift else 0f, s)

        // ears
        for (side in intArrayOf(-1, 1)) {
            val ex = cx + side * 0.27f * s
            val ey = by - 0.30f * s
            fill.color = furColor
            c.drawCircle(ex, ey, 0.12f * s, fill)
            c.drawCircle(ex, ey, 0.12f * s, stroke)
            fill.color = PINK
            c.drawCircle(ex, ey, 0.065f * s, fill)
        }

        // body
        val ry = 0.38f * s + breathe
        fill.color = furColor
        oval(c, cx, by, 0.44f * s, ry, fill)
        drawFurPattern(c, cx, by, s, ry, look.fur, t)
        oval(c, cx, by, 0.44f * s, ry, stroke)

        // stuffed cheek pouches
        if (eating) {
            for (side in intArrayOf(-1, 1)) {
                fill.color = belly
                c.drawCircle(cx + side * 0.33f * s, by + 0.07f * s, 0.13f * s, fill)
                c.drawCircle(cx + side * 0.33f * s, by + 0.07f * s, 0.13f * s, stroke)
            }
        }

        // face and belly
        fill.color = belly
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

        drawEyes(c, cx, by - 0.06f * s, s, mood, t, belly)

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

        look.face?.let { drawFaceItem(c, it, cx, by - 0.06f * s, s) }
        look.body?.let { drawBodyItem(c, it, cx, by, s) }
        prop?.let { drawProp(c, it, cx, by, s, t, look.sign) }

        // little front paws
        for (side in intArrayOf(-1, 1)) {
            fill.color = PINK
            oval(c, cx + side * 0.075f * s, by + 0.23f * s, 0.05f * s, 0.04f * s, fill)
            oval(c, cx + side * 0.075f * s, by + 0.23f * s, 0.05f * s, 0.04f * s, stroke)
        }

        look.head?.let { drawHeadItem(c, it, cx, by, s) }

        if (mood == Mood.SLEEPING) {
            val drift = (t * 0.6f) % 1f
            text.color = OUTLINE
            text.alpha = (255 * (1f - drift)).toInt()
            text.textSize = 0.13f * s
            c.drawText("z", cx - 0.36f * s, by - 0.34f * s - drift * 0.12f * s, text)
            text.textSize = 0.18f * s
            c.drawText("Z", cx - 0.48f * s, by - 0.48f * s - drift * 0.12f * s, text)
            text.alpha = 255
        }
    }

    // ------------------------------------------------------------------ face

    private fun drawEyes(c: Canvas, cx: Float, ey: Float, s: Float, mood: Mood, t: Float, lid: Int) {
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
                    fill.color = WHITE
                    c.drawCircle(ex - 0.02f * s, ey - 0.022f * s, 0.022f * s, fill)
                    c.drawCircle(ex + 0.02f * s, ey + 0.02f * s, 0.009f * s, fill)
                    if (mood == Mood.TIRED) {
                        // heavy lids over the top half
                        c.save()
                        c.clipRect(ex - r * 1.2f, ey - r * 1.2f, ex + r * 1.2f, ey)
                        fill.color = lid
                        c.drawCircle(ex, ey, r * 1.1f, fill)
                        c.restore()
                        c.drawLine(ex - r, ey, ex + r, ey, stroke)
                    }
                }
            }
            if (mood == Mood.SAD) {
                // worried brows: inner end raised
                c.drawLine(ex - side * 0.06f * s, ey - 0.13f * s, ex + side * 0.05f * s, ey - 0.09f * s, stroke)
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

    private fun drawFurPattern(c: Canvas, cx: Float, by: Float, s: Float, ry: Float, fur: Fur, t: Float) {
        if (fur != Fur.CALICO && fur != Fur.GALAXY_BLUE) return
        c.save()
        path.reset()
        rect.set(cx - 0.44f * s, by - ry, cx + 0.44f * s, by + ry)
        path.addOval(rect, Path.Direction.CW)
        c.clipPath(path)
        if (fur == Fur.CALICO) {
            fill.color = 0xFF5E4130.toInt()
            oval(c, cx - 0.32f * s, by - 0.18f * s, 0.17f * s, 0.13f * s, fill)
            oval(c, cx + 0.34f * s, by + 0.20f * s, 0.14f * s, 0.12f * s, fill)
            fill.color = 0xFFFFFBF5.toInt()
            oval(c, cx + 0.26f * s, by - 0.26f * s, 0.11f * s, 0.08f * s, fill)
        } else {
            val stars = floatArrayOf(-0.34f, -0.2f, 0.36f, -0.12f, -0.38f, 0.16f, 0.33f, 0.24f, 0.16f, -0.31f, -0.18f, -0.30f)
            for (i in stars.indices step 2) {
                fill.color = WHITE
                fill.alpha = (150 + 100 * sin(t * 3f + i)).toInt().coerceIn(40, 255)
                c.drawCircle(cx + stars[i] * s, by + stars[i + 1] * s, 0.014f * s, fill)
            }
            fill.alpha = 255
        }
        c.restore()
    }

    private fun drawFaceItem(c: Canvas, item: Item, cx: Float, ey: Float, s: Float) {
        val r = 0.085f * s
        when (item) {
            Item.GLASSES -> {
                fill.color = 0x40FFFFFF
                stroke.color = 0xFF3A2A20.toInt()
                for (side in intArrayOf(-1, 1)) {
                    c.drawCircle(cx + side * 0.15f * s, ey, r, fill)
                    c.drawCircle(cx + side * 0.15f * s, ey, r, stroke)
                }
                c.drawLine(cx - 0.065f * s, ey - 0.01f * s, cx + 0.065f * s, ey - 0.01f * s, stroke)
                stroke.color = OUTLINE
            }
            Item.SUNGLASSES -> {
                for (side in intArrayOf(-1, 1)) {
                    val x = cx + side * 0.15f * s
                    rect.set(x - 0.1f * s, ey - 0.065f * s, x + 0.1f * s, ey + 0.07f * s)
                    fill.color = 0xFF1E1E26.toInt()
                    c.drawRoundRect(rect, 0.05f * s, 0.05f * s, fill)
                    fill.color = 0x66FFFFFF
                    c.drawCircle(x - 0.04f * s, ey - 0.025f * s, 0.018f * s, fill)
                }
                c.drawLine(cx - 0.05f * s, ey - 0.03f * s, cx + 0.05f * s, ey - 0.03f * s, stroke)
            }
            Item.SLEEP_MASK -> {
                fill.color = 0xFF9B8AD8.toInt()
                rect.set(cx - 0.29f * s, ey - 0.075f * s, cx + 0.29f * s, ey + 0.075f * s)
                c.drawRoundRect(rect, 0.07f * s, 0.07f * s, fill)
                c.drawRoundRect(rect, 0.07f * s, 0.07f * s, stroke)
                stroke.color = WHITE
                for (side in intArrayOf(-1, 1)) {
                    val x = cx + side * 0.14f * s
                    rect.set(x - 0.05f * s, ey - 0.05f * s, x + 0.05f * s, ey + 0.02f * s)
                    c.drawArc(rect, 20f, 140f, false, stroke)
                }
                stroke.color = OUTLINE
            }
            else -> Unit
        }
    }

    // ------------------------------------------------------------------ body

    private fun drawCapeBack(c: Canvas, cx: Float, by: Float, s: Float, t: Float) {
        val wave = sin(t * 4f) * 0.02f * s
        path.reset()
        path.moveTo(cx - 0.26f * s, by - 0.12f * s)
        path.lineTo(cx + 0.26f * s, by - 0.12f * s)
        path.lineTo(cx + 0.52f * s + wave, by + 0.42f * s)
        path.quadTo(cx, by + 0.52f * s - wave, cx - 0.52f * s - wave, by + 0.42f * s)
        path.close()
        fill.color = 0xFFD9443B.toInt()
        c.drawPath(path, fill)
        c.drawPath(path, stroke)
    }

    private fun drawBackpackBack(c: Canvas, cx: Float, by: Float, s: Float) {
        fill.color = 0xFF4E8FD8.toInt()
        rect.set(cx + 0.30f * s, by - 0.14f * s, cx + 0.52f * s, by + 0.24f * s)
        c.drawRoundRect(rect, 0.06f * s, 0.06f * s, fill)
        c.drawRoundRect(rect, 0.06f * s, 0.06f * s, stroke)
        fill.color = 0xFF3A70B5.toInt()
        rect.set(cx + 0.33f * s, by - 0.13f * s, cx + 0.51f * s, by - 0.02f * s)
        c.drawRoundRect(rect, 0.05f * s, 0.05f * s, fill)
    }

    private fun drawBodyItem(c: Canvas, item: Item, cx: Float, by: Float, s: Float) {
        when (item) {
            Item.SCARF -> {
                fill.color = 0xFF2F8F5B.toInt()
                rect.set(cx - 0.34f * s, by + 0.12f * s, cx + 0.34f * s, by + 0.21f * s)
                c.drawRoundRect(rect, 0.045f * s, 0.045f * s, fill)
                rect.set(cx + 0.12f * s, by + 0.15f * s, cx + 0.21f * s, by + 0.36f * s)
                c.drawRoundRect(rect, 0.03f * s, 0.03f * s, fill)
                c.drawRoundRect(rect, 0.03f * s, 0.03f * s, stroke)
                rect.set(cx - 0.34f * s, by + 0.12f * s, cx + 0.34f * s, by + 0.21f * s)
                c.drawRoundRect(rect, 0.045f * s, 0.045f * s, stroke)
                stroke.color = 0xFFF3E9D2.toInt()
                stroke.strokeWidth = 0.018f * s
                for (x in floatArrayOf(-0.2f, -0.05f, 0.1f, 0.25f)) {
                    c.drawLine(cx + x * s, by + 0.135f * s, cx + (x - 0.03f) * s, by + 0.195f * s, stroke)
                }
                stroke.color = OUTLINE
                stroke.strokeWidth = 0.026f * s
            }
            Item.TIE -> {
                fill.color = 0xFF3C64B4.toInt()
                path.reset()
                path.moveTo(cx - 0.035f * s, by + 0.13f * s)
                path.lineTo(cx + 0.035f * s, by + 0.13f * s)
                path.lineTo(cx + 0.055f * s, by + 0.32f * s)
                path.lineTo(cx, by + 0.37f * s)
                path.lineTo(cx - 0.055f * s, by + 0.32f * s)
                path.close()
                c.drawPath(path, fill)
                c.drawPath(path, stroke)
                rect.set(cx - 0.045f * s, by + 0.11f * s, cx + 0.045f * s, by + 0.17f * s)
                c.drawRoundRect(rect, 0.015f * s, 0.015f * s, fill)
                c.drawRoundRect(rect, 0.015f * s, 0.015f * s, stroke)
            }
            Item.RAINBOW_BANDANA -> {
                path.reset()
                path.moveTo(cx - 0.24f * s, by + 0.12f * s)
                path.lineTo(cx + 0.24f * s, by + 0.12f * s)
                path.lineTo(cx, by + 0.34f * s)
                path.close()
                c.save()
                c.clipPath(path)
                val bands = intArrayOf(0xFFE53935.toInt(), 0xFFFB8C00.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt(), 0xFF1E88E5.toInt(), 0xFF8E24AA.toInt())
                val h = 0.22f * s / bands.size
                bands.forEachIndexed { i, color ->
                    fill.color = color
                    c.drawRect(cx - 0.25f * s, by + 0.12f * s + i * h, cx + 0.25f * s, by + 0.12f * s + (i + 1) * h, fill)
                }
                c.restore()
                c.drawPath(path, stroke)
            }
            Item.BACKPACK -> {
                stroke.color = 0xFF3A70B5.toInt()
                stroke.strokeWidth = 0.04f * s
                path.reset()
                // Straps run down the fur at the sides, outside the face patch.
                path.moveTo(cx - 0.39f * s, by - 0.10f * s)
                path.quadTo(cx - 0.37f * s, by + 0.16f * s, cx - 0.27f * s, by + 0.31f * s)
                path.moveTo(cx + 0.39f * s, by - 0.10f * s)
                path.quadTo(cx + 0.37f * s, by + 0.16f * s, cx + 0.27f * s, by + 0.31f * s)
                c.drawPath(path, stroke)
                stroke.color = OUTLINE
                stroke.strokeWidth = 0.026f * s
            }
            Item.CAPE -> {
                fill.color = 0xFFF2C14E.toInt()
                c.drawCircle(cx, by + 0.15f * s, 0.035f * s, fill)
                c.drawCircle(cx, by + 0.15f * s, 0.035f * s, stroke)
            }
            else -> Unit
        }
    }

    // ------------------------------------------------------------------ props

    private fun drawProp(c: Canvas, item: Item, cx: Float, by: Float, s: Float, t: Float, sign: String?) {
        when (item) {
            Item.SEED -> {
                fill.color = SEED
                oval(c, cx, by + 0.19f * s, 0.035f * s, 0.06f * s, fill)
                fill.color = 0xFFFFF3E2.toInt()
                oval(c, cx, by + 0.19f * s, 0.008f * s, 0.04f * s, fill)
            }
            Item.WATER_BOTTLE -> {
                rect.set(cx - 0.055f * s, by + 0.06f * s, cx + 0.055f * s, by + 0.29f * s)
                fill.color = 0xFFD6EEFB.toInt()
                c.drawRoundRect(rect, 0.03f * s, 0.03f * s, fill)
                fill.color = 0xFF6FB7E8.toInt()
                c.drawRect(cx - 0.05f * s, by + 0.15f * s, cx + 0.05f * s, by + 0.28f * s, fill)
                c.drawRoundRect(rect, 0.03f * s, 0.03f * s, stroke)
                fill.color = 0xFF3C64B4.toInt()
                rect.set(cx - 0.035f * s, by + 0.02f * s, cx + 0.035f * s, by + 0.065f * s)
                c.drawRoundRect(rect, 0.01f * s, 0.01f * s, fill)
                c.drawRoundRect(rect, 0.01f * s, 0.01f * s, stroke)
            }
            Item.MAGNIFIER -> {
                stroke.strokeWidth = 0.035f * s
                stroke.color = 0xFF7A4E2D.toInt()
                c.drawLine(cx + 0.02f * s, by + 0.25f * s, cx + 0.13f * s, by + 0.10f * s, stroke)
                stroke.color = OUTLINE
                stroke.strokeWidth = 0.026f * s
                fill.color = 0x66D6EEFB
                c.drawCircle(cx + 0.19f * s, by + 0.03f * s, 0.09f * s, fill)
                stroke.color = 0xFF9AA4AE.toInt()
                stroke.strokeWidth = 0.03f * s
                c.drawCircle(cx + 0.19f * s, by + 0.03f * s, 0.09f * s, stroke)
                stroke.color = OUTLINE
                stroke.strokeWidth = 0.026f * s
            }
            Item.UMBRELLA -> drawUmbrella(c, cx, by, s, t)
            Item.BREAK_SIGN -> drawSign(c, cx, by, s, sign ?: "Let's take\na break!")
            else -> Unit
        }
    }

    private fun drawUmbrella(c: Canvas, cx: Float, by: Float, s: Float, t: Float) {
        val ux = cx + 0.34f * s
        val uy = by - 0.42f * s
        val r = 0.30f * s
        // handle with a little hook
        stroke.strokeWidth = 0.03f * s
        c.drawLine(ux, uy, ux, by + 0.16f * s, stroke)
        rect.set(ux - 0.07f * s, by + 0.12f * s, ux, by + 0.2f * s)
        c.drawArc(rect, 0f, 180f, false, stroke)
        stroke.strokeWidth = 0.026f * s
        // canopy with scalloped edge
        path.reset()
        rect.set(ux - r, uy - r, ux + r, uy + r)
        path.arcTo(rect, 180f, 180f)
        val w = 2 * r / 3
        var x = ux + r
        repeat(3) {
            path.quadTo(x - w / 2, uy - 0.06f * s, x - w, uy)
            x -= w
        }
        path.close()
        fill.color = 0xFFE8707A.toInt()
        c.drawPath(path, fill)
        c.drawPath(path, stroke)
        c.drawLine(ux, uy - r, ux - w / 2, uy, stroke)
        c.drawLine(ux, uy - r, ux + w / 2, uy, stroke)
        // rain falling around it
        stroke.color = 0xFF6FB7E8.toInt()
        for (i in 0 until 5) {
            val dx = floatArrayOf(-0.42f, -0.28f, 0.72f, 0.78f, -0.5f)[i] * s
            val phase = ((t * 0.9f + i * 0.23f) % 1f) * 0.5f * s
            val y = by - 0.6f * s + phase
            c.drawLine(cx + dx, y, cx + dx - 0.01f * s, y + 0.06f * s, stroke)
        }
        stroke.color = OUTLINE
    }

    private fun drawSign(c: Canvas, cx: Float, by: Float, s: Float, label: String) {
        val sx = cx + 0.30f * s
        stroke.strokeWidth = 0.03f * s
        stroke.color = 0xFF7A4E2D.toInt()
        c.drawLine(sx, by - 0.5f * s, sx, by + 0.18f * s, stroke)
        stroke.color = OUTLINE
        stroke.strokeWidth = 0.026f * s
        rect.set(cx - 0.36f * s, by - 0.80f * s, cx + 0.40f * s, by - 0.48f * s)
        fill.color = 0xFFF0CF98.toInt()
        c.drawRoundRect(rect, 0.04f * s, 0.04f * s, fill)
        c.drawRoundRect(rect, 0.04f * s, 0.04f * s, stroke)
        text.color = OUTLINE
        text.textSize = 0.085f * s
        val lines = label.split("\n").take(2)
        val mid = (rect.top + rect.bottom) / 2
        lines.forEachIndexed { i, line ->
            val y = mid + (i - (lines.size - 1) / 2f) * 0.1f * s + 0.03f * s
            c.drawText(line, rect.centerX(), y, text)
        }
    }

    // ------------------------------------------------------------------ head

    private fun drawHeadItem(c: Canvas, item: Item, cx: Float, by: Float, s: Float) {
        val top = by - 0.36f * s
        when (item) {
            Item.BOW -> {
                fill.color = 0xFFFF7FA8.toInt()
                val bx = cx - 0.13f * s
                val bw = 0.09f * s
                path.reset()
                path.moveTo(bx, top)
                path.lineTo(bx - bw, top - 0.06f * s)
                path.lineTo(bx - bw, top + 0.06f * s)
                path.close()
                path.moveTo(bx, top)
                path.lineTo(bx + bw, top - 0.06f * s)
                path.lineTo(bx + bw, top + 0.06f * s)
                path.close()
                c.drawPath(path, fill)
                c.drawPath(path, stroke)
                c.drawCircle(bx, top, 0.03f * s, fill)
                c.drawCircle(bx, top, 0.03f * s, stroke)
            }
            Item.PARTY_HAT -> {
                path.reset()
                path.moveTo(cx - 0.11f * s, top + 0.02f * s)
                path.lineTo(cx + 0.11f * s, top + 0.02f * s)
                path.lineTo(cx + 0.02f * s, top - 0.26f * s)
                path.close()
                fill.color = 0xFF7C5CD6.toInt()
                c.drawPath(path, fill)
                c.save()
                c.clipPath(path)
                fill.color = 0xFFFDD835.toInt()
                for (k in 0..3) c.drawRect(cx - 0.2f * s, top - 0.04f * s - k * 0.075f * s, cx + 0.2f * s, top - 0.01f * s - k * 0.075f * s, fill)
                c.restore()
                c.drawPath(path, stroke)
                fill.color = 0xFFFF7FA8.toInt()
                c.drawCircle(cx + 0.02f * s, top - 0.27f * s, 0.035f * s, fill)
                c.drawCircle(cx + 0.02f * s, top - 0.27f * s, 0.035f * s, stroke)
            }
            Item.HEADPHONES -> {
                stroke.strokeWidth = 0.045f * s
                stroke.color = 0xFF3A3A44.toInt()
                rect.set(cx - 0.40f * s, by - 0.48f * s, cx + 0.40f * s, by + 0.25f * s)
                c.drawArc(rect, 200f, 140f, false, stroke)
                stroke.color = OUTLINE
                stroke.strokeWidth = 0.026f * s
                fill.color = 0xFFE8707A.toInt()
                for (side in intArrayOf(-1, 1)) {
                    rect.set(cx + side * 0.40f * s - 0.06f * s, by - 0.18f * s, cx + side * 0.40f * s + 0.06f * s, by + 0.02f * s)
                    c.drawRoundRect(rect, 0.05f * s, 0.05f * s, fill)
                    c.drawRoundRect(rect, 0.05f * s, 0.05f * s, stroke)
                }
            }
            Item.CROWN -> {
                path.reset()
                path.moveTo(cx - 0.14f * s, top + 0.03f * s)
                path.lineTo(cx - 0.14f * s, top - 0.10f * s)
                path.lineTo(cx - 0.07f * s, top - 0.03f * s)
                path.lineTo(cx, top - 0.13f * s)
                path.lineTo(cx + 0.07f * s, top - 0.03f * s)
                path.lineTo(cx + 0.14f * s, top - 0.10f * s)
                path.lineTo(cx + 0.14f * s, top + 0.03f * s)
                path.close()
                fill.color = 0xFFF2C14E.toInt()
                c.drawPath(path, fill)
                c.drawPath(path, stroke)
                fill.color = 0xFFE8707A.toInt()
                c.drawCircle(cx, top - 0.01f * s, 0.022f * s, fill)
            }
            Item.BEANIE -> {
                fill.color = 0xFF5B8BD9.toInt()
                rect.set(cx - 0.30f * s, top - 0.13f * s, cx + 0.30f * s, top + 0.17f * s)
                c.drawArc(rect, 180f, 180f, true, fill)
                c.drawArc(rect, 180f, 180f, false, stroke)
                fill.color = 0xFF8FB2EA.toInt()
                rect.set(cx - 0.32f * s, top - 0.01f * s, cx + 0.32f * s, top + 0.06f * s)
                c.drawRoundRect(rect, 0.03f * s, 0.03f * s, fill)
                c.drawRoundRect(rect, 0.03f * s, 0.03f * s, stroke)
                fill.color = WHITE
                c.drawCircle(cx, top - 0.14f * s, 0.045f * s, fill)
                c.drawCircle(cx, top - 0.14f * s, 0.045f * s, stroke)
            }
            Item.PUMPKIN_HAT -> {
                fill.color = 0xFFF08A24.toInt()
                oval(c, cx, top - 0.05f * s, 0.14f * s, 0.09f * s, fill)
                oval(c, cx, top - 0.05f * s, 0.14f * s, 0.09f * s, stroke)
                stroke.strokeWidth = 0.018f * s
                c.drawLine(cx - 0.05f * s, top - 0.13f * s, cx - 0.05f * s, top + 0.03f * s, stroke)
                c.drawLine(cx + 0.05f * s, top - 0.13f * s, cx + 0.05f * s, top + 0.03f * s, stroke)
                stroke.strokeWidth = 0.026f * s
                stroke.color = 0xFF3F8F3A.toInt()
                c.drawLine(cx, top - 0.13f * s, cx + 0.03f * s, top - 0.19f * s, stroke)
                stroke.color = OUTLINE
            }
            else -> Unit
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
