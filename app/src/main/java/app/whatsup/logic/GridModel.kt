package app.whatsup.logic

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
    val text: String,
    val maxLines: Int,
    val color: Int,
    val style: ChipStyle,
    val dimmed: Boolean,
    val target: ChipTarget,
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
    val lineHeightDp: Float,
    val headerHeightDp: Float,
    val cellPaddingDp: Float,
    val chipHPaddingDp: Float,
    val chipVPaddingDp: Float,
    val gapDp: Float,
    val outerPaddingDp: Float,
    val weekNumberWidthDp: Float,
    val showIcons: Boolean,
)

data class GridModel(
    val weeks: List<List<DayCell>>,
    /** ISO week numbers per row, or null when hidden. */
    val weekNumbers: List<Int>?,
    val metrics: GridMetrics,
)
