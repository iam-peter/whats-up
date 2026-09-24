package app.whatsup.logic

import app.whatsup.config.GlobalConfig
import app.whatsup.model.CalendarEntry
import app.whatsup.model.ChipPattern
import app.whatsup.model.LineStyle
import app.whatsup.model.EntryKind
import java.time.LocalDate

internal fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

object HolidayDeduplicator {
    /**
     * Spec FR-D5 / D-4: on days where the preferred holiday calendar has an
     * entry, entries from other holiday calendars are dropped. Identical
     * titles across holiday calendars are merged as well.
     */
    fun apply(entries: List<CalendarEntry>, preferredCalendarId: Long?): List<CalendarEntry> {
        val holidays = entries.filter { it.isHoliday }
        if (holidays.isEmpty()) return entries
        val preferred = preferredCalendarId?.takeIf { id -> holidays.any { it.calendarId == id } }
            ?: holidays.mapNotNull { it.calendarId }.minOrNull()
        val preferredDays: Set<LocalDate> = holidays
            .filter { it.calendarId == preferred }
            .flatMap { e -> generateSequence(e.firstDay) { it.plusDays(1) }.takeWhile { !it.isAfter(e.lastDay) } }
            .toSet()

        val seen = HashSet<Pair<LocalDate, String>>()
        return entries.filter { e ->
            if (!e.isHoliday) return@filter true
            if (e.calendarId != preferred && e.firstDay in preferredDays) return@filter false
            seen.add(e.firstDay to normalize(e.title))
        }
    }
}

object BirthdayMerger {
    /**
     * Spec FR-B1: contact birthdays win over entries of the Google
     * "Birthdays" calendar for the same person and day, because they carry
     * the birth year.
     */
    fun merge(fromContacts: List<CalendarEntry>, fromCalendar: List<CalendarEntry>): List<CalendarEntry> {
        val contactNames = fromContacts.groupBy({ it.firstDay }, { normalize(it.title) })
        val calendarOnly = fromCalendar.filterNot { e ->
            val title = normalize(e.title)
            contactNames[e.firstDay].orEmpty().any { it.isNotEmpty() && title.contains(it) }
        }
        return fromContacts + calendarOnly
    }
}

object PatternAssigner {
    /** Spec FR-E6: fill and line style come from the entry's calendar, or the contacts setting. */
    fun assign(entries: List<CalendarEntry>, global: GlobalConfig): List<CalendarEntry> = entries.map { e ->
        val id = e.calendarId
        val pattern = when (id) {
            null -> if (e.kind == EntryKind.BIRTHDAY) global.contactBirthdayPattern else ChipPattern.NONE
            else -> global.calendarPatterns[id] ?: ChipPattern.NONE
        }
        val lineStyle = id?.let { global.calendarLineStyles[it] } ?: LineStyle.SOLID
        if (pattern == e.pattern && lineStyle == e.lineStyle) e else e.copy(pattern = pattern, lineStyle = lineStyle)
    }
}

/** Spec FR-D6: birthdays, then all-day (multi-day first), then timed by start. */
val entryOrder: Comparator<CalendarEntry> = compareBy<CalendarEntry>(
    { it.kind.ordinal },
    { if (it.kind == EntryKind.ALL_DAY && it.isMultiDay) 0 else 1 },
    { it.start },
    { it.title.lowercase() },
)
