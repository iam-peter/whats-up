package app.whatsup.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object GridRange {
    const val MAX_WEEKS = 6

    /** Rolling grid starting at the first day of the current week (Q-17). */
    fun start(today: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate =
        today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

    fun days(today: LocalDate, firstDayOfWeek: DayOfWeek, weeks: Int): List<LocalDate> {
        val start = start(today, firstDayOfWeek)
        return (0 until weeks * 7).map { start.plusDays(it.toLong()) }
    }

    /** The month grid: whole weeks from the one with the 1st to the one with the last day. */
    fun monthDays(today: LocalDate, firstDayOfWeek: DayOfWeek): List<LocalDate> {
        val first = today.withDayOfMonth(1)
        val last = today.with(TemporalAdjusters.lastDayOfMonth())
        val start = start(first, firstDayOfWeek)
        val end = last.with(TemporalAdjusters.nextOrSame(firstDayOfWeek.minus(1)))
        return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    }

    /** Weeks derived from the height; 40 dp (the minimum) gives one week (FR-L3, FR-L7). */
    fun autoWeeks(heightDp: Float, targetRowHeightDp: Float): Int =
        (heightDp / targetRowHeightDp).toInt().coerceIn(1, MAX_WEEKS)
}
