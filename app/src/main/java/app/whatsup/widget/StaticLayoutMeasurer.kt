package app.whatsup.widget

import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import app.whatsup.logic.Clipping
import app.whatsup.logic.TextMeasurer
import app.whatsup.logic.WordBreaks
import kotlin.math.max

/** Measures with the same metrics the widget TextViews use (bold, system font). */
class StaticLayoutMeasurer(context: Context) : TextMeasurer {
    private val metrics = context.resources.displayMetrics
    private val paints = HashMap<Float, TextPaint>()

    private fun paint(textSp: Float) = paints.getOrPut(textSp) {
        TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSp, metrics)
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun widthPx(widthDp: Float) =
        max(1, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, widthDp, metrics).toInt())

    private fun layout(text: String, widthDp: Float, textSp: Float, maxLines: Int = Int.MAX_VALUE) =
        StaticLayout.Builder.obtain(text, 0, text.length, paint(textSp), widthPx(widthDp))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .build()

    override fun lineCount(text: String, widthDp: Float, textSp: Float): Int {
        val l = layout(text, widthDp, textSp)
        return WordBreaks.linesWithoutMidWordBreak(text, (0 until l.lineCount).map(l::getLineEnd))
    }

    /**
     * TextViews add font padding (top/bottom instead of ascent/descent) to
     * the first and last line; the lines in between use the normal spacing.
     */
    override fun textHeightDp(lines: Int, textSp: Float): Float {
        val fm = paint(textSp).fontMetrics
        val px = (fm.bottom - fm.top) + (lines - 1).coerceAtLeast(0) * (fm.descent - fm.ascent)
        return px / metrics.density
    }

    override fun clip(text: String, maxLines: Int, widthDp: Float, textSp: Float): String {
        val l = layout(text, widthDp, textSp)
        val lines = maxLines.coerceIn(1, l.lineCount.coerceAtLeast(1))
        val head = (0 until lines - 1).map { text.substring(l.getLineStart(it), l.getLineEnd(it)).trimEnd() }
        val tail = text.substring(if (lines > 1) l.getLineStart(lines - 1) else 0)
        return (head + Clipping.unbreakable(tail)).joinToString("\n")
    }
}
