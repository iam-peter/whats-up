package app.whatsup.logic

import app.whatsup.config.WidgetConfig
import app.whatsup.model.CalendarEntry
import app.whatsup.model.ChipPattern
import app.whatsup.model.EntryKind
import app.whatsup.model.LineStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/** A timed event placed on a day column; positions are in dp from the top of the time area. */
data class TimelineEvent(
    val chip: Chip,
    val lane: Int,
    val topDp: Float,
    val heightDp: Float,
)

data class TimelineDay(
    val date: LocalDate,
    val isToday: Boolean,
    val isPast: Boolean,
    val weekdayLabel: String,
    val allDay: List<Chip>,
    /** Entries that didn't fit (all-day row, lanes), counted in the header. */
    val moreText: String?,
    val laneCount: Int,
    /** Per lane, top to bottom. */
    val lanes: List<List<TimelineEvent>>,
    val description: String,
)

data class TimelineModel(
    val days: List<TimelineDay>,
    val startHour: Int,
    val endHour: Int,
    val hourHeightDp: Float,
    /** Position of the current time in today's column, if it's in range. */
    val nowTopDp: Float?,
    val allDayRows: Int,
    val metrics: GridMetrics,
    val axisWidthDp: Float,
)

/**
 * Week and day timelines (spec section 4.1, `WeekColumns`, `DayTimeline`).
 * Glance has no absolute positioning, so everything is placed here as dp
 * offsets; the view stacks spacers and boxes to match.
 */
class TimelineBuilder(private val grid: GridModelBuilder, private val measurer: TextMeasurer, private val labels: GridLabels) {
    companion object {
        const val DEFAULT_START_HOUR = 8
        const val DEFAULT_END_HOUR = 18
        const val MIN_HOURS = 6

        /** Glance allows 10 children per Row/Column: lanes per day, spacer + box pairs per lane. */
        const val MAX_LANES = 3
        const val MAX_EVENTS_PER_LANE = 4
        const val MAX_ALL_DAY_ROWS = 2
    }

