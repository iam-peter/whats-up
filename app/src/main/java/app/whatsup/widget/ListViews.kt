package app.whatsup.widget

import android.content.Context
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.whatsup.R
import app.whatsup.config.BackgroundStyle
import app.whatsup.logic.ListDay
import app.whatsup.logic.ListItem
import app.whatsup.logic.NextUpItem
import java.time.Instant

/** Panel background for the list layouts: one surface, rounded like the widget. */
@Composable
private fun panelModifier(state: WidgetState, palette: WidgetPalette, root: Boolean): GlanceModifier {
    if (!root) {
        // Inside a combined layout: the outer block has the widget background; per-cell style gets a panel.
        return if (state.config.background == BackgroundStyle.SINGLE) GlanceModifier.fillMaxSize()
        else GlanceModifier.fillMaxSize().padding(4.dp).background(palette.cell).cornerRadius(8.dp)
    }
    val bg = if (state.config.background == BackgroundStyle.SINGLE) palette.background else palette.cell
    return GlanceModifier.fillMaxSize()
        .appWidgetBackground()
        .background(bg)
        .cornerRadius(android.R.dimen.system_app_widget_background_radius)
}

/** Agenda and upcoming birthdays: a scrollable list grouped by day. */
@Composable
fun ListView(days: List<ListDay>, state: WidgetState, palette: WidgetPalette, root: Boolean = true) {
    val context = LocalContext.current
    val textSp = 13f * state.config.textScale
    Box(panelModifier(state, palette, root).padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (days.isEmpty()) {
            Text(context.getString(R.string.no_entries), style = TextStyle(color = palette.onCellDim, fontSize = textSp.sp))
            return@Box
        }
        val dayGroup = @Composable { day: ListDay ->
            Column(
                GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)
                    .clickable(actionStartActivity(Intents.inAppDay(context, day.date, state.appWidgetId))),
            ) {
                Text(
                    day.label,
                    style = TextStyle(
                        color = if (day.isToday) palette.accent else palette.onCellDim,
                        fontSize = (textSp * 0.9f).sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                day.items.forEach { ListRow(it, textSp, palette) }
            }
        }
        if (state.isPreview) {
            // The settings preview can't render collections; show what fits, unscrolled.
            Column(GlanceModifier.fillMaxSize()) { days.take(4).forEach { dayGroup(it) } }
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(days, itemId = { it.date.toEpochDay() }) { dayGroup(it) }
            }
        }
    }
}

@Composable
private fun ListRow(item: ListItem, textSp: Float, palette: WidgetPalette) {
    val textColor = if (item.dimmed) palette.onCellDim else palette.onCell
    Row(
        GlanceModifier.fillMaxWidth().padding(top = 3.dp).semantics { contentDescription = listOfNotNull(item.title, item.detail).joinToString(", ") },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.width(4.dp).height((textSp * 2.4f).dp).background(palette.chip(item.color, item.dimmed)).cornerRadius(2.dp)) {}
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.icon) {
                    Image(
                        ImageProvider(R.drawable.ic_cake), contentDescription = null,
                        modifier = GlanceModifier.size(textSp.dp),
                        colorFilter = ColorFilter.tint(palette.drawableTint(item.color)),
                    )
                    Spacer(GlanceModifier.width(4.dp))
                }
                ClippedText(item.title, TextStyle(color = textColor, fontSize = textSp.sp, fontWeight = FontWeight.Medium))
            }
            item.detail?.let {
                ClippedText(it, TextStyle(color = palette.onCellDim, fontSize = (textSp * 0.85f).sp))
            }
        }
    }
}

/** One line, cut at the edge without "…" (FR-L5): the text is laid out wider than its box. */
@Composable
private fun ClippedText(text: String, style: TextStyle) {
    Box(GlanceModifier.fillMaxWidth()) {
        Text(app.whatsup.logic.Clipping.unbreakable(text), modifier = GlanceModifier.width(1000.dp), style = style)
    }
}

/** The next events, the first one large, with a live countdown when it starts within a day. */
@Composable
fun NextUpView(items: List<NextUpItem>, state: WidgetState, palette: WidgetPalette, root: Boolean = true) {
    val context = LocalContext.current
    val size = LocalSize.current
    val scale = state.config.textScale
    // The first item takes about 60 dp, each further one about 44 dp.
    val fits = 1 + ((size.height.value - 16f - 60f * scale) / (44f * scale)).toInt().coerceAtLeast(0)
    Column(panelModifier(state, palette, root).padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (items.isEmpty()) {
            Text(context.getString(R.string.nothing_coming_up), style = TextStyle(color = palette.onCellDim, fontSize = (14f * scale).sp))
        }
        items.take(fits).forEachIndexed { index, item ->
            NextUpRow(item, big = index == 0, state, palette)
        }
    }
}

@Composable
private fun NextUpRow(item: NextUpItem, big: Boolean, state: WidgetState, palette: WidgetPalette) {
    val context = LocalContext.current
    val scale = state.config.textScale
    val titleSp = (if (big) 18f else 14f) * scale
    val detailSp = (if (big) 13f else 12f) * scale
    // A fixed height: the tap area fills the Box and would otherwise stretch it.
    val rowHeight = ((titleSp + detailSp) * 1.45f + 8f).dp
    Box(GlanceModifier.fillMaxWidth().height(rowHeight).padding(vertical = 4.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.width(4.dp).height(((titleSp + detailSp) * 1.35f).dp).background(palette.chip(item.color, false)).cornerRadius(2.dp)) {}
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight()) {
                ClippedText(item.title, TextStyle(color = palette.onCell, fontSize = titleSp.sp, fontWeight = FontWeight.Bold))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.whenText, style = TextStyle(color = if (item.ongoing) palette.accent else palette.onCellDim, fontSize = detailSp.sp))
                    item.countdownTo?.let { target ->
                        Spacer(GlanceModifier.width(6.dp))
                        AndroidRemoteViews(countdown(context, target, palette, detailSp))
                    }
                }
            }
        }
        // Taps open the day popup next to the item (FR-I1, FR-I2).
        AndroidRemoteViews(dayTapTarget(context, item.date, state.appWidgetId), GlanceModifier.fillMaxSize())
    }
}

/**
 * A Chronometer counting down to [target]. It ticks on its own in the
 * launcher, so the widget needs no updates for it; the next update comes
 * when the event starts (UpdateScheduler).
 */
private fun countdown(context: Context, target: Instant, palette: WidgetPalette, textSp: Float): RemoteViews {
    val millisLeft = target.toEpochMilli() - System.currentTimeMillis()
    return RemoteViews(context.packageName, R.layout.countdown).apply {
        setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + millisLeft, context.getString(R.string.countdown_format), true)
        setChronometerCountDown(R.id.countdown, true)
        setTextColor(R.id.countdown, palette.accent.getColor(context).toArgb())
        setTextViewTextSize(R.id.countdown, TypedValue.COMPLEX_UNIT_SP, textSp)
    }
}
