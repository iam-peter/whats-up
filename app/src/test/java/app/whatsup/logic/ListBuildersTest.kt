package app.whatsup.logic

import app.whatsup.config.WidgetConfig
import app.whatsup.model.CalendarEntry
import app.whatsup.model.EntryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class ListBuildersTest {
    private val labels = object : GridLabels {
        override fun weekdayInitial(day: DayOfWeek) = day.name.take(1)
        override fun birthdays(count: Int) = "$count birthdays"
        override fun more(count: Int) = "+$count"
        override fun time(instant: Instant) = instant.atOffset(ZoneOffset.UTC).let { "%d:%02d".format(it.hour, it.minute) }
        override fun dayDescription(date: LocalDate, isToday: Boolean, entries: List<String>, hidden: Int) = "$date"
        override fun birthdayText(name: String, age: Int?) = if (age != null) "$name ($age)" else name
        override fun dayLabel(date: LocalDate, today: LocalDate) = if (date == today) "Today" else "$date"
        override fun timeRange(start: Instant, end: Instant) = "${time(start)}–${time(end)}"
        override fun allDay() = "All day"
        override fun dateRange(first: LocalDate, last: LocalDate) = "$first–$last"
        override fun ongoingUntil(end: Instant) = "Now, until ${time(end)}"
    }
    private val today = LocalDate.of(2026, 9, 25)
    private val now = LocalDateTime.of(2026, 9, 25, 12, 0).toInstant(ZoneOffset.UTC)

    private fun timed(title: String, day: LocalDate, hour: Int) = CalendarEntry(
        EntryKind.TIMED, title, 0, day,
        start = day.atTime(hour, 0).toInstant(ZoneOffset.UTC), end = day.atTime(hour + 1, 0).toInstant(ZoneOffset.UTC),
        eventId = (day.dayOfMonth * 100 + hour).toLong(),
    )

    @Test fun `month grid covers whole weeks around the month`() {
        val days = GridRange.monthDays(today, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 8, 31), days.first())
        assertEquals(LocalDate.of(2026, 10, 4), days.last())
        assertEquals(0, days.size % 7)
    }

    @Test fun `agenda groups by day and skips empty days`() {
        val entries = listOf(timed("A", today, 9), timed("B", today.plusDays(2), 10), CalendarEntry(EntryKind.ALL_DAY, "Trash", 0, today))
        val days = ListBuilder(labels).build(entries, today, now, 7, WidgetConfig(), birthdaysOnly = false)
        assertEquals(listOf(today, today.plusDays(2)), days.map { it.date })
        assertEquals(listOf("Trash", "A"), days[0].items.map { it.title })
        assertEquals("All day", days[0].items[0].detail)
        assertTrue(days[0].items[1].dimmed) // ended at 10:00
    }

    @Test fun `birthday list only has birthdays`() {
        val entries = listOf(timed("A", today, 15), CalendarEntry(EntryKind.BIRTHDAY, "Anna", 0, today.plusDays(3), age = 40))
        val days = ListBuilder(labels).build(entries, today, now, 30, WidgetConfig(), birthdaysOnly = true)
        assertEquals("Anna (40)", days.single().items.single().title)
        assertNull(days.single().items.single().detail)
    }

    @Test fun `next up skips ended events and counts down within a day`() {
        val entries = listOf(timed("Done", today, 9), timed("Soon", today, 14), timed("Later", today.plusDays(3), 9))
        val items = NextUpBuilder(labels).build(entries, today, now, 3, ZoneOffset.UTC)
        assertEquals(listOf("Soon", "Later"), items.map { it.title })
        assertEquals("Today 14:00–15:00", items[0].whenText)
        assertEquals(today.atTime(14, 0).toInstant(ZoneOffset.UTC), items[0].countdownTo)
        assertNull(items[1].countdownTo)
    }

    @Test fun `next up shows events in progress`() {
        // 11:00–13:00, and it's 12:00.
        val meeting = timed("Meeting", today, 11).copy(end = today.atTime(13, 0).toInstant(ZoneOffset.UTC))
        val item = NextUpBuilder(labels).build(listOf(meeting), today, now, 1, ZoneOffset.UTC).single()
        assertTrue(item.ongoing)
        assertEquals("Now, until 13:00", item.whenText)
    }
}
