package app.whatsup.model

import java.time.Instant
import java.time.LocalDate

enum class EntryKind { BIRTHDAY, ALL_DAY, TIMED }

/**
 * Fill of filled chips, assigned per calendar. NONE is solid colour; the
 * others alternate between the colour and transparent.
 */
enum class ChipPattern { NONE, STRIPES, DOTS, GRID, ZIGZAG }

/** Outline of outlined (timed) chips, assigned per calendar. */
enum class LineStyle { SOLID, DASHED, DOTTED }

/**
 * One thing to show on one or more days. Birthdays, all-day and timed
 * events share this type so ordering, de-duplication and fitting can
 * treat them uniformly.
 */
data class CalendarEntry(
    val kind: EntryKind,
    val title: String,
    /** ARGB; for events this is `DISPLAY_COLOR` (event colour, else calendar colour). */
    val color: Int,
    val firstDay: LocalDate,
    /** Inclusive. */
    val lastDay: LocalDate = firstDay,
    val start: Instant? = null,
    val end: Instant? = null,
    val eventId: Long? = null,
    val calendarId: Long? = null,
    val isHoliday: Boolean = false,
    /** Only for birthdays with a known birth year. */
    val age: Int? = null,
    val pattern: ChipPattern = ChipPattern.NONE,
    val lineStyle: LineStyle = LineStyle.SOLID,
) {
    fun occursOn(day: LocalDate) = !day.isBefore(firstDay) && !day.isAfter(lastDay)
    val isMultiDay get() = lastDay.isAfter(firstDay)
}
