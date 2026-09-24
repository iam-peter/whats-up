package app.whatsup.widget

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import app.whatsup.config.WidgetConfig
import androidx.glance.color.ColorProvider as DayNight

/**
 * Day/night colour pairs so the widget follows the system theme without
 * a re-render (FR-T1). Dynamic colours are the default.
 */
class WidgetPalette(context: Context, cfg: WidgetConfig) {
    private val light: ColorScheme = if (cfg.dynamicColors) dynamicLightColorScheme(context) else lightColorScheme()
    private val dark: ColorScheme = if (cfg.dynamicColors) dynamicDarkColorScheme(context) else darkColorScheme()
    private val opacity = cfg.backgroundOpacity.coerceIn(0f, 1f)

    val background: ColorProvider = DayNight(light.surface.copy(alpha = opacity), dark.surface.copy(alpha = opacity))
    val cell: ColorProvider = DayNight(light.surfaceContainerHigh, dark.surfaceContainerHigh)
    val weekendCell: ColorProvider = DayNight(light.surfaceContainerHighest, dark.surfaceContainerHighest)
    val onCell: ColorProvider = DayNight(light.onSurface, dark.onSurface)
    val onCellDim: ColorProvider = DayNight(light.onSurface.copy(alpha = 0.45f), dark.onSurface.copy(alpha = 0.45f))
    val accent: ColorProvider = DayNight(light.primary, dark.primary)
    val onChip: ColorProvider = DayNight(Color.White, Color.White)
    val onChipDim: ColorProvider = DayNight(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.6f))

    fun chip(argb: Int, dimmed: Boolean): ColorProvider = fixed(Color(argb), if (dimmed) 0.45f else 1f)

    /**
     * Tint for drawable backgrounds (outlines, patterns). Glance's tint keeps
     * the drawable's alpha and ignores the tint's, so dimming comes from the
     * `_dim` drawable variants instead.
     */
    fun drawableTint(argb: Int): ColorProvider = fixed(Color(argb), 1f)

    private fun fixed(c: Color, alpha: Float) = c.copy(alpha = c.alpha * alpha).let { DayNight(it, it) }
}
