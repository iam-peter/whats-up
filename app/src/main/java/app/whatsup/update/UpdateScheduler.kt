package app.whatsup.update

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.whatsup.model.CalendarEntry
import app.whatsup.model.EntryKind
import app.whatsup.widget.WhatsUpWidgetReceiver
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Event-driven refreshes only, no polling (spec FR-U1, FR-U2). */
object UpdateScheduler {
    private const val WORK_CONTENT = "content-change"
    private const val ACTION_MIDNIGHT = "app.whatsup.action.MIDNIGHT"
    private const val ACTION_BOUNDARY = "app.whatsup.action.BOUNDARY"
    private const val ALARM_WINDOW_MS = 60_000L

    fun ensureScheduled(context: Context) {
        if (!hasWidgets(context)) return
        observeContent(context, rearm = false)
        scheduleMidnight(context)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_CONTENT)
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.cancel(alarmIntent(context, ACTION_MIDNIGHT))
        alarms.cancel(alarmIntent(context, ACTION_BOUNDARY))
    }

    fun observeContent(context: Context, rearm: Boolean) {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
            .addContentUriTrigger(ContactsContract.Contacts.CONTENT_URI, true)
            .setTriggerContentUpdateDelay(Duration.ofSeconds(2))
            .setTriggerContentMaxDelay(Duration.ofSeconds(5))
            .build()
        val request = OneTimeWorkRequestBuilder<ContentChangeWorker>().setConstraints(constraints).build()
        // A running worker re-arms by appending; otherwise keep an existing observer.
        val policy = if (rearm) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_CONTENT, policy, request)
    }

    private fun scheduleMidnight(context: Context) {
        val zone = ZoneId.systemDefault()
        val midnight = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        setWindow(context, ACTION_MIDNIGHT, midnight)
    }

    /** Re-render when the next of today's events ends, so it gets dimmed (FR-E5). */
    fun scheduleNextBoundary(context: Context, entries: List<CalendarEntry>, now: Instant = Instant.now()) {
        val today = LocalDate.now()
        val next = entries.asSequence()
            .filter { it.kind == EntryKind.TIMED && it.occursOn(today) }
            .mapNotNull { it.end }
            .filter { it.isAfter(now) }
            .minOrNull() ?: return
        setWindow(context, ACTION_BOUNDARY, next.toEpochMilli())
    }

    private fun setWindow(context: Context, action: String, atMillis: Long) {
        context.getSystemService(AlarmManager::class.java)
            .setWindow(AlarmManager.RTC, atMillis, ALARM_WINDOW_MS, alarmIntent(context, action))
    }

    private fun alarmIntent(context: Context, action: String) = PendingIntent.getBroadcast(
        context, 0, Intent(context, RefreshReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun hasWidgets(context: Context) = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, WhatsUpWidgetReceiver::class.java)).isNotEmpty()
}
