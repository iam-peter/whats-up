package app.whatsup.logic

import app.whatsup.config.WidgetConfig
import app.whatsup.model.CalendarEntry
import app.whatsup.model.ChipPattern
import app.whatsup.model.EntryKind
import app.whatsup.model.LineStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class GridModelBuilderTest {
    private val measurer = object : TextMeasurer {
        override fun textHeightDp(textSp: Float) = textSp * 1.2f
    }
    private val labels = object : GridLabels {
        override fun weekdayInitial(day: DayOfWeek) = day.name.take(1)
        override fun birthdays(count: Int) = "$count birthdays"
        override fun more(count: Int) = "+$count"
        override fun time(instant: Instant) = instant.atOffset(ZoneOffset.UTC).let { "%d:%02d".format(it.hour, it.minute) }
        override fun dayDescription(date: LocalDate, isToday: Boolean, entries: List<String>, hidden: Int) = "$date"
        override fun birthdayText(name: String, age: Int?) = if (age != null) "$name ($age)" else name
    }
    private val builder = GridModelBuilder(measurer, labels)
    private val today = LocalDate.of(2026, 9, 24) // Thursday
    private val now = LocalDateTime.of(2026, 9, 24, 12, 0).toInstant(ZoneOffset.UTC)

    private fun build(entries: List<CalendarEntry>, w: Float = 400f, h: Float = 180f, cfg: WidgetConfig = WidgetConfig()) =
        builder.build(entries, today, now, DayOfWeek.MONDAY, w, h, cfg)

    private fun GridModel.day(week: Int, column: Int) = weeks[week].days[column]

    private fun timed(title: String, hour: Int, day: LocalDate = today) = CalendarEntry(
        EntryKind.TIMED, title, 0, day,
        start = day.atTime(hour, 0).toInstant(ZoneOffset.UTC),
        end = day.atTime(hour + 1, 0).toInstant(ZoneOffset.UTC),
        eventId = hour.toLong(),
    )

    private fun allDay(title: String, first: LocalDate, last: LocalDate = first) =
        CalendarEntry(EntryKind.ALL_DAY, title, 0, first, last, eventId = title.hashCode().toLong())

    private fun birthday(name: String) = CalendarEntry(EntryKind.BIRTHDAY, name, 0, today)

    @Test fun `rolling grid starts on monday of current week`() {
        val model = build(emptyList())
        assertEquals(2, model.weeks.size)
        assertEquals(LocalDate.of(2026, 9, 21), model.day(0, 0).date)
        assertEquals("M", model.day(0, 0).weekdayLabel)
        assertNull(model.day(1, 0).weekdayLabel)
    }

    @Test fun `minimum size gives one week`() = assertEquals(1, build(emptyList(), w = 170f, h = 40f).weeks.size)

    @Test fun `weeks override wins`() = assertEquals(4, build(emptyList(), cfg = WidgetConfig(weeks = 4)).weeks.size)

    @Test fun `timed events show start time and ended ones are dimmed`() {
        val cell = build(listOf(timed("Standup", 9), timed("Review", 15))).day(0, 3)
        assertTrue(cell.isToday)
        assertEquals(listOf(true, false), cell.chips.map { it.dimmed })
        assertEquals("9:00 Standup", cell.chips[0].description)
        assertEquals(ChipStyle.OUTLINED, cell.chips[0].style)
    }

    @Test fun `event times can be turned off`() {
        val chip = build(listOf(timed("Standup", 9)), cfg = WidgetConfig(showEventTimes = false)).day(0, 3).chips.single()
        assertEquals("Standup", chip.description)
    }

    @Test fun `entries are single-line and clipped, never ellipsised`() {
        val chip = build(listOf(timed("A rather long meeting title", 15))).day(0, 3).chips.single()
        assertEquals("15:00 A rather long meeting title", chip.text)
        assertFalse(chip.text.contains("…"))
    }

    @Test fun `several birthdays are aggregated`() {
        val chips = build(listOf(birthday("Anna"), birthday("Ben"))).day(0, 3).chips
        assertEquals(1, chips.size)
        assertEquals("2 birthdays", chips[0].description)
        assertEquals(ChipTarget.InAppDay(today), chips[0].target)
    }

    @Test fun `birthday icon follows the setting`() {
        assertTrue(build(listOf(birthday("Anna"))).day(0, 3).chips.single().icon)
        assertFalse(build(listOf(birthday("Anna")), cfg = WidgetConfig(showBirthdayIcon = false)).day(0, 3).chips.single().icon)
    }

    @Test fun `patterns and line styles reach the chips`() {
        val entries = listOf(
            birthday("Anna").copy(pattern = ChipPattern.DOTS),
            birthday("Ben").copy(pattern = ChipPattern.GRID),
            timed("Standup", 9).copy(pattern = ChipPattern.STRIPES, lineStyle = LineStyle.DOTTED),
        )
        val chips = build(entries, w = 800f).day(0, 3).chips
        assertEquals(listOf(ChipPattern.DOTS, ChipPattern.STRIPES), chips.map { it.pattern })
        assertEquals(LineStyle.DOTTED, chips[1].lineStyle)
    }

    @Test fun `overflow yields plus N`() {
        val many = (8..20).map { timed("Meeting $it", it) }
        val cell = build(many).day(0, 3)
        assertEquals(many.size, cell.chips.size + cell.moreText!!.removePrefix("+").toInt())
    }

    @Test fun `past days are dimmed`() {
        val cell = build(listOf(allDay("Trash", today.minusDays(2)))).day(0, 1)
        assertTrue(cell.isPast)
        assertTrue(cell.chips.single().dimmed)
    }

    @Test fun `multi-day event becomes one bar per week`() {
        // Friday 25 September to Tuesday 29 September crosses the week boundary.
        val trip = allDay("Trip", LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 29))
        val model = build(listOf(trip))
        val first = model.weeks[0].lanes.single().single()
        assertEquals(4, first.startColumn)
        assertEquals(3, first.span)
        assertFalse(first.continuesBefore)
        assertTrue(first.continuesAfter)
        val second = model.weeks[1].lanes.single().single()
        assertEquals(0, second.startColumn)
        assertEquals(2, second.span)
        assertTrue(second.continuesBefore)
        // Not repeated as chips in the covered days.
        assertTrue((4..6).all { model.day(0, it).chips.isEmpty() })
    }

    @Test fun `overlapping bars get separate lanes, others share one`() {
        val a = allDay("A", LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 23))
        val b = allDay("B", LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 24))
        val c = allDay("C", LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26))
        val lanes = build(listOf(a, b, c), h = 400f).weeks[0].lanes
        assertEquals(2, lanes.size)
        assertEquals(listOf("A", "C"), lanes[0].map { it.chip.description })
        assertEquals(listOf("B"), lanes[1].map { it.chip.description })
    }

    @Test fun `bars take entry lines from every day of the week`() {
        val trip = allDay("Trip", LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 22))
        val many = (8..20).map { timed("Meeting $it", it) }
        val without = build(many).day(0, 3).chips.size
        val with = build(many + trip).day(0, 3).chips.size
        assertEquals(without - 1, with)
    }

    @Test fun `past bars are dimmed`() {
        val past = allDay("Past", LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 22))
        assertTrue(build(listOf(past)).weeks[0].lanes.single().single().chip.dimmed)
    }
}
