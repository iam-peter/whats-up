package app.whatsup.widget

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import app.whatsup.config.AppConfig
import app.whatsup.config.configStore
import app.whatsup.data.EntryLoader
import app.whatsup.data.Permissions
import app.whatsup.logic.GridRange
import app.whatsup.update.UpdateScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.WeekFields

class WhatsUpWidget : GlanceAppWidget() {
    /**
     * Exact sizes: the fitting engine measures against the real widget size.
     * Responsive buckets would compose the full grid once per bucket.
     */
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val store = context.configStore
        val initialConfig = store.data.first()
        val initial = withContext(Dispatchers.IO) { loadState(context, initialConfig, appWidgetId) }
        UpdateScheduler.scheduleNextBoundary(context, initial.entries)

        provideContent {
            val config by store.data.collectAsState(initialConfig)
            // Reload whenever the config (incl. refreshToken) changes.
            val state by produceState(initial, config) {
                if (config != initialConfig) {
                    value = withContext(Dispatchers.IO) { loadState(context, config, appWidgetId) }
                    UpdateScheduler.scheduleNextBoundary(context, value.entries)
                }
            }
            WidgetContent(state)
        }
    }

    companion object {
        fun loadState(context: Context, config: AppConfig, appWidgetId: Int): WidgetState {
            val today = LocalDate.now()
            val hasPermission = Permissions.hasCalendar(context)
            val fdow = WeekFields.of(context.resources.configuration.locales[0]).firstDayOfWeek
            val days = GridRange.days(today, fdow, GridRange.MAX_WEEKS)
            val entries = if (hasPermission) {
                EntryLoader(context).load(days.first(), days.last(), config, config.calendarIdsFor(appWidgetId))
            } else emptyList()
            return WidgetState(hasPermission, entries, config.widget(appWidgetId), today, Instant.now(), appWidgetId)
        }
    }
}
