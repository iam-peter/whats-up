package app.whatsup.logic

import app.whatsup.config.WidgetConfig
import app.whatsup.model.CalendarEntry
import app.whatsup.model.ChipPattern
import app.whatsup.model.EntryKind
import app.whatsup.model.LineStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.ceil

class GridModelBuilderTest {
    /** Monospace approximation: each char is 0.6 × text size wide. */
    private val measurer = object : TextMeasurer {
        private fun perLine(widthDp: Float, textSp: Float) = (widthDp / (textSp * 0.6f)).toInt().coerceAtLeast(1)
        override fun lineCount(text: String, widthDp: Float, textSp: Float) =
            ceil(text.length / perLine(widthDp, textSp).toFloat()).toInt().coerceAtLeast(1)
        override fun textHeightDp(lines: Int, textSp: Float) = lines * textSp * 1.2f
        override fun clip(text: String, maxLines: Int, widthDp: Float, textSp: Float) =
            text.take(perLine(widthDp, textSp) * maxLines)
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

    private fun timed(title: String, hour: Int, day: LocalDate = today) = CalendarEntry(
        EntryKind.TIMED, title, 0, day,
        start = day.atTime(hour, 0).toInstant(ZoneOffset.UTC),
        end = day.atTime(hour + 1, 0).toInstant(ZoneOffset.UTC),
        eventId = hour.toLong(),
    )

    @Test fun `rolling grid starts on monday of current week`() {
        val model = build(emptyList())
        assertEquals(2, model.weeks.size)
        assertEquals(LocalDate.of(2026, 9, 21), model.weeks[0][0].date)
        assertEquals("M", model.weeks[0][0].weekdayLabel)
        assertEquals(null, model.weeks[1][0].weekdayLabel)
    }

    @Test fun `minimum size gives one week`() {
        assertEquals(1, build(emptyList(), w = 170f, h = 40f).weeks.size)
    }

    @Test fun `weeks override wins`() {
        assertEquals(4, build(emptyList(), cfg = WidgetConfig(weeks = 4)).weeks.size)
    }

    @Test fun `timed events show start time and ended ones are dimmed`() {
        val cell = build(listOf(timed("Standup", 9), timed("Review", 15))).weeks[0][3]
        assertTrue(cell.isToday)
        assertEquals(listOf(true, false), cell.chips.map { it.dimmed })
        assertTrue(cell.chips[0].text.startsWith("9:00"))
        assertEquals(ChipStyle.OUTLINED, cell.chips[0].style)
    }

    @Test fun `several birthdays are aggregated`() {
        val b = listOf("Anna", "Ben").map { CalendarEntry(EntryKind.BIRTHDAY, it, 0, today) }
        val chips = build(b, w = 800f).weeks[0][3].chips
        assertEquals(1, chips.size)
        assertEquals("2 birthdays", chips[0].text)
        assertEquals(ChipTarget.InAppDay(today), chips[0].target)
    }

    private fun birthday(name: String) = CalendarEntry(EntryKind.BIRTHDAY, name, 0, today)

    @Test fun `birthday icon follows the setting`() {
        val on = build(listOf(birthday("Anna")), w = 800f).weeks[0][3].chips.single()
        val off = build(listOf(birthday("Anna")), w = 800f, cfg = WidgetConfig(showBirthdayIcon = false)).weeks[0][3].chips.single()
        assertEquals(true, on.icon)
        assertEquals(false, off.icon)
        assertEquals("Anna", on.text)
    }

    @Test fun `icon narrows the text instead of disappearing`() {
        // 380 dp, one line: six characters fit without the icon, fewer beside it.
        val long = "Alexander"
        val with = build(listOf(birthday(long)), w = 380f, h = 60f).weeks[0][3].chips.single()
        val without = build(listOf(birthday(long)), w = 380f, h = 60f, cfg = WidgetConfig(showBirthdayIcon = false))
            .weeks[0][3].chips.single()
        assertTrue(with.icon)
        assertTrue(with.text.length < without.text.length)
        assertTrue(long.startsWith(with.text))
    }

    @Test fun `text is clipped, never ellipsised`() {
        val chip = build(listOf(timed("A rather long meeting title", 15)), w = 380f, h = 60f).weeks[0][3].chips.single()
        assertTrue("15:00 A rather long meeting title".startsWith(chip.text))
        assertTrue(!chip.text.contains("…"))
        assertEquals("15:00 A rather long meeting title", chip.description)
    }

    @Test fun `overflow yields plus N and nothing is longer than allowed`() {
        val many = (8..20).map { timed("A rather long meeting title $it", it) }
        val cell = build(many).weeks[0][3]
        assertTrue(cell.moreText!!.startsWith("+"))
        assertEquals(many.size, cell.chips.size + cell.moreText.removePrefix("+").toInt())
        cell.chips.forEach { assertTrue(it.maxLines in 1..2) }
    }

    @Test fun `past days are dimmed`() {
        val cell = build(listOf(CalendarEntry(EntryKind.ALL_DAY, "Trash", 0, today.minusDays(2)))).weeks[0][1]
        assertTrue(cell.isPast)
        assertTrue(cell.chips.single().dimmed)
    }
}
