package app.whatsup.widget

import android.content.Context
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import app.whatsup.logic.TextMeasurer

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

    /** TextViews include font padding, so one line spans top to bottom, not ascent to descent. */
    override fun textHeightDp(textSp: Float): Float {
        val fm = paint(textSp).fontMetrics
        return (fm.bottom - fm.top) / metrics.density
    }
}
