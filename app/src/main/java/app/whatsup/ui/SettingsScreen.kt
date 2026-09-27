package app.whatsup.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import app.whatsup.R
import app.whatsup.config.AppConfig
import app.whatsup.config.BackgroundStyle
import app.whatsup.config.CONTACT_BIRTHDAY_COLOR
import app.whatsup.config.Density
import app.whatsup.config.GlobalConfig
import app.whatsup.config.WeekendStyle
import app.whatsup.config.WidgetConfig
import app.whatsup.config.WidgetLayout
import app.whatsup.config.configStore
import app.whatsup.data.CalendarInfo
import app.whatsup.data.CalendarRepository
import app.whatsup.data.Permissions
import app.whatsup.logic.GooglePalette
import app.whatsup.model.ChipPattern
import app.whatsup.model.LineStyle
import app.whatsup.update.UpdateScheduler
import app.whatsup.update.WidgetUpdater
import app.whatsup.widget.CalendarApp
import app.whatsup.widget.Intents
import app.whatsup.widget.WhatsUpWidget
import app.whatsup.widget.WhatsUpWidgetReceiver
import app.whatsup.widget.WidgetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The one settings screen, opened from the app icon and from a widget's
 * "Widget settings" (FR-C1). It shows the selected widget's preview and
 * settings, then the settings all widgets share. Every change applies at
 * once, so there is no Save button.
 */
@Composable
fun SettingsScreen(initialWidgetId: Int?, onDone: (() -> Unit)?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by context.configStore.data.collectAsState(null)
    // Bumped after permission results so the screen re-reads permission state.
    var permissionEpoch by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionEpoch++
        scope.launch { WidgetUpdater.refreshAll(context) }
        UpdateScheduler.ensureScheduled(context)
    }
    val widgetIds = remember(permissionEpoch) {
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, WhatsUpWidgetReceiver::class.java)).toList()
    }
    var selected by remember { mutableStateOf(initialWidgetId ?: widgetIds.firstOrNull()) }
    val cfgAll = config ?: return
    val hasCalendar = remember(permissionEpoch) { Permissions.hasCalendar(context) }
    val hasContacts = remember(permissionEpoch) { Permissions.hasContacts(context) }
    val calendars = remember(permissionEpoch, cfgAll.global.extraHolidayCalendarIds) {
        CalendarRepository(context).calendars(cfgAll.global.extraHolidayCalendarIds)
    }

    fun updateGlobal(block: (GlobalConfig) -> GlobalConfig) = scope.launch {
        context.configStore.updateData { it.copy(global = block(it.global)) }
        WidgetUpdater.refreshAll(context)
    }
    fun updateWidget(id: Int, block: (WidgetConfig) -> WidgetConfig) = scope.launch {
        context.configStore.updateData { it.copy(widgets = it.widgets + (id to block(it.widget(id)))) }
        WhatsUpWidget().update(context, GlanceAppWidgetManager(context).getGlanceIdBy(id))
        UpdateScheduler.ensureScheduled(context)
    }

    Column(Modifier.safeDrawingPadding().padding(16.dp)) {
        selected?.let { id -> PreviewBox(cfgAll, id) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (!hasCalendar || (cfgAll.global.birthdaysFromContacts && !hasContacts)) {
                Button(onClick = {
                    launcher.launch(buildList {
                        add(Manifest.permission.READ_CALENDAR)
                        if (cfgAll.global.birthdaysFromContacts) add(Manifest.permission.READ_CONTACTS)
                    }.toTypedArray())
                }, Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.grant_access)) }
            }

            val id = selected
            if (id == null) {
                Text(stringResource(R.string.no_widget_yet), Modifier.padding(top = 16.dp))
            } else {
                if (widgetIds.size > 1) {
                    SectionTitle(stringResource(R.string.widget))
                    val names = widgetIds.mapIndexed { i, w -> w to stringResource(R.string.widget_n, i + 1, layoutName(cfgAll.widget(w).layout)) }.toMap()
                    StyleDropdown(stringResource(R.string.editing), widgetIds, id, { selected = it }, Modifier.fillMaxWidth(),
                        name = { names.getValue(it) })
                }
                WidgetSection(cfgAll, cfgAll.widget(id), calendars) { block -> updateWidget(id, block) }
            }

            CalendarsSection(cfgAll.global, calendars) { updateGlobal(it) }
            BirthdaysSection(cfgAll.global, hasContacts, onNeedContacts = { launcher.launch(arrayOf(Manifest.permission.READ_CONTACTS)) }) { updateGlobal(it) }
            HolidaysSection(cfgAll.global, calendars) { updateGlobal(it) }

            OutlinedButton(onClick = {
                AppWidgetManager.getInstance(context)
                    .requestPinAppWidget(ComponentName(context, WhatsUpWidgetReceiver::class.java), null, null)
            }, Modifier.padding(top = 24.dp)) { Text(stringResource(if (widgetIds.isEmpty()) R.string.add_widget else R.string.add_another_widget)) }
        }
        onDone?.let { Button(onClick = it, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.done)) } }
    }
}

