package app.whatsup.data

import android.content.Context
import app.whatsup.config.AppConfig
import app.whatsup.logic.BirthdayMerger
import app.whatsup.logic.BirthdayRules
import app.whatsup.logic.HolidayDeduplicator
import app.whatsup.logic.PatternAssigner
import app.whatsup.model.CalendarEntry
import app.whatsup.model.EntryKind
import java.time.LocalDate
import java.time.ZoneId

/** Birthday chips use a fixed pink, like Chronos. */
const val BIRTHDAY_COLOR = 0xFFE91E63.toInt()

class EntryLoader(context: Context) {
    private val calendars = CalendarRepository(context)
    private val contacts = ContactsBirthdayRepository(context)

    fun load(
        from: LocalDate,
        to: LocalDate,
        config: AppConfig,
        calendarIds: Set<Long>?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<CalendarEntry> {
        val global = config.global
        val (calendarBirthdays, events) = calendars.instances(from, to, calendarIds, zone)
            .partition { it.kind == EntryKind.BIRTHDAY }
        val contactBirthdays = if (global.birthdaysFromContacts) {
            contacts.birthdays().flatMap { b ->
                BirthdayRules.occurrencesIn(b, from, to).map { day ->
                    CalendarEntry(EntryKind.BIRTHDAY, b.name, BIRTHDAY_COLOR, day, age = BirthdayRules.age(b, day))
                }
            }
        } else emptyList()
        val fromCalendar = if (global.birthdaysFromCalendar) calendarBirthdays.map { it.copy(color = BIRTHDAY_COLOR) } else emptyList()
        val birthdays = BirthdayMerger.merge(contactBirthdays, fromCalendar)
        return PatternAssigner.assign(HolidayDeduplicator.apply(events, global.preferredHolidayCalendarId) + birthdays, global)
    }
}
