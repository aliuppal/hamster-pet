package com.aliuppal.pockethamster.pet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders every mood with the real Android graphics stack and writes PNGs to build/pet-renders/. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PetRendererTest {
    @Test
    fun rendersEveryMood() {
        val bg = Color.parseColor("#FFE9D2")
        val out = File("build/pet-renders").apply { mkdirs() }
        val cases = Mood.entries.map { m -> m.name to { c: Canvas -> PetRenderer.draw(c, 150f, 150f, 240f, m, 1f) } } +
            listOf(
                "EATING" to { c: Canvas -> PetRenderer.draw(c, 150f, 150f, 240f, Mood.HAPPY, 1f, eating = true) },
                "WALKING" to { c: Canvas -> PetRenderer.draw(c, 150f, 150f, 240f, Mood.OK, 0.13f, walking = true) },
            )
        for ((name, draw) in cases) {
            val bmp = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp).apply { drawColor(bg) }
            draw(canvas)
            assertTrue("$name drew nothing", bmp.getPixel(150, 200) != bg)
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
