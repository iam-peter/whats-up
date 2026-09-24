package app.whatsup.logic

import app.whatsup.model.CalendarEntry
import app.whatsup.model.EntryKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DedupTest {
    private val oct3 = LocalDate.of(2026, 10, 3)

    private fun holiday(title: String, calendarId: Long, day: LocalDate = oct3) =
        CalendarEntry(EntryKind.ALL_DAY, title, 0, day, calendarId = calendarId, isHoliday = true)

    @Test fun `preferred holiday calendar wins across languages`() {
        val entries = listOf(holiday("Day of German Unity", 5), holiday("Tag der Deutschen Einheit", 9))
        assertEquals(listOf("Tag der Deutschen Einheit"), HolidayDeduplicator.apply(entries, 9).map { it.title })
    }

    @Test fun `lowest id is preferred by default`() {
        val entries = listOf(holiday("Tag der Deutschen Einheit", 9), holiday("Day of German Unity", 5))
        assertEquals(listOf("Day of German Unity"), HolidayDeduplicator.apply(entries, null).map { it.title })
    }

    @Test fun `other holiday calendars stay on days the preferred one is empty`() {
        val entries = listOf(holiday("A", 5), holiday("Only in B", 9, oct3.plusDays(1)))
        assertEquals(2, HolidayDeduplicator.apply(entries, 5).size)
    }

    @Test fun `identical titles are merged and normal events untouched`() {
        val normal = CalendarEntry(EntryKind.ALL_DAY, "Tag der Deutschen Einheit", 0, oct3, calendarId = 1)
        val entries = listOf(holiday("Tag der Deutschen Einheit", 5), holiday("tag der deutschen einheit!", 5), normal)
        assertEquals(2, HolidayDeduplicator.apply(entries, null).size)
    }

    @Test fun `contact birthdays replace birthday calendar entries`() {
        val contact = CalendarEntry(EntryKind.BIRTHDAY, "Anna Muster", 0, oct3, age = 40)
        val calendar = listOf(
            CalendarEntry(EntryKind.BIRTHDAY, "Anna Muster's birthday", 0, oct3),
            CalendarEntry(EntryKind.BIRTHDAY, "Ben", 0, oct3),
        )
        assertEquals(listOf("Anna Muster", "Ben"), BirthdayMerger.merge(listOf(contact), calendar).map { it.title })
    }
}
