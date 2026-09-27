package app.whatsup.logic

import app.whatsup.config.GlobalConfig
import app.whatsup.model.BirthdaySource
import app.whatsup.model.CalendarEntry
import app.whatsup.model.ChipPattern
import app.whatsup.model.LineStyle
import app.whatsup.model.EntryKind

internal fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

object EventDeduplicator {
    /**
     * Spec FR-D5 (v1.14): the same event in several calendars (same title,
     * days and start) is shown once, from the calendar with the lowest ID.
     * Holidays in two languages don't match; the user unticks one calendar.
     */
    fun apply(entries: List<CalendarEntry>): List<CalendarEntry> =
        entries.sortedBy { it.calendarId ?: Long.MAX_VALUE }
            .distinctBy { listOf(normalize(it.title), it.firstDay, it.lastDay, it.start) }
}

object BirthdayMerger {
    /**
     * Spec FR-B1 (optional): for the same person and day, the contact
     * birthday wins over Google Calendar's entry, because it carries the age.
     * A Google entry counts as the same person if its title contains the
     * contact's name, e.g. "Anna Muster's birthday".
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

object ColorOverrides {
    /**
     * Spec FR-E4a: a calendar colour chosen in whats-up replaces the source
     * colour for that calendar's events, except events with their own
     * colour, as in Google Calendar. Birthdays have their own settings.
     */
    fun apply(entries: List<CalendarEntry>, global: GlobalConfig): List<CalendarEntry> = entries.map { e ->
        val override = e.calendarId?.let { global.calendarColors[it] }
        if (override == null || e.hasOwnColor || e.kind == EntryKind.BIRTHDAY) e else e.copy(color = override)
    }
}

object PatternAssigner {
    /** Spec FR-E6: fill and line style come from the entry's calendar, or the contacts setting. */
    fun assign(entries: List<CalendarEntry>, global: GlobalConfig): List<CalendarEntry> = entries.map { e ->
        val id = e.calendarId
        val pattern = when {
            e.birthdaySource == BirthdaySource.CONTACTS -> global.contactBirthdayPattern
            e.birthdaySource == BirthdaySource.GOOGLE -> global.googleBirthdayPattern
            id != null -> global.calendarPatterns[id] ?: ChipPattern.NONE
            else -> ChipPattern.NONE
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
