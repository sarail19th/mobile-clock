package app.personal.clock.widget

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import app.personal.clock.alarm.AlarmScheduler
import app.personal.clock.data.Store
import app.personal.clock.sys

class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, WidgetRenderer.build(context, it)) }
        WidgetTicker.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // Resized: re-fit the text to the new bounds.
        appWidgetManager.updateAppWidget(appWidgetId, WidgetRenderer.build(context, appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = Store.get(context)
        appWidgetIds.forEach { store.deleteWidgetConfig(it) }
        WidgetTicker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        WidgetTicker.schedule(context) // last widget removed: cancels the minute timer
    }

    companion object {
        /** Re-render every placed widget (alarm list changed, zone changed, settings changed, minute tick...). */
        fun updateAll(ctx: Context) {
            val awm = AppWidgetManager.getInstance(ctx)
            val ids = awm.getAppWidgetIds(ComponentName(ctx, ClockWidgetProvider::class.java))
            ids.forEach { runCatching { awm.updateAppWidget(it, WidgetRenderer.build(ctx, it)) } }
            WidgetTicker.schedule(ctx)
        }
    }
}

/**
 * Minute timer for widgets drawn as pictures (custom fonts). It is a NON-wakeup alarm: while the phone sleeps
 * nothing runs and nothing is spent; the moment the phone wakes, the overdue alarm fires and the picture is
 * refreshed. Widgets using the System font are driven by the OS and need none of this.
 */
object WidgetTicker {
    private fun anyBitmapWidget(ctx: Context): Boolean {
        val ids = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, ClockWidgetProvider::class.java))
        val store = Store.get(ctx)
        return ids.any { WidgetRenderer.usesBitmap(store.widgetConfig(it)) }
    }

    fun schedule(ctx: Context) {
        val am = ctx.sys<AlarmManager>()
        val pi = AlarmScheduler.widgetTickPendingIntent(ctx)
        am.cancel(pi)
        if (!anyBitmapWidget(ctx)) return
        val next = (System.currentTimeMillis() / 60_000L + 1) * 60_000L + 150L
        try {
            am.setExact(AlarmManager.RTC, next, pi)
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC, next, pi)
        }
    }
}
