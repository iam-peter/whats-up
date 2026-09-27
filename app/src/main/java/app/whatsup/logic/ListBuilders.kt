package app.whatsup.logic

import app.whatsup.config.WidgetConfig
import app.whatsup.model.CalendarEntry
import app.whatsup.model.ChipPattern
import app.whatsup.model.EntryKind
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One line in the agenda or birthday list. */
data class ListItem(
    val title: String,
    /** Time range, "All day" or a date range; null for birthdays. */
    val detail: String?,
    val color: Int,
    val kind: EntryKind,
    val icon: Boolean,
    val dimmed: Boolean,
    val pattern: ChipPattern,
)

data class ListDay(val date: LocalDate, val label: String, val isToday: Boolean, val items: List<ListItem>)

/** Agenda and upcoming-birthday lists (spec section 4.1, `Agenda`, `BirthdayStrip`). */
class ListBuilder(private val labels: GridLabels) {
    fun build(
        entries: List<CalendarEntry>,
        today: LocalDate,
        now: Instant,
        days: Int,
        cfg: WidgetConfig,
        birthdaysOnly: Boolean,
    ): List<ListDay> {
        val sorted = entries.sortedWith(entryOrder)
            .filter { !birthdaysOnly || it.kind == EntryKind.BIRTHDAY }
        return (0 until days).map { today.plusDays(it.toLong()) }.mapNotNull { day ->
            val items = sorted.filter { it.occursOn(day) }.map { item(it, day, today, now, cfg) }
            if (items.isEmpty()) null else ListDay(day, labels.dayLabel(day, today), day == today, items)
        }
    }

    private fun item(e: CalendarEntry, day: LocalDate, today: LocalDate, now: Instant, cfg: WidgetConfig): ListItem {
        val detail = when {
            e.kind == EntryKind.BIRTHDAY -> null
            e.kind == EntryKind.TIMED && e.start != null && e.end != null -> labels.timeRange(e.start, e.end)
            e.isMultiDay -> labels.dateRange(e.firstDay, e.lastDay)
            else -> labels.allDay()
        }
        val ended = day == today && e.kind == EntryKind.TIMED && e.end != null && !e.end.isAfter(now)
        return ListItem(
            title = if (e.kind == EntryKind.BIRTHDAY) labels.birthdayText(e.title, e.age) else e.title,
            detail = detail,
            color = e.color,
            kind = e.kind,
            icon = e.kind == EntryKind.BIRTHDAY && cfg.showBirthdayIcon,
            dimmed = ended,
            pattern = e.pattern,
        )
    }
}

/** An upcoming event for the next-up card. */
data class NextUpItem(
    val title: String,
    val color: Int,
    val date: LocalDate,
    /** "Today 14:00–15:00", "Now, until 15:00", "Mon 28 Sep, all day" … */
    val whenText: String,
    /** Set when the event starts within a day: the card counts down to it. */
    val countdownTo: Instant?,
    val ongoing: Boolean,
)

/** The next events from now (spec section 4.1, `NextUp`). Birthdays have their own layout. */
class NextUpBuilder(private val labels: GridLabels) {
    companion object {
        private val COUNTDOWN_WINDOW: Duration = Duration.ofHours(24)
    }

    fun build(entries: List<CalendarEntry>, today: LocalDate, now: Instant, max: Int, zone: ZoneId = ZoneId.systemDefault()): List<NextUpItem> {
        val timed = entries
            .filter { it.kind == EntryKind.TIMED && it.start != null && it.end != null && it.end.isAfter(now) }
            .distinctBy { Triple(it.eventId, it.start, it.title) }
            .sortedBy { it.start }
        // Without timed events, upcoming all-day events are better than an empty card.
        val chosen = timed.ifEmpty {
            entries.filter { it.kind == EntryKind.ALL_DAY && !it.lastDay.isBefore(today) }
                .distinctBy { it.eventId to it.title }
                .sortedBy { it.firstDay }
        }
        return chosen.take(max).map { e ->
            val start = e.start
            val ongoing = start != null && !start.isAfter(now)
            val date = if (start != null) start.atZone(zone).toLocalDate() else maxOf(e.firstDay, today)
            val whenText = when {
                ongoing -> labels.ongoingUntil(e.end!!)
                start != null -> "${labels.dayLabel(date, today)} ${labels.timeRange(start, e.end!!)}"
                e.isMultiDay -> labels.dateRange(e.firstDay, e.lastDay)
                else -> "${labels.dayLabel(date, today)}, ${labels.allDay()}"
            }
            val countdown = start?.takeIf { !ongoing && Duration.between(now, it) <= COUNTDOWN_WINDOW }
            NextUpItem(e.title, e.color, date, whenText, countdown, ongoing)
        }
    }
}
