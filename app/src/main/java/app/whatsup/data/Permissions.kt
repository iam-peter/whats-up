package app.whatsup.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

object Permissions {
    fun hasCalendar(context: Context) = granted(context, Manifest.permission.READ_CALENDAR)
    fun hasContacts(context: Context) = granted(context, Manifest.permission.READ_CONTACTS)

    private fun granted(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
