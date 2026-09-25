package app.whatsup.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.whatsup.R
import app.whatsup.config.AppConfig
import app.whatsup.config.GlobalConfig
import app.whatsup.config.configStore
import app.whatsup.data.BIRTHDAY_COLOR
import app.whatsup.data.CalendarRepository
import app.whatsup.data.Permissions
import app.whatsup.model.ChipPattern
import app.whatsup.model.LineStyle
import app.whatsup.update.UpdateScheduler
import app.whatsup.update.WidgetUpdater
import app.whatsup.widget.WhatsUpWidgetReceiver
import kotlinx.coroutines.launch

/** Permissions and global settings (calendar default, holidays, birthday sources). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { Surface(Modifier.fillMaxSize()) { MainScreen() } } }
    }
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by context.configStore.data.collectAsState(AppConfig())
    // Bumped after permission results so the screen re-reads permission state.
    var permissionEpoch by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionEpoch++
        scope.launch { WidgetUpdater.refreshAll(context) }
        UpdateScheduler.ensureScheduled(context)
    }
    val hasCalendar = remember(permissionEpoch) { Permissions.hasCalendar(context) }
    val hasContacts = remember(permissionEpoch) { Permissions.hasContacts(context) }
    val calendars = remember(permissionEpoch, config.global.extraHolidayCalendarIds) {
        CalendarRepository(context).calendars(config.global.extraHolidayCalendarIds)
    }

    fun update(block: (GlobalConfig) -> GlobalConfig) = scope.launch {
        context.configStore.updateData { it.copy(global = block(it.global)) }
        WidgetUpdater.refreshAll(context)
    }

    Column(
        Modifier.safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        SectionTitle(stringResource(R.string.permissions))
        if (!hasCalendar || (config.global.birthdaysFromContacts && !hasContacts)) {
            Button(onClick = {
                val wanted = buildList {
                    add(Manifest.permission.READ_CALENDAR)
                    if (config.global.birthdaysFromContacts) add(Manifest.permission.READ_CONTACTS)
                }
                launcher.launch(wanted.toTypedArray())
            }) { Text(stringResource(R.string.grant_access)) }
        } else {
            Text(stringResource(R.string.permissions_ok))
        }
        Button(onClick = {
            AppWidgetManager.getInstance(context)
                .requestPinAppWidget(ComponentName(context, WhatsUpWidgetReceiver::class.java), null, null)
        }, Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.add_widget)) }

        SectionTitle(stringResource(R.string.birthdays))
        SwitchRow(stringResource(R.string.birthdays_from_contacts), config.global.birthdaysFromContacts) { on ->
            update { it.copy(birthdaysFromContacts = on) }
            if (on && !hasContacts) launcher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
        }
        SwitchRow(stringResource(R.string.birthdays_from_calendar), config.global.birthdaysFromCalendar) { on ->
            update { it.copy(birthdaysFromCalendar = on) }
        }

        // FR-D5: Google holiday calendars are recognised; others can be marked by hand.
        val markable = calendars.filter { !it.isBirthdays }
        if (markable.isNotEmpty()) {
            SectionTitle(stringResource(R.string.holiday_calendars))
            markable.forEach { cal ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !cal.autoHoliday) {
                        update {
                            val ids = it.extraHolidayCalendarIds
                            it.copy(extraHolidayCalendarIds = if (cal.id in ids) ids - cal.id else ids + cal.id)
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(cal.isHoliday, onCheckedChange = null, enabled = !cal.autoHoliday, modifier = Modifier.padding(8.dp))
                    Text(cal.name)
                }
            }
        }

        val holidayCalendars = calendars.filter { it.isHoliday }
        if (holidayCalendars.size > 1) {
            SectionTitle(stringResource(R.string.preferred_holiday_calendar))
            val preferred = config.global.preferredHolidayCalendarId ?: holidayCalendars.minOf { it.id }
            holidayCalendars.forEach { cal ->
                Row(Modifier.fillMaxWidth().clickable { update { it.copy(preferredHolidayCalendarId = cal.id) } },
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(cal.id == preferred, onClick = null, modifier = Modifier.padding(8.dp))
                    Text(cal.name)
                }
            }
        }

        if (calendars.isNotEmpty()) {
            SectionTitle(stringResource(R.string.default_calendars))
            CalendarPicker(calendars, config.global.calendarIds) { ids -> update { it.copy(calendarIds = ids) } }
        }

        SectionTitle(stringResource(R.string.patterns))
        if (config.global.birthdaysFromContacts) {
            CalendarStyleRow(stringResource(R.string.contact_birthdays), null, Color(BIRTHDAY_COLOR),
                config.global.contactBirthdayPattern, { p -> update { it.copy(contactBirthdayPattern = p) } },
                line = null)
        }
        calendars.forEach { cal ->
            CalendarStyleRow(
                cal.name, cal.account, Color(if (cal.isBirthdays) BIRTHDAY_COLOR else cal.color),
                fill = config.global.calendarPatterns[cal.id] ?: ChipPattern.NONE,
                onFill = { p ->
                    update { it.copy(calendarPatterns = it.calendarPatterns.withDefault(cal.id, p, ChipPattern.NONE)) }
                },
                // Birthday calendars only have all-day entries, so no outlined chips.
                line = if (cal.isBirthdays) null else config.global.calendarLineStyles[cal.id] ?: LineStyle.SOLID,
                onLine = { l ->
                    update { it.copy(calendarLineStyles = it.calendarLineStyles.withDefault(cal.id, l, LineStyle.SOLID)) }
                },
            )
        }
    }
}

/** Stores [value] for [key], dropping the entry when it is the [default]. */
private fun <V> Map<Long, V>.withDefault(key: Long, value: V, default: V) =
    if (value == default) this - key else this + (key to value)
