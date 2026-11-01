package com.aliuppal.pockethamster.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.RemoteViews
import com.aliuppal.pockethamster.R
import com.aliuppal.pockethamster.chat.PetAction
import com.aliuppal.pockethamster.chat.PetBrain
import com.aliuppal.pockethamster.companion.CompanionEngine
import com.aliuppal.pockethamster.pet.PetRenderer
import com.aliuppal.pockethamster.pet.PetRepository
import com.aliuppal.pockethamster.pet.PetState
import com.aliuppal.pockethamster.ui.MainActivity
import kotlin.math.roundToInt

/** Home screen widget: the hamster, its mood, stat bars and Feed / Play / Chat buttons. */
class PetWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_FEED = "com.aliuppal.pockethamster.widget.FEED"
        private const val ACTION_PLAY = "com.aliuppal.pockethamster.widget.PLAY"

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(context, PetWidgetProvider::class.java))
            if (ids.isNotEmpty()) render(context, mgr, ids, PetRepository.state.value)
        }

        private fun render(context: Context, mgr: AppWidgetManager, ids: IntArray, s: PetState) {
            val views = RemoteViews(context.packageName, R.layout.widget_pet).apply {
                setImageViewBitmap(R.id.pet_image, petBitmap(s))
                setTextViewText(R.id.pet_name, s.name)
                setTextViewText(R.id.pet_mood, s.mood.label)
                setProgressBar(R.id.bar_food, 100, s.fullness.roundToInt(), false)
                setProgressBar(R.id.bar_happy, 100, s.happiness.roundToInt(), false)
                setProgressBar(R.id.bar_energy, 100, s.energy.roundToInt(), false)
                setOnClickPendingIntent(R.id.btn_feed, broadcast(context, ACTION_FEED, 10))
                setOnClickPendingIntent(R.id.btn_play, broadcast(context, ACTION_PLAY, 11))
                setOnClickPendingIntent(R.id.btn_chat, openApp(context, chat = true, requestCode = 12))
                setOnClickPendingIntent(R.id.pet_image, openApp(context, chat = false, requestCode = 13))
            }
            mgr.updateAppWidget(ids, views)
        }

        private fun petBitmap(s: PetState): Bitmap {
            val size = 280
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            // Smaller and lower than the app's framing so an umbrella or sign still fits on top.
            PetRenderer.draw(
                Canvas(bmp), size / 2f, size * 0.58f, size * 0.7f, s.mood, t = 1f,
                eating = s.isEating(), look = CompanionEngine.currentLook(),
            )
            return bmp
        }

        private fun broadcast(context: Context, action: String, requestCode: Int) = PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, PetWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun openApp(context: Context, chat: Boolean, requestCode: Int) = PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_CHAT, chat),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        // refresh() applies stat decay and re-renders every widget through refreshAll().
        PetRepository.refresh()
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FEED -> PetBrain.perform(PetAction.FEED)
            ACTION_PLAY -> PetBrain.perform(PetAction.PLAY)
        }
        super.onReceive(context, intent)
    }
}
