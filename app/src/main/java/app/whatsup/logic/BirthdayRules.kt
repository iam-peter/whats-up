package app.whatsup.logic

import java.time.LocalDate
import java.time.Year

data class Birthday(
    val name: String,
    val month: Int,
    val day: Int,
    val birthYear: Int?,
    /** Contact lookup key, used to de-duplicate raw contacts of one person. */
    val key: String,
)

object BirthdayRules {
    /** Apple devices store year-less birthdays as 1604. */
    private const val NO_YEAR_SENTINEL = 1604

    /**
     * Parses the formats found in `ContactsContract.CommonDataKinds.Event.START_DATE`:
     * `yyyy-MM-dd`, `--MM-dd`, `yyyyMMdd` and ISO date-times.
     */
    fun parse(name: String, raw: String, key: String): Birthday? {
        val s = raw.trim()
        val (year, month, day) = when {
            s.startsWith("--") && s.length >= 7 ->
                Triple(null, s.substring(2, 4).toIntOrNull(), s.substring(5, 7).toIntOrNull())
            s.length >= 10 && s[4] == '-' ->
                Triple(s.substring(0, 4).toIntOrNull(), s.substring(5, 7).toIntOrNull(), s.substring(8, 10).toIntOrNull())
            s.length == 8 && s.all(Char::isDigit) ->
                Triple(s.substring(0, 4).toInt(), s.substring(4, 6).toInt(), s.substring(6, 8).toInt())
            else -> return null
        }
        if (month == null || day == null || month !in 1..12 || day !in 1..31) return null
        val knownYear = year?.takeIf { it != NO_YEAR_SENTINEL }
        return Birthday(name, month, day, knownYear, key)
    }

    /** 29 February is shown on 28 February in non-leap years (FR-B3). */
    fun occurrence(b: Birthday, year: Int): LocalDate =
        if (b.month == 2 && b.day == 29 && !Year.isLeap(year.toLong())) LocalDate.of(year, 2, 28)
        else LocalDate.of(year, b.month, b.day)

    fun age(b: Birthday, occurrence: LocalDate): Int? =
        b.birthYear?.let { occurrence.year - it }?.takeIf { it >= 0 }

    fun occurrencesIn(b: Birthday, from: LocalDate, to: LocalDate): List<LocalDate> =
        (from.year..to.year).map { occurrence(b, it) }.filter { !it.isBefore(from) && !it.isAfter(to) }
}