@Composable
private fun PreviewBox(config: AppConfig, appWidgetId: Int) {
    val context = LocalContext.current
    val size = remember(appWidgetId) { widgetSize(context, appWidgetId) }
    var preview by remember { mutableStateOf<WidgetState?>(null) }
    LaunchedEffect(config, appWidgetId) {
        preview = withContext(Dispatchers.IO) { WhatsUpWidget.loadState(context, config, appWidgetId).copy(isPreview = true) }
    }
    Box(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        preview?.let { WidgetPreview(it, size) }
    }
}

@Composable
private fun layoutName(layout: WidgetLayout) = stringResource(
    when (layout) {
        WidgetLayout.ROLLING_GRID -> R.string.layout_rolling
        WidgetLayout.MONTH_GRID -> R.string.layout_month
        WidgetLayout.AGENDA -> R.string.layout_agenda
        WidgetLayout.BIRTHDAYS -> R.string.layout_birthdays
        WidgetLayout.NEXT_UP -> R.string.layout_next_up
        WidgetLayout.WEEK_TIMELINE -> R.string.layout_week_timeline
        WidgetLayout.DAY_TIMELINE -> R.string.layout_day_timeline
        WidgetLayout.GRID_AGENDA -> R.string.layout_grid_agenda
        WidgetLayout.NEXT_UP_BIRTHDAYS -> R.string.layout_next_up_birthdays
    },
)

