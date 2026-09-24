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
    /** Laid out for display: may contain line breaks and no-break spaces. */
    val text: String,
    /** The plain text, for accessibility. */
    val description: String,
    val maxLines: Int,
    val color: Int,
    val style: ChipStyle,
    val dimmed: Boolean,
    val target: ChipTarget,
    val pattern: ChipPattern = ChipPattern.NONE,
    val lineStyle: LineStyle = LineStyle.SOLID,
    /**
     * Exact height of the text: the view is limited by height, not by a
     * line count, because a line limit makes Android add "…".
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
    /** Only set in the first row (Chronos behaviour). */
    val weekdayLabel: String?,
    val chips: List<Chip>,
    val moreText: String?,
    val description: String,
)

data class GridMetrics(
    val textSp: Float,
    /** One fitter line: a single-line chip including padding and spacing. */
    val lineHeightDp: Float,
    val headerHeightDp: Float,
    val cellPaddingDp: Float,
    val chipHPaddingDp: Float,
    val chipVPaddingDp: Float,
    val gapDp: Float,
    val outerPaddingDp: Float,
    val weekNumberWidthDp: Float,
    val iconSizeDp: Float,
    val iconGapDp: Float,
)

data class GridModel(
    val weeks: List<List<DayCell>>,
    /** ISO week numbers per row, or null when hidden. */
    val weekNumbers: List<Int>?,
    val metrics: GridMetrics,
)
