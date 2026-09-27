package app.whatsup.logic

import app.whatsup.model.CalendarEntry
import app.whatsup.model.EntryKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DedupTest {
    private val oct3 = LocalDate.of(2026, 10, 3)

    private fun event(title: String, calendarId: Long, day: LocalDate = oct3) =
        CalendarEntry(EntryKind.ALL_DAY, title, 0, day, calendarId = calendarId)

    @Test fun `the same event in two calendars is shown once`() {
        val entries = listOf(event("Tag der Deutschen Einheit", 9), event("tag der deutschen einheit!", 5), event("Trash", 9))
        val kept = EventDeduplicator.apply(entries)
        assertEquals(listOf(5L, 9L), kept.map { it.calendarId })
        assertEquals(2, kept.size)
    }

    @Test fun `different days or times are not duplicates`() {
        val entries = listOf(event("Standup", 1), event("Standup", 2, oct3.plusDays(1)))
        assertEquals(2, EventDeduplicator.apply(entries).size)
    }

    @Test fun `hiding duplicate birthdays keeps the contact`() {
        val contact = CalendarEntry(EntryKind.BIRTHDAY, "Anna Muster", 0, oct3, age = 40)
        val calendar = listOf(
            CalendarEntry(EntryKind.BIRTHDAY, "Anna Muster's birthday", 0, oct3),
            CalendarEntry(EntryKind.BIRTHDAY, "Ben", 0, oct3),
        )
        assertEquals(listOf("Anna Muster", "Ben"), BirthdayMerger.merge(listOf(contact), calendar).map { it.title })
    }
}