    fun build(
        entries: List<CalendarEntry>,
        today: LocalDate,
        now: Instant,
        days: List<LocalDate>,
        widthDp: Float,
        heightDp: Float,
        cfg: WidgetConfig,
        cornerRadiusDp: Float = 0f,
        zone: ZoneId = ZoneId.systemDefault(),
    ): TimelineModel {
        val m0 = grid.metrics(cfg, narrow = false, cornerRadiusDp)
        val axisWidth = m0.textSp * 2.4f
        val cellWidth = (widthDp - 2 * m0.outerPaddingDp - axisWidth) / days.size
        val m = grid.metrics(cfg, narrow = cellWidth < GridModelBuilder.NARROW_CELL_DP, cornerRadiusDp).copy(cellWidthDp = cellWidth)
        val sorted = entries.sortedWith(entryOrder)
        val timed = sorted.filter { it.kind == EntryKind.TIMED && it.start != null && it.end != null }

        // Hours: default working day, widened to the events shown.
        val shown = timed.filter { e -> days.any { e.occursOn(it) } }
        var startHour = min(DEFAULT_START_HOUR, shown.minOfOrNull { hourOf(it.start!!, zone) } ?: DEFAULT_START_HOUR)
        var endHour = max(DEFAULT_END_HOUR, shown.maxOfOrNull { ceilHourOf(it.end!!, zone) } ?: DEFAULT_END_HOUR).coerceAtMost(24)
        if (endHour - startHour < MIN_HOURS) endHour = min(24, startHour + MIN_HOURS)
        if (endHour - startHour < MIN_HOURS) startHour = max(0, endHour - MIN_HOURS)

        val allDayEntries = days.associateWith { day -> sorted.filter { it.kind != EntryKind.TIMED && it.occursOn(day) } }
        val allDayRows = min(MAX_ALL_DAY_ROWS, allDayEntries.values.maxOfOrNull { it.size } ?: 0)
        val headerHeight = m.headerHeightDp + m.headerInsetDp + 2 * (m.gapDp + m.cellPaddingDp)
        val timeHeight = heightDp - 2 * m.outerPaddingDp - headerHeight - allDayRows * m.lineHeightDp
        val hourHeight = (timeHeight / (endHour - startHour)).coerceAtLeast(1f)
        val minEventHeight = measurer.textHeightDp(m.textSp) + 2 * m.chipVPaddingDp

        fun offset(instant: Instant, day: LocalDate): Float {
            val t = instant.atZone(zone)
            val minutes = if (t.toLocalDate().isBefore(day)) 0 else if (t.toLocalDate().isAfter(day)) 24 * 60 else t.hour * 60 + t.minute
            return ((minutes - startHour * 60).coerceIn(0, (endHour - startHour) * 60)) / 60f * hourHeight
        }

        val result = days.map { day ->
            var hidden = 0
            val allDay = allDayEntries.getValue(day)
            hidden += (allDay.size - allDayRows).coerceAtLeast(0)
            val allDayChips = allDay.take(allDayRows).map { e ->
                val text = if (e.kind == EntryKind.BIRTHDAY) labels.birthdayText(e.title, e.age) else e.title
                chip(e, text, day, today, now, m, cfg)
            }
            // Greedy lanes: each event goes into the first lane whose last event has ended.
            val lanes = mutableListOf<MutableList<TimelineEvent>>()
            val laneEnds = mutableListOf<Float>()
            for (e in timed.filter { it.occursOn(day) }.sortedBy { it.start }) {
                val top = offset(e.start!!, day)
                val height = max(minEventHeight, offset(e.end!!, day) - top)
                var lane = laneEnds.indexOfFirst { it <= top + 0.5f }
                if (lane < 0 && lanes.size < MAX_LANES) {
                    lanes += mutableListOf<TimelineEvent>(); laneEnds += 0f; lane = lanes.lastIndex
                }
                if (lane < 0 || lanes[lane].size >= MAX_EVENTS_PER_LANE) { hidden++; continue }
                // Short events are drawn taller than they last; keep them from overlapping.
                val placedTop = max(top, laneEnds[lane])
                val text = if (cfg.showEventTimes) "${labels.time(e.start)} ${e.title}" else e.title
                lanes[lane] += TimelineEvent(chip(e, text, day, today, now, m, cfg), lane, placedTop, height)
                laneEnds[lane] = placedTop + height
            }
            TimelineDay(
                date = day,
                isToday = day == today,
                isPast = day.isBefore(today),
                weekdayLabel = labels.weekdayInitial(day.dayOfWeek),
                allDay = allDayChips,
                moreText = if (hidden > 0) labels.more(hidden) else null,
                laneCount = lanes.size,
                lanes = lanes,
                description = labels.dayDescription(day, day == today, (allDay + timed.filter { it.occursOn(day) }).map { it.title }, 0),
            )
        }
        val nowMinutes = now.atZone(zone).let { it.hour * 60 + it.minute }
        val nowTop = if (days.contains(today) && nowMinutes in startHour * 60..endHour * 60) (nowMinutes - startHour * 60) / 60f * hourHeight else null
        return TimelineModel(result, startHour, endHour, hourHeight, nowTop, allDayRows, m, axisWidth)
    }

    private fun chip(e: CalendarEntry, text: String, day: LocalDate, today: LocalDate, now: Instant, m: GridMetrics, cfg: WidgetConfig) = Chip(
        text = Clipping.unbreakable(text),
        description = text,
        color = e.color,
        style = if (e.kind == EntryKind.TIMED) ChipStyle.OUTLINED else ChipStyle.FILLED,
        dimmed = day.isBefore(today) || (e.kind == EntryKind.TIMED && e.end != null && !e.end.isAfter(now)),
        target = ChipTarget.InAppDay(day),
        pattern = e.pattern,
        lineStyle = e.lineStyle,
        textHeightDp = measurer.textHeightDp(m.textSp),
        icon = e.kind == EntryKind.BIRTHDAY && cfg.showBirthdayIcon,
    )

    private fun hourOf(i: Instant, zone: ZoneId) = i.atZone(zone).hour
    private fun ceilHourOf(i: Instant, zone: ZoneId) = i.atZone(zone).let { if (it.minute == 0) it.hour.coerceAtLeast(1) else it.hour + 1 }
}
