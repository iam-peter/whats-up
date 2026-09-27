package app.whatsup.widget

import android.content.Context
import android.text.format.DateFormat
import app.whatsup.R
import app.whatsup.logic.GridLabels
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

class AndroidLabels(private val context: Context, private val zone: ZoneId = ZoneId.systemDefault()) : GridLabels {
    private val locale: Locale = context.resources.configuration.locales[0]
    private val timeFormat = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mma", locale)
    private val dayFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    private val shortDate = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale)

    override fun weekdayInitial(day: DayOfWeek) = day.getDisplayName(TextStyle.NARROW, locale)
    override fun birthdays(count: Int) = context.resources.getQuantityString(R.plurals.birthdays_count, count, count)
    override fun more(count: Int) = context.getString(R.string.more_count, count)
    override fun time(instant: Instant): String = timeFormat.format(instant.atZone(zone)).lowercase(locale)
    override fun birthdayText(name: String, age: Int?) = if (age != null) "$name ($age)" else name

    override fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> context.getString(R.string.today_title)
        today.plusDays(1) -> context.getString(R.string.tomorrow)
        else -> shortDate.format(date)
    }

    override fun timeRange(start: Instant, end: Instant) = "${time(start)}–${time(end)}"
    override fun allDay(): String = context.getString(R.string.all_day)
    override fun dateRange(first: LocalDate, last: LocalDate) = "${shortDate.format(first)} – ${shortDate.format(last)}"
    override fun ongoingUntil(end: Instant): String = context.getString(R.string.now_until, time(end))

    override fun dayDescription(date: LocalDate, isToday: Boolean, entries: List<String>, hidden: Int): String {
        val res = context.resources
        return buildString {
            append(dayFormat.format(date))
            if (isToday) append(", ").append(context.getString(R.string.today))
            append(", ").append(res.getQuantityString(R.plurals.entries_count, entries.size, entries.size))
            if (entries.isNotEmpty()) append(": ").append(entries.joinToString("; "))
        }
    }
}