/** Settings of one widget. */
@Composable
private fun WidgetSection(config: AppConfig, cfg: WidgetConfig, calendars: List<CalendarInfo>, update: (WidgetConfig.() -> WidgetConfig) -> Unit) {
    val context = LocalContext.current
    SectionTitle(stringResource(R.string.layout))
    StyleDropdown(stringResource(R.string.layout), WidgetLayout.entries, cfg.layout, { l -> update { copy(layout = l) } },
        Modifier.fillMaxWidth(), name = { layoutName(it) })
    when (cfg.layout) {
        WidgetLayout.ROLLING_GRID -> {
            SectionTitle(stringResource(R.string.weeks))
            ChoiceRow(listOf<Pair<Int?, String>>(null to stringResource(R.string.auto)) + (1..6).map { it to it.toString() }, cfg.weeks) { w ->
                update { copy(weeks = w) }
            }
        }
        WidgetLayout.MONTH_GRID, WidgetLayout.WEEK_TIMELINE, WidgetLayout.DAY_TIMELINE -> Unit
        else -> {
            SectionTitle(stringResource(R.string.days_ahead))
            ChoiceRow(listOf(7, 14, 30, 60).map { it to it.toString() }, cfg.lookAheadDays) { d -> update { copy(lookAheadDays = d) } }
        }
    }

    SectionTitle(stringResource(R.string.text_size))
    // Saved when the slider is released, not on every step.
    var scale by remember(cfg.textScale) { mutableFloatStateOf(cfg.textScale) }
    Slider(scale, { scale = it }, valueRange = 0.8f..1.4f, steps = 5, onValueChangeFinished = { update { copy(textScale = scale) } })

    SectionTitle(stringResource(R.string.density))
    ChoiceRow(listOf(Density.COMPACT to stringResource(R.string.compact), Density.COMFORTABLE to stringResource(R.string.comfortable)), cfg.density) { d ->
        update { copy(density = d) }
    }

    SectionTitle(stringResource(R.string.background))
    ChoiceRow(listOf(BackgroundStyle.PER_CELL to stringResource(R.string.per_cell), BackgroundStyle.SINGLE to stringResource(R.string.single)), cfg.background) { b ->
        update { copy(background = b) }
    }
    if (cfg.background == BackgroundStyle.SINGLE) {
        var opacity by remember(cfg.backgroundOpacity) { mutableFloatStateOf(cfg.backgroundOpacity) }
        Slider(opacity, { opacity = it }, onValueChangeFinished = { update { copy(backgroundOpacity = opacity) } })
    }

    SectionTitle(stringResource(R.string.appearance))
    SwitchRow(stringResource(R.string.dynamic_colors), cfg.dynamicColors) { v -> update { copy(dynamicColors = v) } }
    if (!cfg.dynamicColors) {
        Text(stringResource(R.string.accent_color), Modifier.padding(vertical = 6.dp))
        ColorChoice(ACCENT_COLORS, cfg.accentColor) { c -> update { copy(accentColor = c) } }
    }
    SwitchRow(stringResource(R.string.tint_weekends), cfg.weekendStyle == WeekendStyle.TINTED) { v ->
        update { copy(weekendStyle = if (v) WeekendStyle.TINTED else WeekendStyle.NONE) }
    }
    SwitchRow(stringResource(R.string.week_numbers), cfg.showWeekNumbers) { v -> update { copy(showWeekNumbers = v) } }
    SwitchRow(stringResource(R.string.birthday_icon), cfg.showBirthdayIcon) { v -> update { copy(showBirthdayIcon = v) } }
    SwitchRow(stringResource(R.string.event_times), cfg.showEventTimes) { v -> update { copy(showEventTimes = v) } }
    SwitchRow(stringResource(R.string.popup_next_to_day), cfg.popupNextToDay) { v -> update { copy(popupNextToDay = v) } }

    if (calendars.isNotEmpty()) {
        SwitchRow(stringResource(R.string.use_default_calendars), cfg.calendarIds == null) { useDefault ->
            update { copy(calendarIds = if (useDefault) null else config.global.calendarIds ?: calendars.map { it.id }.toSet()) }
        }
        cfg.calendarIds?.let { ids ->
            CalendarPicker(calendars, ids) { picked -> update { copy(calendarIds = picked ?: calendars.map { c -> c.id }.toSet()) } }
        }
    }
    val calendarApps = remember { Intents.calendarApps(context) }
    val systemDefault = stringResource(R.string.system_default)
    StyleDropdown(
        stringResource(R.string.calendar_app), listOf<CalendarApp?>(null) + calendarApps,
        calendarApps.firstOrNull { it.packageName == cfg.calendarPackage }, { app -> update { copy(calendarPackage = app?.packageName) } },
        Modifier.fillMaxWidth().padding(top = 8.dp), name = { it?.label ?: systemDefault },
    )
}

