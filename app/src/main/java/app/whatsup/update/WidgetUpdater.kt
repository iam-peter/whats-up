package app.whatsup.update

import android.content.BroadcastReceiver
import android.content.Context
import androidx.glance.appwidget.updateAll
import app.whatsup.config.configStore
import app.whatsup.widget.WhatsUpWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object WidgetUpdater {
    /**
     * Bumping the token makes running widget sessions reload; updateAll
     * starts sessions for widgets that have none.
     */
    suspend fun refreshAll(context: Context) {
        context.configStore.updateData { it.copy(refreshToken = it.refreshToken + 1) }
        WhatsUpWidget().updateAll(context)
    }
}

fun BroadcastReceiver.launchAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}
