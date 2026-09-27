package app.whatsup.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.BackHandler
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

/** Settings pages: the main list, and a page per calendar or birthday source. */
private sealed interface Page {
    data object Main : Page
    data class Calendar(val id: Long) : Page
    data object ContactBirthdays : Page
    data object GoogleBirthdays : Page
}

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
    val calendars = remember(permissionEpoch) { CalendarRepository(context).calendars() }
    var page by remember { mutableStateOf<Page>(Page.Main) }
    BackHandler(enabled = page != Page.Main) { page = Page.Main }

    fun updateGlobal(block: GlobalConfig.() -> GlobalConfig) = scope.launch {
        context.configStore.updateData { it.copy(global = it.global.block()) }
        WidgetUpdater.refreshAll(context)
    }
    fun updateWidget(id: Int, block: (WidgetConfig) -> WidgetConfig) = scope.launch {
        context.configStore.updateData { it.copy(widgets = it.widgets + (id to block(it.widget(id)))) }
        WhatsUpWidget().update(context, GlanceAppWidgetManager(context).getGlanceIdBy(id))
        UpdateScheduler.ensureScheduled(context)
    }

    val global = cfgAll.global
    when (val p = page) {
        Page.Main -> Unit
        is Page.Calendar -> {
            val cal = calendars.firstOrNull { it.id == p.id } ?: run { page = Page.Main; return }
            StylePage(
                title = cal.name, subtitle = cal.account,
                color = global.calendarColors[cal.id] ?: cal.color, sourceColor = cal.color, colors = GooglePalette.calendarColors,
                fill = global.calendarPatterns[cal.id] ?: ChipPattern.NONE,
                line = global.calendarLineStyles[cal.id] ?: LineStyle.SOLID,
                included = global.calendarIds == null || cal.id in global.calendarIds,
                onIncluded = { on -> updateGlobal { toggleCalendar(cal.id, on, calendars) } },
                onColor = { c -> updateGlobal { copy(calendarColors = if (c == null) calendarColors - cal.id else calendarColors + (cal.id to c)) } },
                onFill = { f -> updateGlobal { copy(calendarPatterns = calendarPatterns.withDefault(cal.id, f, ChipPattern.NONE)) } },
                onLine = { l -> updateGlobal { copy(calendarLineStyles = calendarLineStyles.withDefault(cal.id, l, LineStyle.SOLID)) } },
                onBack = { page = Page.Main },
            )
            return
        }
        Page.ContactBirthdays -> {
            StylePage(
                title = stringResource(R.string.contact_birthdays), subtitle = null,
                color = global.contactBirthdayColor, sourceColor = CONTACT_BIRTHDAY_COLOR, colors = BIRTHDAY_COLORS,
                fill = global.contactBirthdayPattern, line = null,
                onColor = { c -> updateGlobal { copy(contactBirthdayColor = c ?: CONTACT_BIRTHDAY_COLOR) } },
                onFill = { f -> updateGlobal { copy(contactBirthdayPattern = f) } },
                onBack = { page = Page.Main },
            )
            return
        }
        Page.GoogleBirthdays -> {
            StylePage(
                title = stringResource(R.string.google_birthdays), subtitle = null,
                color = global.googleBirthdayColor, sourceColor = GooglePalette.BIRTHDAY, colors = BIRTHDAY_COLORS,
                fill = global.googleBirthdayPattern, line = null,
                onColor = { c -> updateGlobal { copy(googleBirthdayColor = c ?: GooglePalette.BIRTHDAY) } },
                onFill = { f -> updateGlobal { copy(googleBirthdayPattern = f) } },
                onBack = { page = Page.Main },
            )
            return
        }
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

            CalendarsSection(cfgAll.global, calendars, onOpen = { page = Page.Calendar(it) }) { updateGlobal(it) }
            BirthdaysSection(
                cfgAll.global, hasContacts,
                onNeedContacts = { launcher.launch(arrayOf(Manifest.permission.READ_CONTACTS)) },
                onOpen = { page = it },
            ) { updateGlobal(it) }

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

/** Calendars, shared by all widgets (FR-D2, FR-E4a, FR-E6): tap a row for its colour and style. */
@Composable
private fun CalendarsSection(global: GlobalConfig, calendars: List<CalendarInfo>, onOpen: (Long) -> Unit, update: (GlobalConfig.() -> GlobalConfig) -> Unit) {
    val shown = calendars.filterNot { it.isBirthdays }
    if (shown.isEmpty()) return
    SectionTitle(stringResource(R.string.calendars_all_widgets))
    Text(stringResource(R.string.calendars_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    shown.forEach { cal ->
        StyleListRow(
            title = cal.name,
            summary = styleSummary(global.calendarPatterns[cal.id] ?: ChipPattern.NONE, global.calendarLineStyles[cal.id] ?: LineStyle.SOLID),
            color = global.calendarColors[cal.id] ?: cal.color,
            included = global.calendarIds == null || cal.id in global.calendarIds,
            onIncluded = { on -> update { toggleCalendar(cal.id, on, calendars) } },
            onClick = { onOpen(cal.id) },
        )
    }
    SwitchRow(stringResource(R.string.hide_duplicate_events), global.hideDuplicateEvents) { on -> update { copy(hideDuplicateEvents = on) } }
}

@Composable
private fun BirthdaysSection(
    global: GlobalConfig,
    hasContacts: Boolean,
    onNeedContacts: () -> Unit,
    onOpen: (Page) -> Unit,
    update: (GlobalConfig.() -> GlobalConfig) -> Unit,
) {
    SectionTitle(stringResource(R.string.birthdays))
    StyleListRow(
        title = stringResource(R.string.contact_birthdays),
        summary = styleSummary(global.contactBirthdayPattern, null),
        color = global.contactBirthdayColor,
        included = global.birthdaysFromContacts,
        onIncluded = { on ->
            update { copy(birthdaysFromContacts = on) }
            if (on && !hasContacts) onNeedContacts()
        },
        onClick = { onOpen(Page.ContactBirthdays) },
    )
    StyleListRow(
        title = stringResource(R.string.google_birthdays),
        summary = styleSummary(global.googleBirthdayPattern, null),
        color = global.googleBirthdayColor,
        included = global.birthdaysFromCalendar,
        onIncluded = { on -> update { copy(birthdaysFromCalendar = on) } },
        onClick = { onOpen(Page.GoogleBirthdays) },
    )
    SwitchRow(stringResource(R.string.birthday_icon), global.showBirthdayIcon) { on -> update { copy(showBirthdayIcon = on) } }
    if (global.birthdaysFromContacts && global.birthdaysFromCalendar) {
        SwitchRow(stringResource(R.string.hide_duplicate_birthdays), global.hideDuplicateBirthdays) { on -> update { copy(hideDuplicateBirthdays = on) } }
    }
}

@Composable
private fun styleSummary(fill: ChipPattern, line: LineStyle?): String =
    listOfNotNull(stringResource(patternName(fill)), line?.let { stringResource(lineName(it)) }).joinToString(" · ")

/** A calendar or birthday source in the list: checkbox, name, style summary, colour. Tap opens its page. */
@Composable
private fun StyleListRow(title: String, summary: String, color: Int, included: Boolean, onIncluded: (Boolean) -> Unit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(included, onCheckedChange = onIncluded)
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.size(24.dp).clip(CircleShape).background(Color(color)))
        Text("›", Modifier.padding(start = 12.dp, end = 4.dp), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A calendar's (or birthday source's) own page: sample, colour, fill and outline. */
@Composable
private fun StylePage(
    title: String,
    subtitle: String?,
    color: Int,
    sourceColor: Int,
    colors: List<Int>,
    fill: ChipPattern,
    line: LineStyle?,
    onColor: (Int?) -> Unit,
    onFill: (ChipPattern) -> Unit,
    onBack: () -> Unit,
    onLine: (LineStyle) -> Unit = {},
    included: Boolean? = null,
    onIncluded: (Boolean) -> Unit = {},
) {
    Column(Modifier.safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState())) {
        TextButton(onClick = onBack) { Text("‹ " + stringResource(R.string.back)) }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        // How entries of this calendar look in the widget.
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val onFilled = if (fill == ChipPattern.NONE) Color.White else MaterialTheme.colorScheme.onSurface
            Text(
                stringResource(R.string.sample_all_day), color = onFilled, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(3.dp)).chipFill(Color(color), fill).padding(horizontal = 8.dp, vertical = 4.dp),
            )
            if (line != null) {
                Text(
                    stringResource(R.string.sample_timed), style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.chipOutline(Color(color), line).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        included?.let { SwitchRow(stringResource(R.string.show_by_default), it, onIncluded) }

        SectionTitle(stringResource(R.string.color))
        ColorChoice(colors, color) { onColor(if (it == sourceColor) null else it) }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(8.dp)).clickable { onColor(null) }.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(sourceColor)))
            Text(stringResource(R.string.reset_calendar_color), Modifier.padding(start = 12.dp))
        }

        SectionTitle(stringResource(R.string.fill))
        ChipPattern.entries.forEach { p ->
            OptionRow(selected = p == fill, onClick = { onFill(p) }, label = stringResource(patternName(p))) { FillSwatch(Color(color), p) }
        }
        if (line != null) {
            SectionTitle(stringResource(R.string.outline))
            LineStyle.entries.forEach { l ->
                OptionRow(selected = l == line, onClick = { onLine(l) }, label = stringResource(lineName(l))) { LineSwatch(Color(color), l) }
            }
        }
    }
}

@Composable
private fun OptionRow(selected: Boolean, onClick: () -> Unit, label: String, swatch: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, modifier = Modifier.padding(end = 12.dp))
        swatch()
        Text(label, Modifier.padding(start = 12.dp))
    }
}

/** Ticks or unticks a calendar in the default selection (null = all). */
private fun GlobalConfig.toggleCalendar(id: Long, on: Boolean, calendars: List<CalendarInfo>): GlobalConfig {
    val all = calendars.map { it.id }.toSet()
    val next = if (on) (calendarIds ?: all) + id else (calendarIds ?: all) - id
    return copy(calendarIds = if (next.containsAll(all)) null else next)
}

/** Stores [value] for [key], dropping the entry when it is the [default]. */
private fun <V> Map<Long, V>.withDefault(key: Long, value: V, default: V) =
    if (value == default) this - key else this + (key to value)

/** Google Calendar's event colours plus the contacts pink. */
private val BIRTHDAY_COLORS = (listOf(CONTACT_BIRTHDAY_COLOR) + GooglePalette.eventColors).distinct()
