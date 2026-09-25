package app.whatsup.logic

import app.whatsup.config.Density
import app.whatsup.config.WidgetConfig
import app.whatsup.model.CalendarEntry
import app.whatsup.model.ChipPattern
import app.whatsup.model.EntryKind
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import kotlin.math.floor

/**
 * Turns entries + widget size into a fully fitted [GridModel]. All layout
 * decisions (bar lanes, clipping, "+N") happen here, before any Glance view
 * is built (spec FR-L4…FR-L6).
 */
class GridModelBuilder(
    private val measurer: TextMeasurer,
    private val labels: GridLabels,
) {
    companion object {
        /** Auto weeks: one week per this many dp of height. */
        const val TARGET_ROW_HEIGHT_DP = 80f

        /** Glance Row/Column hold at most 10 children: header + chips + "+N". */
        const val MAX_CHIPS_PER_CELL = 8

        /** Below this cell width, reduce padding (FR-L6). */
        const val NARROW_CELL_DP = 44f
    }

    /**
     * [cornerRadiusDp] is the launcher's widget corner radius; the outer
     * padding keeps corner cells' content inside the rounded outline (FR-T5).
     */
    fun metrics(cfg: WidgetConfig, narrow: Boolean, cornerRadiusDp: Float = 0f): GridMetrics {
        val comfortable = cfg.density == Density.COMFORTABLE
        val textSp = (if (comfortable) 12f else 11f) * cfg.textScale
        val chipVPadding = if (comfortable) 2f else 1f
        return GridMetrics(
            textSp = textSp,
            lineHeightDp = measurer.textHeightDp(textSp) + 2 * chipVPadding + 1f,
            headerHeightDp = textSp * 1.3f + 2f,
            headerInsetDp = 2f,
            // Compact matches Chronos: chips nearly fill the cell, so titles get the width.
            cellPaddingDp = if (comfortable) 4f else 1f,
            chipHPaddingDp = if (narrow) 1f else if (comfortable) 3f else 2f,
            chipVPaddingDp = chipVPadding,
            gapDp = if (comfortable) 2f else 1f,
            // A corner of radius r intrudes about 0.3 r diagonally.
            outerPaddingDp = maxOf(if (comfortable) 6f else 4f, cornerRadiusDp * 0.3f),
            weekNumberWidthDp = if (cfg.showWeekNumbers) textSp * 1.6f else 0f,
            iconSizeDp = textSp,
            iconGapDp = 2f,
        )
    }

    fun build(
        entries: List<CalendarEntry>,
        today: LocalDate,
        now: Instant,
        firstDayOfWeek: DayOfWeek,
        widthDp: Float,
        heightDp: Float,
        cfg: WidgetConfig,
        cornerRadiusDp: Float = 0f,
    ): GridModel {
        val provisional = metrics(cfg, narrow = false, cornerRadiusDp)
        val weekCount = (cfg.weeks ?: GridRange.autoWeeks(heightDp - 2 * provisional.outerPaddingDp, TARGET_ROW_HEIGHT_DP))
            .coerceIn(1, GridRange.MAX_WEEKS)
        val cellWidth = (widthDp - 2 * provisional.outerPaddingDp - provisional.weekNumberWidthDp) / 7f
        val m = metrics(cfg, narrow = cellWidth < NARROW_CELL_DP, cornerRadiusDp).copy(cellWidthDp = cellWidth)
        val cellHeight = (heightDp - 2 * m.outerPaddingDp) / weekCount
        val contentHeight = cellHeight - 2 * m.gapDp - 2 * m.cellPaddingDp - m.headerInsetDp - m.headerHeightDp
        val capacity = floor(contentHeight / m.lineHeightDp).toInt().coerceAtLeast(0)

        val sorted = entries.sortedWith(entryOrder)
        val weeks = GridRange.days(today, firstDayOfWeek, weekCount).chunked(7).mapIndexed { index, days ->
            buildWeek(days, index == 0, sorted, today, now, capacity, m, cfg)
        }
        return GridModel(weeks, m, cfg.showWeekNumbers)
    }

    private fun buildWeek(
        days: List<LocalDate>,
        firstRow: Boolean,
        entries: List<CalendarEntry>,
        today: LocalDate,
        now: Instant,
        capacity: Int,
        m: GridMetrics,
        cfg: WidgetConfig,
    ): Week {
        val weekStart = days.first()
        val weekEnd = days.last()
        // Multi-day all-day events become bars (FR-E2): longest first within a start day.
        val candidates = entries
            .filter { it.kind == EntryKind.ALL_DAY && it.isMultiDay && !it.lastDay.isBefore(weekStart) && !it.firstDay.isAfter(weekEnd) }
            .sortedWith(compareBy<CalendarEntry>({ maxOf(it.firstDay, weekStart) }, { -it.lastDay.toEpochDay() }))
        val lanes = mutableListOf<MutableList<IntRange>>()
        val bars = mutableListOf<Pair<Int, SpanBar>>()
        val barred = HashSet<CalendarEntry>()
        for (e in candidates) {
            val first = ChronoUnit.DAYS.between(weekStart, maxOf(e.firstDay, weekStart)).toInt()
            val last = ChronoUnit.DAYS.between(weekStart, minOf(e.lastDay, weekEnd)).toInt()
            val cols = first..last
            var lane = lanes.indexOfFirst { used -> used.none { it.first <= cols.last && cols.first <= it.last } }
            if (lane < 0 && lanes.size < capacity) {
                lanes += mutableListOf<IntRange>()
                lane = lanes.lastIndex
            }
            // No lane left: the event falls back to a chip in each of its days.
            if (lane < 0) continue
            lanes[lane] += cols
            barred += e
            bars += lane to SpanBar(
                startColumn = first,
                span = last - first + 1,
                chip = eventChip(e, weekStart.plusDays(first.toLong()), today, now, cfg, m)
                    .copy(dimmed = minOf(e.lastDay, weekEnd).isBefore(today)),
                continuesBefore = e.firstDay.isBefore(weekStart),
                continuesAfter = e.lastDay.isAfter(weekEnd),
            )
        }
        val laneList = lanes.indices.map { i -> bars.filter { it.first == i }.map { it.second }.sortedBy { it.startColumn } }
        val cellCapacity = capacity - lanes.size
        val cells = days.map { day ->
            buildCell(day, firstRow, entries.filter { it.occursOn(day) && it !in barred }, today, now, cellCapacity, m, cfg)
        }
        val weekNumber = if (cfg.showWeekNumbers) weekStart.get(WeekFields.ISO.weekOfWeekBasedYear()) else null
        return Week(cells, laneList, weekNumber)
    }

    private fun buildCell(
        day: LocalDate,
        firstRow: Boolean,
        dayEntries: List<CalendarEntry>,
        today: LocalDate,
        now: Instant,
        capacity: Int,
        m: GridMetrics,
        cfg: WidgetConfig,
    ): DayCell {
        val isPast = day.isBefore(today)
        val candidates = chipCandidates(day, dayEntries, today, now, isPast, m, cfg)
        // Every entry is a single line (FR-L5); what doesn't fit becomes "+N".
        val fit = CellFitter.fit(candidates, capacity, maxLinesPerItem = 1, maxItems = MAX_CHIPS_PER_CELL) { 1 }
        return DayCell(
            date = day,
            isToday = day == today,
            isPast = isPast,
            isWeekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY,
            isNextMonth = day.isAfter(today) && (day.year > today.year || day.monthValue > today.monthValue),
            weekdayLabel = if (firstRow) labels.weekdayInitial(day.dayOfWeek) else null,
            chips = fit.visible.map { it.item },
            moreText = if (fit.hidden > 0) labels.more(fit.hidden) else null,
            description = labels.dayDescription(day, day == today, candidates.map { it.description }, 0),
        )
    }

    private fun chipCandidates(
        day: LocalDate,
        dayEntries: List<CalendarEntry>,
        today: LocalDate,
        now: Instant,
        isPast: Boolean,
        m: GridMetrics,
        cfg: WidgetConfig,
    ): List<Chip> {
        val (birthdays, events) = dayEntries.partition { it.kind == EntryKind.BIRTHDAY }
        val result = mutableListOf<Chip>()
        when (birthdays.size) {
            0 -> Unit
            1 -> birthdays.single().let { b ->
                result += birthdayChip(labels.birthdayText(b.title, b.age), b.color, b.pattern, day, isPast, m, cfg)
            }
            // Spec D-3: several birthdays are always aggregated.
            else -> birthdays.first().let { b ->
                result += birthdayChip(labels.birthdays(birthdays.size), b.color, b.pattern, day, isPast, m, cfg)
            }
        }
        events.mapTo(result) { eventChip(it, day, today, now, cfg, m).copy(dimmed = isPast || it.hasEnded(day, today, now)) }
        return result
    }

    private fun CalendarEntry.hasEnded(day: LocalDate, today: LocalDate, now: Instant) =
        day == today && kind == EntryKind.TIMED && end != null && !end.isAfter(now)

    private fun eventChip(e: CalendarEntry, day: LocalDate, today: LocalDate, now: Instant, cfg: WidgetConfig, m: GridMetrics): Chip {
        val timed = e.kind == EntryKind.TIMED
        // Only the day an event starts on shows its time, and only if enabled (FR-E3).
        val showTime = cfg.showEventTimes && timed && e.start != null && e.firstDay == day
        val text = if (showTime) "${labels.time(e.start)} ${e.title}" else e.title
        return Chip(
            text = Clipping.unbreakable(text),
            description = text,
            color = e.color,
            style = if (timed) ChipStyle.OUTLINED else ChipStyle.FILLED,
            dimmed = day.isBefore(today) || e.hasEnded(day, today, now),
            pattern = e.pattern,
            lineStyle = e.lineStyle,
            // Every tap on the widget opens the day popup (spec D-2, revised in v1.6).
            target = ChipTarget.InAppDay(day),
            textHeightDp = measurer.textHeightDp(m.textSp),
        )
    }

    private fun birthdayChip(
        text: String,
        color: Int,
        pattern: ChipPattern,
        day: LocalDate,
        isPast: Boolean,
        m: GridMetrics,
        cfg: WidgetConfig,
    ) = Chip(
        text = Clipping.unbreakable(text),
        description = text,
        color = color,
        style = ChipStyle.FILLED,
        dimmed = isPast,
        target = ChipTarget.InAppDay(day),
        pattern = pattern,
        textHeightDp = measurer.textHeightDp(m.textSp),
        icon = cfg.showBirthdayIcon,
    )
}
