package app.whatsup.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import app.whatsup.config.configStore
import app.whatsup.update.UpdateScheduler
import app.whatsup.update.launchAsync

class WhatsUpWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = WhatsUpWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        UpdateScheduler.ensureScheduled(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        UpdateScheduler.cancelAll(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        launchAsync {
            context.configStore.updateData { c -> c.copy(widgets = c.widgets - appWidgetIds.toSet()) }
        }
    }

    /** Auto Backup restores configs under old IDs; move them to the new ones (FR-C3). */
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        launchAsync {
            context.configStore.updateData { c ->
                val moved = oldWidgetIds.zip(newWidgetIds).mapNotNull { (old, new) -> c.widgets[old]?.let { new to it } }
                c.copy(widgets = c.widgets - oldWidgetIds.toSet() + moved)
            }
        }
    }
}
