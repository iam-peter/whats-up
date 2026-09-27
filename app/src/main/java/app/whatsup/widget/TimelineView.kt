package app.whatsup.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
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
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import app.whatsup.config.BackgroundStyle
import app.whatsup.logic.TimelineDay
import app.whatsup.logic.TimelineModel

/**
 * Week or day timeline: day headers, an all-day row, and a time area where
 * events sit at their time. Glance has no absolute positioning, so each lane
 * is a column of spacers and event boxes with heights from the builder.
 */
@Composable
fun TimelineView(model: TimelineModel, state: WidgetState, palette: WidgetPalette, root: Boolean = true) {
    val m = model.metrics
    var modifier = GlanceModifier.fillMaxSize()
    if (root) modifier = modifier.appWidgetBackground().cornerRadius(android.R.dimen.system_app_widget_background_radius)
    if (state.config.background == BackgroundStyle.SINGLE) modifier = modifier.background(palette.background)
    val inset = (m.gapDp + m.cellPaddingDp).dp
    val axis = model.axisWidthDp.dp
    Column(modifier.padding(m.outerPaddingDp.dp)) {
        Row(GlanceModifier.fillMaxWidth().padding(top = inset + m.headerInsetDp.dp, bottom = inset)) {
            Spacer(GlanceModifier.width(axis))
            model.days.forEach { DayTitle(it, model, palette, GlanceModifier.defaultWeight().padding(horizontal = inset + m.headerInsetDp.dp)) }
        }
        if (model.allDayRows > 0) {
            Row(GlanceModifier.fillMaxWidth().height((model.allDayRows * m.lineHeightDp).dp)) {
                Spacer(GlanceModifier.width(axis))
                model.days.forEach { day ->
                    Column(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = inset)) {
                        day.allDay.forEach { ChipView(it, state, m, palette) }
                    }
                }
            }
        }
        Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
            HourGrid(model, palette)
            Row(GlanceModifier.fillMaxSize()) {
                Spacer(GlanceModifier.width(axis))
                model.days.forEach { DayColumn(it, model, state, palette, GlanceModifier.defaultWeight().fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun DayTitle(day: TimelineDay, model: TimelineModel, palette: WidgetPalette, modifier: GlanceModifier) {
    val m = model.metrics
    val color = if (day.isPast) palette.onCellDim else palette.onCell
    Row(modifier.height(m.headerHeightDp.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(day.weekdayLabel, style = TextStyle(color = color, fontSize = m.textSp.sp, fontWeight = FontWeight.Medium))
        Spacer(GlanceModifier.defaultWeight())
        day.moreText?.let {
            Text(it, style = TextStyle(color = palette.onCellDim, fontSize = (m.textSp * 0.9f).sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.width(3.dp))
        }
        Text(
            day.date.dayOfMonth.toString(),
            style = TextStyle(color = if (day.isToday) palette.accent else color, fontSize = m.textSp.sp, fontWeight = FontWeight.Bold),
        )
    }
}

/** Hour labels on the left and a faint line per hour; nested in chunks for Glance's 10-children limit. */
@Composable
private fun HourGrid(model: TimelineModel, palette: WidgetPalette) {
    val m = model.metrics
    val hours = (model.startHour until model.endHour).toList()
    Column(GlanceModifier.fillMaxSize()) {
        hours.chunked(8).forEach { chunk ->
            Column(GlanceModifier.fillMaxWidth()) {
                chunk.forEach { hour ->
                    Column(GlanceModifier.fillMaxWidth().height(model.hourHeightDp.dp)) {
                        Box(GlanceModifier.fillMaxWidth().height(1.dp).padding(start = model.axisWidthDp.dp).background(palette.cell)) {}
                        Text(
                            "%d".format(hour),
                            modifier = GlanceModifier.width(model.axisWidthDp.dp).padding(end = 3.dp),
                            style = TextStyle(color = palette.onCellDim, fontSize = (m.textSp * 0.85f).sp, textAlign = TextAlign.End),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayColumn(day: TimelineDay, model: TimelineModel, state: WidgetState, palette: WidgetPalette, modifier: GlanceModifier) {
    val context = LocalContext.current
    val m = model.metrics
    Box(modifier.padding(horizontal = m.gapDp.dp).semantics { contentDescription = day.description }) {
        Row(GlanceModifier.fillMaxSize()) {
            day.lanes.forEach { lane ->
                Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                    var y = 0f
                    lane.forEach { e ->
                        if (e.topDp > y) Spacer(GlanceModifier.height((e.topDp - y).dp))
                        Box(GlanceModifier.fillMaxWidth().height(e.heightDp.dp)) { ChipView(e.chip, state, m, palette, fill = true) }
                        y = e.topDp + e.heightDp
                    }
                }
            }
        }
        // Current time in today's column.
        if (day.isToday) model.nowTopDp?.let { top ->
            Column(GlanceModifier.fillMaxSize()) {
                Spacer(GlanceModifier.height(top.dp))
                Box(GlanceModifier.fillMaxWidth().height(2.dp).background(palette.accent)) {}
            }
        }
        AndroidRemoteViews(dayTapTarget(context, day.date, state.appWidgetId), GlanceModifier.fillMaxSize())
    }
}
