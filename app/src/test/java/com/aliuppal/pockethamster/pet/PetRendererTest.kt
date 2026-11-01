package com.aliuppal.pockethamster.pet

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.aliuppal.pockethamster.cosmetics.Fur
import com.aliuppal.pockethamster.cosmetics.Item
import com.aliuppal.pockethamster.cosmetics.Look
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders every mood, fur and costume with the real Android graphics stack into build/pet-renders/. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class PetRendererTest {
    private val bg = Color.parseColor("#FFE9D2")
    private val out = File("build/pet-renders").apply { mkdirs() }

    private fun render(name: String, draw: (Canvas) -> Unit) {
        val bmp = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp).apply { drawColor(bg) })
        assertTrue("$name drew nothing", bmp.getPixel(150, 200) != bg)
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // Same framing as the app's PetCanvas: centre at 56% height, size 72% of the box.
    private fun pet(c: Canvas, mood: Mood = Mood.HAPPY, look: Look = Look(), t: Float = 1f, walking: Boolean = false, eating: Boolean = false) =
        PetRenderer.draw(c, 150f, 168f, 216f, mood, t, walking, eating, look)

    @Test
    fun rendersEveryMood() {
        Mood.entries.forEach { m -> render("mood_${m.name}") { pet(it, m) } }
        render("EATING") { pet(it, eating = true) }
        render("WALKING") { pet(it, Mood.OK, t = 0.13f, walking = true) }
    }

    @Test
    fun rendersEveryFur() {
        Fur.entries.forEach { f -> render("fur_${f.name}") { pet(it, look = Look(fur = f)) } }
    }

    @Test
    fun rendersEveryItem() {
        Item.entries.forEach { item ->
            render("item_${item.name}") { pet(it, look = Look().with(item.slot, item)) }
        }
    }

    @Test
    fun rendersContextScenes() {
        render("scene_hydration") { pet(it, look = Look(body = Item.BACKPACK, prop = Item.WATER_BOTTLE)) }
        render("scene_break_sign") { pet(it, Mood.OK, Look(prop = Item.BREAK_SIGN, sign = "Let's take\na break!")) }
        render("scene_sleep_mask") { pet(it, Mood.SLEEPING, Look(face = Item.SLEEP_MASK)) }
        render("scene_night_sleepy") { pet(it, Mood.HAPPY, Look(sleepy = true)) }
        render("scene_charging_buzz") { pet(it, Mood.HAPPY, Look(shiver = true), t = 0.02f) }
        render("scene_work_mode") { pet(it, look = Look(face = Item.GLASSES, body = Item.TIE)) }
        render("scene_weekend") { pet(it, look = Look(fur = Fur.GALAXY_BLUE, face = Item.SUNGLASSES, body = Item.BACKPACK, head = Item.CROWN)) }
        render("scene_rain") { pet(it, Mood.OK, Look(fur = Fur.CALICO, head = Item.BEANIE, body = Item.SCARF, prop = Item.UMBRELLA)) }
    }
}
