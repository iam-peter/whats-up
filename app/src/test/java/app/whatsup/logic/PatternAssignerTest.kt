package app.whatsup.logic

import app.whatsup.config.GlobalConfig
import app.whatsup.model.CalendarEntry
import app.whatsup.model.ChipPattern
import app.whatsup.model.EntryKind
import app.whatsup.model.LineStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PatternAssignerTest {
    private val day = LocalDate.of(2026, 9, 24)
    private val global = GlobalConfig(
        calendarPatterns = mapOf(7L to ChipPattern.STRIPES),
        calendarLineStyles = mapOf(7L to LineStyle.DASHED),
        contactBirthdayPattern = ChipPattern.DOTS,
    )

    private fun assign(e: CalendarEntry) = PatternAssigner.assign(listOf(e), global).single().pattern

    @Test fun `calendar pattern applies to its events`() =
        assertEquals(ChipPattern.STRIPES, assign(CalendarEntry(EntryKind.TIMED, "Standup", 0, day, calendarId = 7)))

    @Test fun `calendars without a pattern get none`() =
        assertEquals(ChipPattern.NONE, assign(CalendarEntry(EntryKind.ALL_DAY, "Trash", 0, day, calendarId = 8)))

    @Test fun `contact birthdays use the contacts setting`() =
        assertEquals(ChipPattern.DOTS, assign(CalendarEntry(EntryKind.BIRTHDAY, "Anna", 0, day)))

    @Test fun `birthday calendar entries use their calendar`() =
        assertEquals(ChipPattern.STRIPES, assign(CalendarEntry(EntryKind.BIRTHDAY, "Ben", 0, day, calendarId = 7)))

    @Test fun `line style comes from the calendar`() {
        val e = PatternAssigner.assign(listOf(CalendarEntry(EntryKind.TIMED, "Standup", 0, day, calendarId = 7)), global).single()
        assertEquals(LineStyle.DASHED, e.lineStyle)
    }

    @Test fun `calendars without a line style are solid`() {
        val e = PatternAssigner.assign(listOf(CalendarEntry(EntryKind.TIMED, "Review", 0, day, calendarId = 8)), global).single()
        assertEquals(LineStyle.SOLID, e.lineStyle)
    }
}