/** Calendars, shared by all widgets: shown by default, colour, fill and outline (FR-D2, FR-E4a, FR-E6). */
@Composable
private fun CalendarsSection(global: GlobalConfig, calendars: List<CalendarInfo>, update: (GlobalConfig.() -> GlobalConfig) -> Unit) {
    val shown = calendars.filterNot { it.isBirthdays }
    if (shown.isEmpty()) return
    SectionTitle(stringResource(R.string.calendars_all_widgets))
    Text(stringResource(R.string.calendars_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    shown.forEach { cal ->
        val included = global.calendarIds == null || cal.id in global.calendarIds
        CalendarStyleRow(
            cal.name, cal.account, Color(global.calendarColors[cal.id] ?: cal.color),
            fill = global.calendarPatterns[cal.id] ?: ChipPattern.NONE,
            onFill = { p -> update { copy(calendarPatterns = calendarPatterns.withDefault(cal.id, p, ChipPattern.NONE)) } },
            line = global.calendarLineStyles[cal.id] ?: LineStyle.SOLID,
            onLine = { l -> update { copy(calendarLineStyles = calendarLineStyles.withDefault(cal.id, l, LineStyle.SOLID)) } },
            sourceColor = cal.color,
            onColor = { c -> update { copy(calendarColors = if (c == null) calendarColors - cal.id else calendarColors + (cal.id to c)) } },
            included = included,
            onIncluded = { on ->
                update {
                    val all = calendars.map { it.id }.toSet()
                    val current = calendarIds ?: all
                    val next = if (on) current + cal.id else current - cal.id
                    copy(calendarIds = if (next.containsAll(all)) null else next)
                }
            },
        )
    }
}

@Composable
private fun BirthdaysSection(
    global: GlobalConfig,
    hasContacts: Boolean,
    onNeedContacts: () -> Unit,
    update: (GlobalConfig.() -> GlobalConfig) -> Unit,
) {
    SectionTitle(stringResource(R.string.birthdays))
    SwitchRow(stringResource(R.string.birthdays_from_contacts), global.birthdaysFromContacts) { on ->
        update { copy(birthdaysFromContacts = on) }
        if (on && !hasContacts) onNeedContacts()
    }
    if (global.birthdaysFromContacts) {
        CalendarStyleRow(stringResource(R.string.contact_birthdays), null, Color(global.contactBirthdayColor),
            global.contactBirthdayPattern, { p -> update { copy(contactBirthdayPattern = p) } }, line = null,
            sourceColor = CONTACT_BIRTHDAY_COLOR, onColor = { c -> update { copy(contactBirthdayColor = c ?: CONTACT_BIRTHDAY_COLOR) } },
            palette = BIRTHDAY_COLORS)
    }
    SwitchRow(stringResource(R.string.birthdays_from_calendar), global.birthdaysFromCalendar) { on -> update { copy(birthdaysFromCalendar = on) } }
    if (global.birthdaysFromCalendar) {
        CalendarStyleRow(stringResource(R.string.google_birthdays), null, Color(global.googleBirthdayColor),
            global.googleBirthdayPattern, { p -> update { copy(googleBirthdayPattern = p) } }, line = null,
            sourceColor = GooglePalette.BIRTHDAY, onColor = { c -> update { copy(googleBirthdayColor = c ?: GooglePalette.BIRTHDAY) } },
            palette = BIRTHDAY_COLORS)
    }
}

/** FR-D5: Google holiday calendars are recognised; others can be marked by hand. */
@Composable
private fun HolidaysSection(global: GlobalConfig, calendars: List<CalendarInfo>, update: (GlobalConfig.() -> GlobalConfig) -> Unit) {
    val markable = calendars.filter { !it.isBirthdays }
    if (markable.isEmpty()) return
    SectionTitle(stringResource(R.string.holiday_calendars))
    markable.forEach { cal ->
        Row(
            Modifier.fillMaxWidth().clickable(enabled = !cal.autoHoliday) {
                update { copy(extraHolidayCalendarIds = if (cal.id in extraHolidayCalendarIds) extraHolidayCalendarIds - cal.id else extraHolidayCalendarIds + cal.id) }
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(cal.isHoliday, onCheckedChange = null, enabled = !cal.autoHoliday, modifier = Modifier.padding(8.dp))
            Text(cal.name)
        }
    }
    val holidayCalendars = calendars.filter { it.isHoliday }
    if (holidayCalendars.size > 1) {
        Text(stringResource(R.string.preferred_holiday_calendar), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge)
        val preferred = global.preferredHolidayCalendarId ?: holidayCalendars.minOf { it.id }
        holidayCalendars.forEach { cal ->
            Row(Modifier.fillMaxWidth().clickable { update { copy(preferredHolidayCalendarId = cal.id) } }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(cal.id == preferred, onClick = null, modifier = Modifier.padding(8.dp))
                Text(cal.name)
            }
        }
    }
}

/** Stores [value] for [key], dropping the entry when it is the [default]. */
private fun <V> Map<Long, V>.withDefault(key: Long, value: V, default: V) =
    if (value == default) this - key else this + (key to value)

/** Google Calendar's event colours plus the contacts pink. */
private val BIRTHDAY_COLORS = (listOf(CONTACT_BIRTHDAY_COLOR) + GooglePalette.eventColors).distinct()
