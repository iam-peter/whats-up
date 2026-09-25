package app.whatsup.ui

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.whatsup.R
import app.whatsup.config.AppConfig
import app.whatsup.config.configStore
import app.whatsup.data.EntryLoader
import app.whatsup.logic.ChipTarget
import app.whatsup.logic.entryOrder
import app.whatsup.model.CalendarEntry
import app.whatsup.model.EntryKind
import app.whatsup.widget.AndroidLabels
import app.whatsup.widget.Intents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Day popup over the home screen, opened by every tap on the widget (spec
 * D-2, revised in v1.6). Swiping moves to the previous or next day.
 */
class DayActivity : ComponentActivity() {
    companion object {
        const val EXTRA_DATE = "date"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val date = intent.getStringExtra(EXTRA_DATE)?.let(LocalDate::parse) ?: LocalDate.now()
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setContent { AppTheme { DayPopup(date, appWidgetId, onDismiss = ::finish) } }
    }
}

/** Pages either side of the start day; far more than anyone swipes. */
private const val PAGE_RANGE = 10_000

@Composable
private fun DayPopup(start: LocalDate, appWidgetId: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var config by remember { mutableStateOf<AppConfig?>(null) }
    LaunchedEffect(Unit) { config = context.configStore.data.first() }
    val pager = rememberPagerState(initialPage = PAGE_RANGE) { 2 * PAGE_RANGE + 1 }
    val noRipple = remember { MutableInteractionSource() }

    // Tapping the dimmed area around the card closes the popup.
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(noRipple, indication = null, onClick = onDismiss)
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.7f).dp)
                // Taps on the card must not reach the scrim.
                .clickable(noRipple, indication = null) {},
        ) {
            val cfg = config ?: return@Surface
            HorizontalPager(pager) { page ->
                DayPage(start.plusDays((page - PAGE_RANGE).toLong()), appWidgetId, cfg, onDismiss)
            }
        }
    }
}

@Composable
private fun DayPage(date: LocalDate, appWidgetId: Int, config: AppConfig, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val labels = remember { AndroidLabels(context) }
    val calendarPackage = config.widget(appWidgetId).calendarPackage
    val entries by produceState<List<CalendarEntry>?>(null, date) {
        value = withContext(Dispatchers.IO) {
            // The widget's own calendar selection, if it was opened from one.
            EntryLoader(context).load(date, date, config, config.calendarIdsFor(appWidgetId)).sortedWith(entryOrder)
        }
    }
    val today = LocalDate.now()
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(date), style = MaterialTheme.typography.titleLarge)
                val relative = when (date) {
                    today -> stringResource(R.string.today_title)
                    today.plusDays(1) -> stringResource(R.string.tomorrow)
                    today.minusDays(1) -> stringResource(R.string.yesterday)
                    else -> null
                }
                relative?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            TextButton(onClick = {
                context.startActivity(Intents.calendarDay(context, date, calendarPackage))
                onDismiss()
            }) { Text(stringResource(R.string.open_calendar)) }
        }
        val list = entries
        when {
            list == null -> Unit
            list.isEmpty() -> Text(
                stringResource(R.string.no_entries),
                Modifier.padding(vertical = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(list) { e -> EntryRow(e, labels, calendarPackage, onDismiss) }
            }
        }
    }
}

@Composable
private fun EntryRow(e: CalendarEntry, labels: AndroidLabels, calendarPackage: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val dayFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val subtitle = when {
        // The day view shows start–end (Q-25).
        e.kind == EntryKind.TIMED -> listOfNotNull(e.start, e.end).joinToString("–") { labels.time(it) }
        e.isMultiDay -> "${dayFormat.format(e.firstDay)} – ${dayFormat.format(e.lastDay)}"
        else -> ""
    }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = e.eventId != null) {
            context.startActivity(Intents.forTarget(context, ChipTarget.Event(e.eventId!!, e.start, e.end), calendarPackage))
            onDismiss()
        }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 4.dp, height = 32.dp).background(Color(e.color), RoundedCornerShape(2.dp)))
        Column(Modifier.padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (e.kind == EntryKind.BIRTHDAY) {
                    Icon(painterResource(R.drawable.ic_cake), contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp).size(16.dp), tint = Color(e.color))
                }
                Text(if (e.kind == EntryKind.BIRTHDAY) labels.birthdayText(e.title, e.age) else e.title)
            }
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
