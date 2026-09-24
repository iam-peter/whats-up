package app.whatsup.data

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.Data
import app.whatsup.logic.Birthday
import app.whatsup.logic.BirthdayRules

class ContactsBirthdayRepository(private val context: Context) {
    /** One birthday per contact (raw contacts are merged by lookup key). */
    fun birthdays(): List<Birthday> {
        if (!Permissions.hasContacts(context)) return emptyList()
        val projection = arrayOf(Data.DISPLAY_NAME, Event.START_DATE, Data.LOOKUP_KEY)
        val selection = "${Data.MIMETYPE} = ? AND ${Event.TYPE} = ?"
        val args = arrayOf(Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString())
        return context.contentResolver.query(Data.CONTENT_URI, projection, selection, args, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val date = c.getString(1) ?: continue
                    BirthdayRules.parse(name, date, c.getString(2) ?: name)?.let(::add)
                }
            }
        }.orEmpty()
            .groupBy { it.key }
            // Prefer the raw contact that knows the birth year.
            .map { (_, list) -> list.firstOrNull { it.birthYear != null } ?: list.first() }
    }
}
