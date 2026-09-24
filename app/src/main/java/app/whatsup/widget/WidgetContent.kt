package app.whatsup.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
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
import app.whatsup.R
import app.whatsup.config.BackgroundStyle
import app.whatsup.config.WeekendStyle
import app.whatsup.config.WidgetConfig
import app.whatsup.logic.Chip
import app.whatsup.logic.ChipStyle
import app.whatsup.logic.ChipTarget
import app.whatsup.logic.DayCell
import app.whatsup.logic.GridMetrics
import app.whatsup.logic.GridModel
import app.whatsup.logic.GIFT
import app.whatsup.logic.GridModelBuilder
import app.whatsup.model.CalendarEntry
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.WeekFields

data class WidgetState(
    val hasPermission: Boolean,
    val entries: List<CalendarEntry>,
    val config: WidgetConfig,
    val today: LocalDate,
    val now: Instant,
)

fun widgetCornerRadiusDp(context: Context): Float {
    val res = context.resources
    return res.getDimension(android.R.dimen.system_app_widget_background_radius) / res.displayMetrics.density
}

/** Shared by the widget and the live preview in the configuration screen. */
@Composable
fun WidgetContent(state: WidgetState) {
    val context = LocalContext.current
    val size = LocalSize.current
    val palette = WidgetPalette(context, state.config)
    if (!state.hasPermission) {
        PermissionPrompt(palette)
        return
    }
    val model = GridModelBuilder(StaticLayoutMeasurer(context), AndroidLabels(context)).build(
        entries = state.entries,
        today = state.today,
        now = state.now,
        firstDayOfWeek = WeekFields.of(context.resources.configuration.locales[0]).firstDayOfWeek,
        widthDp = size.width.value,
        heightDp = size.height.value,
        cfg = state.config,
        cornerRadiusDp = widgetCornerRadiusDp(context),
    )
    DayGrid(model, state.config, palette)
}

@Composable
private fun DayGrid(model: GridModel, cfg: WidgetConfig, palette: WidgetPalette) {
    val m = model.metrics
    var root = GlanceModifier.fillMaxSize()
        .appWidgetBackground()
        .cornerRadius(android.R.dimen.system_app_widget_background_radius)
    if (cfg.background == BackgroundStyle.SINGLE) root = root.background(palette.background)
    Column(root.padding(m.outerPaddingDp.dp)) {
        model.weeks.forEachIndexed { row, week ->
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                model.weekNumbers?.let { numbers ->
                    Text(
                        numbers[row].toString(),
                        modifier = GlanceModifier.width(m.weekNumberWidthDp.dp).padding(top = (m.gapDp + m.cellPaddingDp).dp),
                        style = TextStyle(color = palette.onCellDim, fontSize = (m.textSp * 0.85f).sp, textAlign = TextAlign.Center),
                    )
                }
                week.forEach { cell ->
                    DayCellView(cell, cfg, m, palette, GlanceModifier.defaultWeight().fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun DayCellView(cell: DayCell, cfg: WidgetConfig, m: GridMetrics, palette: WidgetPalette, modifier: GlanceModifier) {
    val context = LocalContext.current
    val textColor = if (cell.isPast) palette.onCellDim else palette.onCell
    var body = GlanceModifier.fillMaxSize()
    if (cfg.background == BackgroundStyle.PER_CELL) {
        val bg = if (cfg.weekendStyle == WeekendStyle.TINTED && cell.isWeekend) palette.weekendCell else palette.cell
        body = body.background(bg).cornerRadius(4.dp)
    } else if (cfg.weekendStyle == WeekendStyle.TINTED && cell.isWeekend) {
        body = body.background(palette.weekendCell).cornerRadius(4.dp)
    }
    body = body
        .clickable(actionStartActivity(Intents.calendarDay(cell.date, cfg.calendarPackage)))
        .semantics { contentDescription = cell.description }

    Box(modifier.padding(m.gapDp.dp)) {
        Box(body) {
            var content = GlanceModifier.fillMaxSize().padding(m.cellPaddingDp.dp)
            if (cell.isToday) {
                // Spec Q-35: today is marked with a cell border.
                content = content.background(ImageProvider(R.drawable.today_outline), colorFilter = ColorFilter.tint(palette.accent))
            }
            Column(content) {
                Row(GlanceModifier.fillMaxWidth().height(m.headerHeightDp.dp), verticalAlignment = Alignment.CenterVertically) {
                    cell.weekdayLabel?.let {
                        Text(it, style = TextStyle(color = textColor, fontSize = m.textSp.sp, fontWeight = FontWeight.Medium))
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        cell.date.dayOfMonth.toString(),
                        style = TextStyle(
                            color = if (cell.isToday) palette.accent else textColor,
                            fontSize = m.textSp.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                cell.chips.forEach { ChipView(it, cfg, m, palette) }
                cell.moreText?.let {
                    Text(
                        it,
                        maxLines = 1,
                        modifier = GlanceModifier.fillMaxWidth()
                            .padding(horizontal = m.chipHPaddingDp.dp)
                            .clickable(actionStartActivity(Intents.forTarget(context, ChipTarget.InAppDay(cell.date), cfg.calendarPackage))),
                        style = TextStyle(color = palette.onCellDim, fontSize = m.textSp.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipView(chip: Chip, cfg: WidgetConfig, m: GridMetrics, palette: WidgetPalette) {
    val context = LocalContext.current
    val tint = palette.chip(chip.color, chip.dimmed)
    val background = when (chip.style) {
        ChipStyle.FILLED -> GlanceModifier.background(tint)
        // Spike S1: Glance has no border modifier, so the outline is a tinted drawable.
        ChipStyle.OUTLINED -> GlanceModifier.background(ImageProvider(R.drawable.chip_outline), colorFilter = ColorFilter.tint(tint))
    }
    val textColor = when (chip.style) {
        ChipStyle.FILLED -> if (chip.dimmed) palette.onChipDim else palette.onChip
        ChipStyle.OUTLINED -> if (chip.dimmed) palette.onCellDim else palette.onCell
    }
    // The Box keeps chip + spacing as one child (Glance allows 10 per Column).
    Box(GlanceModifier.fillMaxWidth().padding(bottom = 1.dp)) {
        Text(
            chip.text,
            maxLines = chip.maxLines,
            modifier = GlanceModifier.fillMaxWidth()
                .then(background)
                .cornerRadius(3.dp)
                .padding(horizontal = m.chipHPaddingDp.dp, vertical = m.chipVPaddingDp.dp)
                .clickable(actionStartActivity(Intents.forTarget(context, chip.target, cfg.calendarPackage)))
                .semantics { contentDescription = chip.text.removePrefix("$GIFT\u00A0") },
            style = TextStyle(color = textColor, fontSize = m.textSp.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun PermissionPrompt(palette: WidgetPalette) {
    val context = LocalContext.current
    Box(
        GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .background(palette.background)
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .clickable(actionStartActivity(Intents.grantAccess(context))),
        contentAlignment = Alignment.Center,
    ) {
        Text(context.getString(R.string.grant_access), style = TextStyle(color = palette.onCell, fontSize = 14.sp))
    }
}
