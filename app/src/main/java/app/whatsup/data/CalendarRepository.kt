package app.whatsup.data

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import app.whatsup.model.CalendarEntry
import app.whatsup.model.EntryKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class CalendarInfo(
    val id: Long,
    val name: String,
    val account: String,
    val color: Int,
    val isHoliday: Boolean,
    val isBirthdays: Boolean,
)

class CalendarRepository(private val context: Context) {
    companion object {
        const val BIRTHDAY_CALENDAR = "addressbook#contacts@group.v.calendar.google.com"
        private const val HOLIDAY_SUFFIX = "#holiday@group.v.calendar.google.com"

        fun isHoliday(owner: String?) = owner?.endsWith(HOLIDAY_SUFFIX) == true
        fun isBirthdays(owner: String?) = owner == BIRTHDAY_CALENDAR
    }

    fun calendars(): List<CalendarInfo> {
        if (!Permissions.hasCalendar(context)) return emptyList()
        val projection = arrayOf(
            Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
            Calendars.CALENDAR_COLOR, Calendars.OWNER_ACCOUNT,
        )
        return context.contentResolver.query(
            Calendars.CONTENT_URI, projection, "${Calendars.VISIBLE} = 1", null,
            "${Calendars.ACCOUNT_NAME}, ${Calendars.CALENDAR_DISPLAY_NAME}",
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val owner = c.getString(4)
                    add(CalendarInfo(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getInt(3),
                        isHoliday(owner), isBirthdays(owner)))
                }
            }
        }.orEmpty()
    }

    /**
     * Expanded instances in [from]..[to] (inclusive), without declined and
     * cancelled events (FR-D1, FR-D4). Entries of the Google birthday
     * calendar come back as [EntryKind.BIRTHDAY].
     */
    fun instances(from: LocalDate, to: LocalDate, calendarIds: Set<Long>?, zone: ZoneId): List<CalendarEntry> {
        if (!Permissions.hasCalendar(context)) return emptyList()
        // All-day instances are stored in UTC; widen the window by a day on each side.
        val begin = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY,
            Instances.DISPLAY_COLOR, Instances.CALENDAR_ID, Instances.OWNER_ACCOUNT,
            Instances.SELF_ATTENDEE_STATUS, Instances.STATUS,
        )
        val selection = buildString {
            append("${Instances.VISIBLE} = 1")
            append(" AND (${Instances.STATUS} IS NULL OR ${Instances.STATUS} != ${Events.STATUS_CANCELED})")
            append(" AND ${Instances.SELF_ATTENDEE_STATUS} != ${Attendees.ATTENDEE_STATUS_DECLINED}")
            if (calendarIds != null) append(" AND ${Instances.CALENDAR_ID} IN (${calendarIds.joinToString().ifEmpty { "-1" }})")
        }
        return context.contentResolver.query(uri, projection, selection, null, "${Instances.BEGIN} ASC")?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val allDay = c.getInt(4) != 0
                    val startMs = c.getLong(2)
                    val endMs = c.getLong(3)
                    val owner = c.getString(7)
                    val firstDay: LocalDate
                    val lastDay: LocalDate
                    if (allDay) {
                        firstDay = Instant.ofEpochMilli(startMs).atZone(ZoneOffset.UTC).toLocalDate()
                        lastDay = Instant.ofEpochMilli(maxOf(startMs, endMs - 1)).atZone(ZoneOffset.UTC).toLocalDate()
                    } else {
                        firstDay = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()
                        lastDay = Instant.ofEpochMilli(maxOf(startMs, endMs - 1)).atZone(zone).toLocalDate()
                    }
                    if (lastDay.isBefore(from) || firstDay.isAfter(to)) continue
                    add(
                        CalendarEntry(
                            kind = when {
                                isBirthdays(owner) -> EntryKind.BIRTHDAY
                                allDay -> EntryKind.ALL_DAY
                                else -> EntryKind.TIMED
                            },
                            title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "—",
                            color = c.getInt(5),
                            firstDay = firstDay,
                            lastDay = lastDay,
                            start = if (allDay) null else Instant.ofEpochMilli(startMs),
                            end = if (allDay) null else Instant.ofEpochMilli(endMs),
                            eventId = c.getLong(0),
                            calendarId = c.getLong(6),
                            isHoliday = isHoliday(owner),
                        )
                    )
                }
            }
        }.orEmpty()
    }
}
