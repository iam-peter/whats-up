package app.whatsup.logic

import app.whatsup.config.WidgetConfig
import app.whatsup.model.CalendarEntry
import app.whatsup.model.EntryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class TimelineBuilderTest {
    private val measurer = object : TextMeasurer { override fun textHeightDp(textSp: Float) = textSp * 1.2f }
    private val labels = object : GridLabels {
        override fun weekdayInitial(day: DayOfWeek) = day.name.take(1)
        override fun birthdays(count: Int) = "$count birthdays"
        override fun more(count: Int) = "+$count"
        override fun time(instant: Instant) = instant.atOffset(ZoneOffset.UTC).let { "%d:%02d".format(it.hour, it.minute) }
        override fun dayDescription(date: LocalDate, isToday: Boolean, entries: List<String>, hidden: Int) = "$date"
        override fun birthdayText(name: String, age: Int?) = name
        override fun dayLabel(date: LocalDate, today: LocalDate) = "$date"
        override fun timeRange(start: Instant, end: Instant) = ""
        override fun allDay() = ""
        override fun dateRange(first: LocalDate, last: LocalDate) = ""
        override fun ongoingUntil(end: Instant) = ""
    }
    private val builder = TimelineBuilder(GridModelBuilder(measurer, labels), measurer, labels)
    private val today = LocalDate.of(2026, 9, 24)
    private val now = LocalDateTime.of(2026, 9, 24, 12, 0).toInstant(ZoneOffset.UTC)

    private fun timed(title: String, from: Int, to: Int, day: LocalDate = today) = CalendarEntry(
        EntryKind.TIMED, title, 0, day,
        start = day.atTime(from, 0).toInstant(ZoneOffset.UTC), end = day.atTime(to, 0).toInstant(ZoneOffset.UTC),
    )

    private fun build(entries: List<CalendarEntry>, days: List<LocalDate> = listOf(today), h: Float = 400f) =
        builder.build(entries, today, now, days, 360f, h, WidgetConfig(), zone = ZoneOffset.UTC)

    @Test fun `default hours are 8 to 18 and widen to the events`() {
        assertEquals(8 to 18, build(emptyList()).let { it.startHour to it.endHour })
        assertEquals(6 to 21, build(listOf(timed("Early", 6, 7), timed("Late", 20, 21))).let { it.startHour to it.endHour })
    }

    @Test fun `events are placed by time`() {
        val model = build(listOf(timed("Nine", 9, 10)))
        val e = model.days.single().lanes.single().single()
        assertEquals(model.hourHeightDp, e.topDp, 0.01f)
        assertEquals(model.hourHeightDp, e.heightDp, 0.01f)
    }

    @Test fun `overlapping events get lanes`() {
        val day = build(listOf(timed("A", 9, 11), timed("B", 10, 12), timed("C", 11, 12))).days.single()
        assertEquals(2, day.laneCount)
        assertEquals(listOf("9:00 A", "11:00 C"), day.lanes[0].map { it.chip.description })
        assertEquals(listOf("10:00 B"), day.lanes[1].map { it.chip.description })
    }

    @Test fun `more than three parallel events are counted`() {
        val day = build((0..4).map { timed("E$it", 9, 12) }).days.single()
        assertEquals(3, day.laneCount)
        assertEquals("+2", day.moreText)
    }

    @Test fun `now line only on a shown today`() {
        assertNotNull(build(emptyList()).nowTopDp)
        assertNull(build(emptyList(), days = listOf(today.plusDays(1))).nowTopDp)
    }

    @Test fun `all-day entries go to the all-day row`() {
        val model = build(listOf(CalendarEntry(EntryKind.ALL_DAY, "Trash", 0, today), timed("A", 9, 10)))
        assertEquals(1, model.allDayRows)
        assertEquals("Trash", model.days.single().allDay.single().description)
        assertTrue(model.days.single().lanes.isNotEmpty())
    }
}
