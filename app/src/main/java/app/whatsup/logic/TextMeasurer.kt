package app.whatsup.logic

/** Platform text measurement, so the fitting logic stays JVM-testable. */
interface TextMeasurer {
    fun lineCount(text: String, widthDp: Float, textSp: Float): Int

    /** Returns [text] shortened with "…" so it fits in [maxLines]. */
    fun ellipsize(text: String, maxLines: Int, widthDp: Float, textSp: Float): String
}

/** Localised strings the grid needs. */
interface GridLabels {
    fun weekdayInitial(day: java.time.DayOfWeek): String
    fun birthdays(count: Int): String
    fun more(count: Int): String
    fun time(instant: java.time.Instant): String
    fun dayDescription(date: java.time.LocalDate, isToday: Boolean, entries: List<String>, hidden: Int): String
    fun birthdayText(name: String, age: Int?): String
}
