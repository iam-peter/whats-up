package app.whatsup.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
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
import androidx.glance.layout.size
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
import app.whatsup.logic.GridModelBuilder
import app.whatsup.logic.SpanBar
import app.whatsup.logic.Week
import app.whatsup.model.CalendarEntry
import app.whatsup.model.ChipPattern
import app.whatsup.model.LineStyle
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.WeekFields

data class WidgetState(
    val hasPermission: Boolean,
    val entries: List<CalendarEntry>,
    val config: WidgetConfig,
    val today: LocalDate,
    val now: Instant,
    /** Passed to the in-app day view, so it shows this widget's calendars. */
    val appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
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
    DayGrid(model, state, palette)
}

@Composable
private fun DayGrid(model: GridModel, state: WidgetState, palette: WidgetPalette) {
    val m = model.metrics
    var root = GlanceModifier.fillMaxSize()
        .appWidgetBackground()
        .cornerRadius(android.R.dimen.system_app_widget_background_radius)
    if (state.config.background == BackgroundStyle.SINGLE) root = root.background(palette.background)
    Column(root.padding(m.outerPaddingDp.dp)) {
        model.weeks.forEach { week ->
            WeekView(week, model.showWeekNumbers, state, m, palette, GlanceModifier.fillMaxWidth().defaultWeight())
        }
    }
}

/**
 * A week is two layers (FR-E2): the day backgrounds, which take day taps,
 * and above them the headers, the bar lanes and the day entries. Bars need
 * the second layer because they cross the day columns.
 */
@Composable
private fun WeekView(week: Week, showWeekNumbers: Boolean, state: WidgetState, m: GridMetrics, palette: WidgetPalette, modifier: GlanceModifier) {
    val cfg = state.config
    val inset = (m.gapDp + m.cellPaddingDp).dp
    val weekNumberColumn = @Composable {
        if (showWeekNumbers) Spacer(GlanceModifier.width(m.weekNumberWidthDp.dp))
    }
    Box(modifier) {
        Row(GlanceModifier.fillMaxSize()) {
            weekNumberColumn()
            week.days.forEach { DayBackground(it, cfg, m, palette, GlanceModifier.defaultWeight().fillMaxHeight()) }
        }
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth().padding(top = inset)) {
                if (showWeekNumbers) {
                    Text(
                        week.weekNumber?.toString() ?: "",
                        modifier = GlanceModifier.width(m.weekNumberWidthDp.dp),
                        style = TextStyle(color = palette.onCellDim, fontSize = (m.textSp * 0.85f).sp, textAlign = TextAlign.Center),
                    )
                }
                week.days.forEach { DayHeader(it, m, palette, GlanceModifier.defaultWeight().padding(horizontal = inset)) }
            }
            week.lanes.forEach { LaneRow(it, weekNumberColumn, state, m, palette) }
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                weekNumberColumn()
                week.days.forEach { DayEntries(it, state, m, palette, GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = inset)) }
            }
        }
    }
}

@Composable
private fun DayBackground(cell: DayCell, cfg: WidgetConfig, m: GridMetrics, palette: WidgetPalette, modifier: GlanceModifier) {
    var body = GlanceModifier.fillMaxSize()
    val tintWeekend = cfg.weekendStyle == WeekendStyle.TINTED && cell.isWeekend
    if (cfg.background == BackgroundStyle.PER_CELL || tintWeekend) {
        body = body.background(if (tintWeekend) palette.weekendCell else palette.cell).cornerRadius(4.dp)
    }
    body = body
        .clickable(actionStartActivity(Intents.calendarDay(LocalContext.current, cell.date, cfg.calendarPackage)))
        .semantics { contentDescription = cell.description }
    Box(modifier.padding(m.gapDp.dp)) {
        Box(body) {
            if (cell.isToday) {
                // Spec Q-35: today is marked with a cell border.
                Box(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.today_outline), colorFilter = ColorFilter.tint(palette.accent))) {}
            }
        }
    }
}

