package app.whatsup.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.whatsup.R
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

/** In-app day view, opened from "+N" and aggregated birthdays (spec D-2, D-3). */
class DayActivity : ComponentActivity() {
    companion object {
        const val EXTRA_DATE = "date"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val date = intent.getStringExtra(EXTRA_DATE)?.let(LocalDate::parse) ?: LocalDate.now()
        setContent { AppTheme { Surface(Modifier.fillMaxSize()) { DayScreen(date) } } }
    }
}

@Composable
private fun DayScreen(date: LocalDate) {
    val context = LocalContext.current
    val labels = remember { AndroidLabels(context) }
    var entries by remember { mutableStateOf<List<CalendarEntry>>(emptyList()) }
    LaunchedEffect(date) {
        val config = context.configStore.data.first()
        entries = withContext(Dispatchers.IO) {
            EntryLoader(context).load(date, date, config, config.global.calendarIds).sortedWith(entryOrder)
        }
    }
    Column(Modifier.safeDrawingPadding().padding(16.dp)) {
        Text(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(date), style = MaterialTheme.typography.headlineSmall)
        LazyColumn(Modifier.padding(top = 12.dp)) {
            items(entries) { e ->
                val subtitle = when (e.kind) {
                    EntryKind.BIRTHDAY, EntryKind.ALL_DAY -> ""
                    // Agenda-style views show start–end (Q-25).
                    EntryKind.TIMED -> listOfNotNull(e.start, e.end).joinToString("–") { labels.time(it) }
                }
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = e.eventId != null) {
                        context.startActivity(Intents.forTarget(context, ChipTarget.Event(e.eventId!!, e.start, e.end), null))
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
        }
    }
}
