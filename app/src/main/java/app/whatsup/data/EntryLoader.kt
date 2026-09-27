package app.whatsup.data

import android.content.Context
import app.whatsup.config.AppConfig
import app.whatsup.logic.BirthdayRules
import app.whatsup.logic.BirthdayMerger
import app.whatsup.logic.ColorOverrides
import app.whatsup.logic.HolidayDeduplicator
import app.whatsup.logic.PatternAssigner
import app.whatsup.model.BirthdaySource
import app.whatsup.model.CalendarEntry
import app.whatsup.model.EntryKind
import java.time.LocalDate
import java.time.ZoneId

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
        val (calendarBirthdays, events) = calendars.instances(from, to, calendarIds, zone, global.extraHolidayCalendarIds)
            .partition { it.kind == EntryKind.BIRTHDAY }
        val contactBirthdays = if (global.birthdaysFromContacts) {
            contacts.birthdays().flatMap { b ->
                BirthdayRules.occurrencesIn(b, from, to).map { day ->
                    CalendarEntry(
                        EntryKind.BIRTHDAY, b.name, global.contactBirthdayColor, day,
                        age = BirthdayRules.age(b, day), birthdaySource = BirthdaySource.CONTACTS,
                    )
                }
            }
        } else emptyList()
        val fromCalendar = if (global.birthdaysFromCalendar) calendarBirthdays.map { it.copy(color = global.googleBirthdayColor) } else emptyList()
        // Both sources stay visible unless the user hides duplicates (FR-B1).
        val birthdays = if (global.hideDuplicateBirthdays) BirthdayMerger.merge(contactBirthdays, fromCalendar) else contactBirthdays + fromCalendar
        val holidays = if (global.hideDuplicateHolidays) HolidayDeduplicator.apply(events, global.preferredHolidayCalendarId) else events
        val styled = ColorOverrides.apply(holidays, global)
        return PatternAssigner.assign(styled + birthdays, global)
    }
}
