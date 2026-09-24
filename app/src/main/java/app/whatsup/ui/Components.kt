package app.whatsup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.whatsup.R
import app.whatsup.data.CalendarInfo
import app.whatsup.model.ChipPattern
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

/** A calendar (or the contacts birthday source) with its pattern choices (FR-E6). */
@Composable
fun PatternRow(label: String, sublabel: String?, color: Color, selected: ChipPattern, onSelect: (ChipPattern) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label)
        sublabel?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipPattern.entries.forEach { pattern ->
                PatternSwatch(color, pattern, pattern == selected) { onSelect(pattern) }
            }
        }
    }
}

@Composable
private fun PatternSwatch(color: Color, pattern: ChipPattern, selected: Boolean, onClick: () -> Unit) {
    val tile: ImageBitmap? = patternTile(pattern)?.let { ImageBitmap.imageResource(it) }
    val brush = remember(tile) { tile?.let { ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)) } }
    val name = stringResource(patternName(pattern))
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier.size(width = 48.dp, height = 24.dp)
            .clip(shape)
            .background(color)
            .then(if (brush != null) Modifier.background(brush) else Modifier)
            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, shape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = name
                role = Role.RadioButton
                this.selected = selected
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
