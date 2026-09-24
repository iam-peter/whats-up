package app.whatsup.logic

import app.whatsup.config.Density
import app.whatsup.config.WidgetConfig
import app.whatsup.model.CalendarEntry
import app.whatsup.model.ChipPattern
import app.whatsup.model.EntryKind
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.WeekFields
import kotlin.math.floor

/**
 * Turns entries + widget size into a fully fitted [GridModel]. All text
 * decisions (wrapping, clipping, "+N") happen here, before any Glance view
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

        /** Safety margin against rounding differences between measuring and rendering. */
        private const val WIDTH_SLACK_DP = 1f
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
            lineHeightDp = measurer.textHeightDp(1, textSp) + 2 * chipVPadding + 1f,
            headerHeightDp = textSp * 1.3f + 2f,
            cellPaddingDp = if (narrow) 1f else if (comfortable) 4f else 2f,
            chipHPaddingDp = if (narrow) 1f else 3f,
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
        val weeks = (cfg.weeks ?: GridRange.autoWeeks(heightDp - 2 * provisional.outerPaddingDp, TARGET_ROW_HEIGHT_DP))
            .coerceIn(1, GridRange.MAX_WEEKS)
        val cellWidth = (widthDp - 2 * provisional.outerPaddingDp - provisional.weekNumberWidthDp) / 7f
        val m = metrics(cfg, narrow = cellWidth < NARROW_CELL_DP, cornerRadiusDp)
        val cellHeight = (heightDp - 2 * m.outerPaddingDp) / weeks
        val contentHeight = cellHeight - 2 * m.gapDp - 2 * m.cellPaddingDp - m.headerHeightDp
        val capacity = floor(contentHeight / m.lineHeightDp).toInt().coerceAtLeast(0)
        val textWidth = cellWidth - 2 * m.gapDp - 2 * m.cellPaddingDp - 2 * m.chipHPaddingDp - WIDTH_SLACK_DP

        val days = GridRange.days(today, firstDayOfWeek, weeks)
        val sorted = entries.sortedWith(entryOrder)
        val cells = days.mapIndexed { index, day ->
            buildCell(day, index < 7, sorted.filter { it.occursOn(day) }, today, now, capacity, textWidth, m, cfg.showBirthdayIcon)
        }
        val weekNumbers = if (cfg.showWeekNumbers) {
            days.chunked(7).map { it.first().get(WeekFields.ISO.weekOfWeekBasedYear()) }
        } else null
        return GridModel(cells.chunked(7), weekNumbers, m)
    }

    private fun buildCell(
        day: LocalDate,
        firstRow: Boolean,
        dayEntries: List<CalendarEntry>,
        today: LocalDate,
        now: Instant,
        capacity: Int,
        textWidth: Float,
        m: GridMetrics,
        showBirthdayIcon: Boolean,
    ): DayCell {
        val isPast = day.isBefore(today)
        val candidates = chipCandidates(day, dayEntries, today, now, isPast, showBirthdayIcon)
        // The icon sits beside the text, so it narrows the text column.
        fun widthFor(chip: Chip) = if (chip.icon) textWidth - m.iconSizeDp - m.iconGapDp else textWidth
        val fit = CellFitter.fit(candidates, capacity, maxLinesPerItem = 2, maxItems = MAX_CHIPS_PER_CELL) {
            measurer.lineCount(it.text, widthFor(it), m.textSp)
        }
        val chips = fit.visible.map { (chip, lines) ->
            chip.copy(
                text = measurer.clip(chip.text, lines, widthFor(chip), m.textSp),
                maxLines = lines,
                textHeightDp = measurer.textHeightDp(lines, m.textSp),
            )
        }
        return DayCell(
            date = day,
            isToday = day == today,
            isPast = isPast,
            isWeekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY,
            weekdayLabel = if (firstRow) labels.weekdayInitial(day.dayOfWeek) else null,
            chips = chips,
            moreText = if (fit.hidden > 0) labels.more(fit.hidden) else null,
            description = labels.dayDescription(day, day == today, candidates.map { it.text }, 0),
        )
    }

    private fun chipCandidates(
        day: LocalDate,
        dayEntries: List<CalendarEntry>,
        today: LocalDate,
        now: Instant,
        isPast: Boolean,
        showBirthdayIcon: Boolean,
    ): List<Chip> {
        val (birthdays, events) = dayEntries.partition { it.kind == EntryKind.BIRTHDAY }
        val result = mutableListOf<Chip>()
        when (birthdays.size) {
            0 -> Unit
            1 -> birthdays.single().let { b ->
                result += birthdayChip(labels.birthdayText(b.title, b.age), b.color, b.pattern, day, isPast, showBirthdayIcon)
            }
            // Spec D-3: several birthdays are always aggregated.
            else -> birthdays.first().let { b ->
                result += birthdayChip(labels.birthdays(birthdays.size), b.color, b.pattern, day, isPast, showBirthdayIcon)
            }
        }
        for (e in events) {
            val timed = e.kind == EntryKind.TIMED
            val ended = day == today && timed && e.end != null && !e.end.isAfter(now)
            // Only the day an event starts on shows its time.
            val showTime = timed && e.start != null && e.firstDay == day
            val text = if (showTime) "${labels.time(e.start)} ${e.title}" else e.title
            result += Chip(
                text = text,
                description = text,
                maxLines = 1,
                color = e.color,
                style = if (timed) ChipStyle.OUTLINED else ChipStyle.FILLED,
                dimmed = isPast || ended,
                pattern = e.pattern,
                lineStyle = e.lineStyle,
                target = e.eventId?.let { ChipTarget.Event(it, e.start, e.end) } ?: ChipTarget.CalendarDay(day),
            )
        }
        return result
    }

    private fun birthdayChip(
        text: String,
        color: Int,
        pattern: ChipPattern,
        day: LocalDate,
        isPast: Boolean,
        showIcon: Boolean,
    ) = Chip(
        text = text,
        description = text,
        maxLines = 1,
        color = color,
        style = ChipStyle.FILLED,
        dimmed = isPast,
        target = ChipTarget.InAppDay(day),
        pattern = pattern,
        icon = showIcon,
    )
}
