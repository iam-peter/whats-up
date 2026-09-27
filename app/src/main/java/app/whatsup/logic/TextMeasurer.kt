package app.whatsup.logic

/** Platform text measurement, so the layout logic stays JVM-testable. */
interface TextMeasurer {
    /** Height of one line of text as the widget's TextView draws it. */
    fun textHeightDp(textSp: Float): Float
}

/** Localised strings the grid needs. */
interface GridLabels {
    fun weekdayInitial(day: java.time.DayOfWeek): String
    fun birthdays(count: Int): String
    fun more(count: Int): String
    fun time(instant: java.time.Instant): String
    fun dayDescription(date: java.time.LocalDate, isToday: Boolean, entries: List<String>, hidden: Int): String
    fun birthdayText(name: String, age: Int?): String

    /** "Today", "Tomorrow" or a short date, for list headers. */
    fun dayLabel(date: java.time.LocalDate, today: java.time.LocalDate): String
    fun timeRange(start: java.time.Instant, end: java.time.Instant): String
    fun allDay(): String
    fun dateRange(first: java.time.LocalDate, last: java.time.LocalDate): String

    /** "Now, until 15:00" for an event in progress. */
    fun ongoingUntil(end: java.time.Instant): String
}
