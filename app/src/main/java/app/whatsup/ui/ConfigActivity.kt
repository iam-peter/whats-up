package app.whatsup.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import app.whatsup.R
import app.whatsup.config.AppConfig
import app.whatsup.config.BackgroundStyle
import app.whatsup.config.Density
import app.whatsup.config.WeekendStyle
import app.whatsup.config.WidgetConfig
import app.whatsup.config.configStore
import app.whatsup.data.CalendarRepository
import app.whatsup.update.UpdateScheduler
import app.whatsup.widget.WhatsUpWidget
import app.whatsup.widget.WidgetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Per-widget configuration with live preview (FR-C1, FR-C2). */
class ConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, result)
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize()) {
                    ConfigScreen(appWidgetId) {
                        setResult(RESULT_OK, result)
                        finish()
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigScreen(appWidgetId: Int, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var appConfig by remember { mutableStateOf<AppConfig?>(null) }
    var cfg by remember { mutableStateOf(WidgetConfig()) }
    var preview by remember { mutableStateOf<WidgetState?>(null) }
    val calendars = remember { CalendarRepository(context).calendars() }
    val size = remember { widgetSize(context, appWidgetId) }

    LaunchedEffect(Unit) {
        val loaded = context.configStore.data.first()
        appConfig = loaded
        cfg = loaded.widget(appWidgetId)
    }
    LaunchedEffect(appConfig, cfg) {
        val base = appConfig ?: return@LaunchedEffect
        val withDraft = base.copy(widgets = base.widgets + (appWidgetId to cfg))
        preview = withContext(Dispatchers.IO) { WhatsUpWidget.loadState(context, withDraft, appWidgetId) }
    }

    Column(Modifier.safeDrawingPadding().padding(16.dp)) {
        Box(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            preview?.let { WidgetPreview(it, size) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionTitle(stringResource(R.string.weeks))
            ChoiceRow(listOf<Pair<Int?, String>>(null to stringResource(R.string.auto)) + (1..6).map { it to it.toString() },
                cfg.weeks) { cfg = cfg.copy(weeks = it) }

            SectionTitle(stringResource(R.string.text_size))
            Slider(cfg.textScale, { cfg = cfg.copy(textScale = it) }, valueRange = 0.8f..1.4f, steps = 5)

            SectionTitle(stringResource(R.string.density))
            ChoiceRow(listOf(Density.COMPACT to stringResource(R.string.compact), Density.COMFORTABLE to stringResource(R.string.comfortable)),
                cfg.density) { cfg = cfg.copy(density = it) }

            SectionTitle(stringResource(R.string.background))
            ChoiceRow(listOf(BackgroundStyle.PER_CELL to stringResource(R.string.per_cell), BackgroundStyle.SINGLE to stringResource(R.string.single)),
                cfg.background) { cfg = cfg.copy(background = it) }
            if (cfg.background == BackgroundStyle.SINGLE) {
                Slider(cfg.backgroundOpacity, { cfg = cfg.copy(backgroundOpacity = it) })
            }

            SectionTitle(stringResource(R.string.appearance))
            SwitchRow(stringResource(R.string.dynamic_colors), cfg.dynamicColors) { cfg = cfg.copy(dynamicColors = it) }
            SwitchRow(stringResource(R.string.tint_weekends), cfg.weekendStyle == WeekendStyle.TINTED) {
                cfg = cfg.copy(weekendStyle = if (it) WeekendStyle.TINTED else WeekendStyle.NONE)
            }
            SwitchRow(stringResource(R.string.week_numbers), cfg.showWeekNumbers) { cfg = cfg.copy(showWeekNumbers = it) }
            SwitchRow(stringResource(R.string.birthday_icon), cfg.showBirthdayIcon) { cfg = cfg.copy(showBirthdayIcon = it) }

            if (calendars.isNotEmpty()) {
                SectionTitle(stringResource(R.string.calendars))
                SwitchRow(stringResource(R.string.use_default_calendars), cfg.calendarIds == null) { useDefault ->
                    cfg = cfg.copy(calendarIds = if (useDefault) null else appConfig?.global?.calendarIds ?: calendars.map { it.id }.toSet())
                }
                cfg.calendarIds?.let { ids ->
                    CalendarPicker(calendars, ids) { cfg = cfg.copy(calendarIds = it ?: calendars.map { c -> c.id }.toSet()) }
                }
            }
            // TODO(M1): calendar app picker (Q-41) via queryIntentActivities on the calendar VIEW intent.
        }
        Button(onClick = {
            scope.launch {
                context.configStore.updateData { it.copy(widgets = it.widgets + (appWidgetId to cfg)) }
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                WhatsUpWidget().update(context, glanceId)
                UpdateScheduler.ensureScheduled(context)
                onDone()
            }
        }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
    }
}
