package app.whatsup.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Midnight / event-end alarms and time, zone, locale and boot broadcasts. */
class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        launchAsync {
            WidgetUpdater.refreshAll(context)
            UpdateScheduler.ensureScheduled(context)
        }
    }
}
