package app.whatsup.widget

import android.appwidget.AppWidgetManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import app.whatsup.logic.ChipTarget
import app.whatsup.ui.DayActivity
import app.whatsup.ui.MainActivity
import java.time.LocalDate
import java.time.ZoneId

/** An app that can show calendar dates, for the picker (Q-41). */
data class CalendarApp(val packageName: String, val label: String)

object Intents {
    fun forTarget(
        context: Context,
        target: ChipTarget,
        calendarPackage: String?,
        appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
    ): Intent = when (target) {
        is ChipTarget.Event -> Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, target.eventId))
            .apply {
                target.begin?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it.toEpochMilli()) }
                target.end?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it.toEpochMilli()) }
            }.inCalendarApp(context, calendarPackage)
        is ChipTarget.CalendarDay -> calendarDay(context, target.date, calendarPackage)
        is ChipTarget.InAppDay -> inAppDay(context, target.date, appWidgetId)
    }

    fun calendarDay(context: Context, date: LocalDate, calendarPackage: String?): Intent {
        val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return dayIntent(millis).inCalendarApp(context, calendarPackage)
    }

    fun inAppDay(context: Context, date: LocalDate, appWidgetId: Int) = Intent(context, DayActivity::class.java)
        .putExtra(DayActivity.EXTRA_DATE, date.toString())
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        // Distinct data so PendingIntents for different days and widgets don't collapse.
        .setData(android.net.Uri.parse("whatsup://day/$appWidgetId/$date"))

    fun grantAccess(context: Context) = Intent(context, MainActivity::class.java)

    /** Apps that open calendar dates, for the calendar app picker (Q-41). */
    fun calendarApps(context: Context): List<CalendarApp> {
        val pm = context.packageManager
        return pm.queryIntentActivities(dayIntent(0), 0)
            .map { CalendarApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun dayIntent(millis: Long): Intent {
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").also { ContentUris.appendId(it, millis) }.build()
        return Intent(Intent.ACTION_VIEW, uri)
    }

    /**
     * Targets the chosen calendar app (FR-I3). If it can't open this kind of
     * link, the app is just launched instead.
     */
    private fun Intent.inCalendarApp(context: Context, pkg: String?): Intent {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (pkg == null) return this
        val targeted = Intent(this).setPackage(pkg)
        if (targeted.resolveActivity(context.packageManager) != null) return targeted
        return context.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: this
    }
}
