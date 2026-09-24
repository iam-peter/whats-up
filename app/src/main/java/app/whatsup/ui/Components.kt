package app.whatsup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.whatsup.R
import app.whatsup.data.CalendarInfo
import app.whatsup.model.ChipPattern
import app.whatsup.model.LineStyle
import app.whatsup.widget.patternTile

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

/** [selected] null means "all calendars". */
@Composable
fun CalendarPicker(calendars: List<CalendarInfo>, selected: Set<Long>?, onChange: (Set<Long>?) -> Unit) {
    Column {
        calendars.forEach { cal ->
            val checked = selected == null || cal.id in selected
            Row(Modifier.fillMaxWidth().clickable {
                val current = selected ?: calendars.map { it.id }.toSet()
                val next = if (checked) current - cal.id else current + cal.id
                onChange(if (next.size == calendars.size) null else next)
            }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                Box(Modifier.size(12.dp).background(Color(cal.color), CircleShape))
                Column(Modifier.padding(start = 8.dp)) {
                    Text(cal.name)
                    Text(cal.account, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * A calendar (or the contacts birthday source) with its fill and, for
 * calendars with timed events, outline choices (FR-E6).
 */
@Composable
fun CalendarStyleRow(
    label: String,
    sublabel: String?,
    color: Color,
    fill: ChipPattern,
    onFill: (ChipPattern) -> Unit,
    line: LineStyle?,
    onLine: (LineStyle) -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label)
        sublabel?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StyleDropdown(stringResource(R.string.fill), ChipPattern.entries, fill, onFill, Modifier.weight(1f),
                name = { stringResource(patternName(it)) }) { FillSwatch(color, it) }
            if (line != null) {
                StyleDropdown(stringResource(R.string.outline), LineStyle.entries, line, onLine, Modifier.weight(1f),
                    name = { stringResource(lineName(it)) }) { LineSwatch(color, it) }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> StyleDropdown(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier,
    name: @Composable (T) -> String,
    swatch: @Composable (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }, modifier) {
        OutlinedTextField(
            value = name(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            leadingIcon = { swatch(selected) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(name(option)) },
                    leadingIcon = { swatch(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private val SWATCH_WIDTH = 32.dp
private val SWATCH_HEIGHT = 18.dp

/** Drawn like the widget: the tile tinted to the colour, transparent in between. */
@Composable
private fun FillSwatch(color: Color, pattern: ChipPattern) {
    val tile: ImageBitmap? = patternTile(pattern)?.let { ImageBitmap.imageResource(it) }
    val brush = remember(tile) { tile?.let { ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)) } }
    Box(
        Modifier.size(SWATCH_WIDTH, SWATCH_HEIGHT).clip(RoundedCornerShape(3.dp)).drawBehind {
            if (brush == null) drawRect(color) else drawRect(brush, colorFilter = ColorFilter.tint(color))
        },
    )
}

/** Matches the chip_outline* drawables. */
@Composable
private fun LineSwatch(color: Color, style: LineStyle) {
    Box(
        Modifier.size(SWATCH_WIDTH, SWATCH_HEIGHT).drawBehind {
            val width = (if (style == LineStyle.DOTTED) 1.5.dp else 1.dp).toPx()
            val effect = when (style) {
                LineStyle.SOLID -> null
                LineStyle.DASHED -> PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))
                LineStyle.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 1.5.dp.toPx()))
            }
            drawRoundRect(
                color,
                topLeft = Offset(width / 2, width / 2),
                size = Size(size.width - width, size.height - width),
                cornerRadius = CornerRadius(3.dp.toPx()),
                style = Stroke(width, pathEffect = effect),
            )
        },
    )
}

private fun patternName(pattern: ChipPattern) = when (pattern) {
    ChipPattern.NONE -> R.string.pattern_none
    ChipPattern.STRIPES -> R.string.pattern_stripes
    ChipPattern.DOTS -> R.string.pattern_dots
    ChipPattern.GRID -> R.string.pattern_grid
    ChipPattern.ZIGZAG -> R.string.pattern_zigzag
}

private fun lineName(style: LineStyle) = when (style) {
    LineStyle.SOLID -> R.string.line_solid
    LineStyle.DASHED -> R.string.line_dashed
    LineStyle.DOTTED -> R.string.line_dotted
}