@Composable
private fun DayHeader(cell: DayCell, m: GridMetrics, palette: WidgetPalette, modifier: GlanceModifier) {
    val textColor = if (cell.isPast) palette.onCellDim else palette.onCell
    Row(modifier.height(m.headerHeightDp.dp), verticalAlignment = Alignment.CenterVertically) {
        cell.weekdayLabel?.let {
            Text(it, style = TextStyle(color = textColor, fontSize = m.textSp.sp, fontWeight = FontWeight.Medium))
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(
            cell.date.dayOfMonth.toString(),
            style = TextStyle(color = if (cell.isToday) palette.accent else textColor, fontSize = m.textSp.sp, fontWeight = FontWeight.Bold),
        )
    }
}

/** One lane of bars; bars and the gaps between them get exact widths (Glance weights are all equal). */
@Composable
private fun LaneRow(bars: List<SpanBar>, weekNumberColumn: @Composable () -> Unit, state: WidgetState, m: GridMetrics, palette: WidgetPalette) {
    val inset = m.gapDp + m.cellPaddingDp
    Row(GlanceModifier.fillMaxWidth().height(m.lineHeightDp.dp)) {
        weekNumberColumn()
        var column = 0
        // Multi-day bars span at least two columns unless cut at a week edge, so a
        // lane holds at most four: with their gaps, within Glance's 10 children.
        bars.take(4).forEach { bar ->
            if (bar.startColumn > column) Spacer(GlanceModifier.width(((bar.startColumn - column) * m.cellWidthDp).dp))
            // A bar that continues into the next or previous week runs to the edge.
            val start = if (bar.continuesBefore) 0f else inset
            val end = if (bar.continuesAfter) 0f else inset
            Box(GlanceModifier.width((bar.span * m.cellWidthDp).dp).padding(start = start.dp, end = end.dp)) {
                ChipView(bar.chip, state, m, palette, rounded = !bar.continuesBefore && !bar.continuesAfter)
            }
            column = bar.startColumn + bar.span
        }
    }
}

@Composable
private fun DayEntries(cell: DayCell, state: WidgetState, m: GridMetrics, palette: WidgetPalette, modifier: GlanceModifier) {
    val context = LocalContext.current
    Column(modifier) {
        cell.chips.forEach { ChipView(it, state, m, palette) }
        cell.moreText?.let {
            Text(
                it,
                modifier = GlanceModifier.fillMaxWidth()
                    .height(m.lineHeightDp.dp)
                    .padding(horizontal = m.chipHPaddingDp.dp)
                    .clickable(actionStartActivity(Intents.forTarget(context, ChipTarget.InAppDay(cell.date), state.config.calendarPackage, state.appWidgetId))),
                style = TextStyle(color = palette.onCellDim, fontSize = m.textSp.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun ChipView(chip: Chip, state: WidgetState, m: GridMetrics, palette: WidgetPalette, rounded: Boolean = true) {
    val context = LocalContext.current
    // Glance has no border or pattern modifiers, so both are tinted drawables (FR-E6).
    val fillPattern = patternDrawable(chip.pattern, chip.dimmed)
    val drawable = if (chip.style == ChipStyle.OUTLINED) outlineDrawable(chip.lineStyle, chip.dimmed) else fillPattern
    val background = if (drawable != null) {
        GlanceModifier.background(ImageProvider(drawable), colorFilter = ColorFilter.tint(palette.drawableTint(chip.color)))
    } else {
        GlanceModifier.background(palette.chip(chip.color, chip.dimmed))
    }
    // Text on a solid chip sits on the colour; otherwise it also sits on the cell.
    val onColour = chip.style == ChipStyle.FILLED && fillPattern == null
    val textColor = when {
        onColour -> if (chip.dimmed) palette.onChipDim else palette.onChip
        else -> if (chip.dimmed) palette.onCellDim else palette.onCell
    }
    val chipModifier = GlanceModifier.fillMaxWidth()
        .then(background)
        .cornerRadius(if (rounded) 3.dp else 0.dp)
        .padding(horizontal = m.chipHPaddingDp.dp, vertical = m.chipVPaddingDp.dp)
        .clickable(actionStartActivity(Intents.forTarget(context, chip.target, state.config.calendarPackage, state.appWidgetId)))
        .semantics { contentDescription = chip.description }
    // No ellipsis (FR-L5). The text view is laid out wider than the chip, so its
    // single line never wraps or ellipsises; the chip's bounds cut it at the
    // exact pixel edge. (Glance's TextViews add "…" whenever maxLines is set.)
    val textHeight = chip.textHeightDp.dp
    val text = @Composable { modifier: GlanceModifier ->
        Box(modifier) {
            Text(
                chip.text,
                modifier = GlanceModifier.width(CLIPPED_TEXT_WIDTH).height(textHeight),
                style = TextStyle(color = textColor, fontSize = m.textSp.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
    // The outer Box keeps chip + spacing as one child (Glance allows 10 per Column).
    Box(GlanceModifier.fillMaxWidth().padding(bottom = 1.dp)) {
        if (chip.icon) {
            Row(chipModifier, verticalAlignment = Alignment.CenterVertically) {
                // Monochrome cake, tinted like the text (FR-B2).
                Image(
                    ImageProvider(R.drawable.ic_cake),
                    contentDescription = null,
                    modifier = GlanceModifier.size(m.iconSizeDp.dp),
                    colorFilter = ColorFilter.tint(textColor),
                )
                Spacer(GlanceModifier.width(m.iconGapDp.dp))
                text(GlanceModifier.defaultWeight().height(textHeight))
            }
        } else {
            text(chipModifier.height(textHeight + (2 * m.chipVPaddingDp).dp))
        }
    }
}

/** Wider than any chip; see [ChipView]. */
private val CLIPPED_TEXT_WIDTH = 1000.dp

@DrawableRes
fun outlineDrawable(style: LineStyle, dimmed: Boolean): Int = when (style) {
    LineStyle.SOLID -> if (dimmed) R.drawable.chip_outline_dim else R.drawable.chip_outline
    LineStyle.DASHED -> if (dimmed) R.drawable.chip_outline_dashed_dim else R.drawable.chip_outline_dashed
    LineStyle.DOTTED -> if (dimmed) R.drawable.chip_outline_dotted_dim else R.drawable.chip_outline_dotted
}

/** The raw tile, for drawing the pattern outside the widget (settings swatches). */
@DrawableRes
fun patternTile(pattern: ChipPattern): Int? = when (pattern) {
    ChipPattern.NONE -> null
    ChipPattern.STRIPES -> R.drawable.tile_stripes
    ChipPattern.DOTS -> R.drawable.tile_dots
    ChipPattern.GRID -> R.drawable.tile_grid
    ChipPattern.ZIGZAG -> R.drawable.tile_zigzag
}

@DrawableRes
fun patternDrawable(pattern: ChipPattern, dimmed: Boolean): Int? = when (pattern) {
    ChipPattern.NONE -> null
    ChipPattern.STRIPES -> if (dimmed) R.drawable.pattern_stripes_dim else R.drawable.pattern_stripes
    ChipPattern.DOTS -> if (dimmed) R.drawable.pattern_dots_dim else R.drawable.pattern_dots
    ChipPattern.GRID -> if (dimmed) R.drawable.pattern_grid_dim else R.drawable.pattern_grid
    ChipPattern.ZIGZAG -> if (dimmed) R.drawable.pattern_zigzag_dim else R.drawable.pattern_zigzag
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
