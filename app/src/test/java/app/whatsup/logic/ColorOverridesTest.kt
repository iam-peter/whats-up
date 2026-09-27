package app.whatsup.logic

import app.whatsup.config.GlobalConfig
import app.whatsup.model.BirthdaySource
import app.whatsup.model.CalendarEntry
import app.whatsup.model.EntryKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ColorOverridesTest {
    private val day = LocalDate.of(2026, 10, 3)
    private val graphite = 0xFF616161.toInt()
    private val global = GlobalConfig(calendarColors = mapOf(2L to graphite))

    private fun color(e: CalendarEntry) = ColorOverrides.apply(listOf(e), global).single().color

    @Test fun `override replaces the calendar colour`() =
        assertEquals(graphite, color(CalendarEntry(EntryKind.ALL_DAY, "Tag der Deutschen Einheit", 1, day, calendarId = 2)))

    @Test fun `other calendars keep their colour`() =
        assertEquals(1, color(CalendarEntry(EntryKind.ALL_DAY, "Trash", 1, day, calendarId = 6)))

    @Test fun `events with their own colour keep it`() =
        assertEquals(1, color(CalendarEntry(EntryKind.TIMED, "Special", 1, day, calendarId = 2, hasOwnColor = true)))

    @Test fun `birthdays are not affected`() =
        assertEquals(1, color(CalendarEntry(EntryKind.BIRTHDAY, "Ben", 1, day, calendarId = 2, birthdaySource = BirthdaySource.GOOGLE)))
}
