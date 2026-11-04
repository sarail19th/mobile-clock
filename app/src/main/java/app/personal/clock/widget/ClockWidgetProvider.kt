package app.personal.clock.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import app.personal.clock.data.Store

class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, WidgetRenderer.build(context, it)) }
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
    }

    companion object {
        /** Re-render every placed widget (alarm list changed, zone changed, settings changed...). */
        fun updateAll(ctx: Context) {
            val awm = AppWidgetManager.getInstance(ctx)
            val ids = awm.getAppWidgetIds(ComponentName(ctx, ClockWidgetProvider::class.java))
            ids.forEach { runCatching { awm.updateAppWidget(it, WidgetRenderer.build(ctx, it)) } }
        }
    }
}
