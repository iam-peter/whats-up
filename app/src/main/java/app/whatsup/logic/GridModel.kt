package app.whatsup.logic

import app.whatsup.model.ChipPattern
import app.whatsup.model.LineStyle
import java.time.Instant
import java.time.LocalDate

enum class ChipStyle { FILLED, OUTLINED }

sealed interface ChipTarget {
    data class Event(val eventId: Long, val begin: Instant?, val end: Instant?) : ChipTarget
    data class CalendarDay(val date: LocalDate) : ChipTarget

    /** Spec D-2: "+N" and aggregated birthdays open the in-app day view. */
    data class InAppDay(val date: LocalDate) : ChipTarget
}

data class Chip(
    /** Laid out for display: spaces are no-break, so the line is clipped at the edge. */
    val text: String,
    /** The plain text, for accessibility. */
    val description: String,
    val color: Int,
    val style: ChipStyle,
    val dimmed: Boolean,
    val target: ChipTarget,
    val pattern: ChipPattern = ChipPattern.NONE,
    val lineStyle: LineStyle = LineStyle.SOLID,
    /**
     * Exact height of the single text line: the view is limited by height,
     * not by maxLines, because a line limit makes Android add "…".
     */
    val textHeightDp: Float = 0f,
    /** Show the cake icon beside the text (birthdays, if enabled). */
    val icon: Boolean = false,
)

data class DayCell(
    val date: LocalDate,
    val isToday: Boolean,
    val isPast: Boolean,
    val isWeekend: Boolean,
    /** In a month after today's: drawn without a cell background, like Chronos. */
    val isNextMonth: Boolean,
    /** Only set in the first row (Chronos behaviour). */
    val weekdayLabel: String?,
    val chips: List<Chip>,
    val moreText: String?,
    val description: String,
)

/** A multi-day event drawn as one bar across the days it covers in a week (FR-E2). */
data class SpanBar(
    /** Day column (0–6) where the bar starts in this week. */
    val startColumn: Int,
    val span: Int,
    val chip: Chip,
    /** The event started before this week or ends after it: square that end. */
    val continuesBefore: Boolean,
    val continuesAfter: Boolean,
)

data class Week(
    val days: List<DayCell>,
    /** Bar lanes above the day entries; each lane holds non-overlapping bars. */
    val lanes: List<List<SpanBar>>,
    /** ISO week number, or null when hidden. */
    val weekNumber: Int?,
)

data class GridMetrics(
    val textSp: Float,
    /** One entry line: a chip including padding and spacing. */
    val lineHeightDp: Float,
    val headerHeightDp: Float,
    /** Extra inset of the day letter and number, so the today border doesn't touch them. */
    val headerInsetDp: Float,
    val cellPaddingDp: Float,
    val chipHPaddingDp: Float,
    val chipVPaddingDp: Float,
    val gapDp: Float,
    val outerPaddingDp: Float,
    val weekNumberWidthDp: Float,
    val iconSizeDp: Float,
    val iconGapDp: Float,
    /** Width of one day column; bars are sized in multiples of it. */
    val cellWidthDp: Float = 0f,
)

data class GridModel(
    val weeks: List<Week>,
    val metrics: GridMetrics,
    val showWeekNumbers: Boolean,
)
