package app.whatsup.widget

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import app.whatsup.logic.ChipTarget
import app.whatsup.ui.DayActivity
import app.whatsup.ui.MainActivity
import java.time.LocalDate
import java.time.ZoneId

object Intents {
    fun forTarget(context: Context, target: ChipTarget, calendarPackage: String?): Intent = when (target) {
        is ChipTarget.Event -> Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, target.eventId))
            .apply {
                target.begin?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it.toEpochMilli()) }
                target.end?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it.toEpochMilli()) }
            }.inCalendarApp(calendarPackage)
        is ChipTarget.CalendarDay -> calendarDay(target.date, calendarPackage)
        is ChipTarget.InAppDay -> inAppDay(context, target.date)
    }

    fun calendarDay(date: LocalDate, calendarPackage: String?): Intent {
        val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").also { ContentUris.appendId(it, millis) }.build()
        return Intent(Intent.ACTION_VIEW, uri).inCalendarApp(calendarPackage)
    }

    fun inAppDay(context: Context, date: LocalDate) = Intent(context, DayActivity::class.java)
        .putExtra(DayActivity.EXTRA_DATE, date.toString())
        // Distinct data so PendingIntents for different days don't collapse.
        .setData(android.net.Uri.parse("whatsup://day/$date"))

    fun grantAccess(context: Context) = Intent(context, MainActivity::class.java)

    private fun Intent.inCalendarApp(pkg: String?) = apply {
        pkg?.let { setPackage(it) }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
