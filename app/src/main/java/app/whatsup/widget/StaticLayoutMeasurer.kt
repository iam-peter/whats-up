package app.whatsup.widget

import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import app.whatsup.logic.TextMeasurer
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
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

    override fun lineCount(text: String, widthDp: Float, textSp: Float) = layout(text, widthDp, textSp).lineCount

    override fun ellipsize(text: String, maxLines: Int, widthDp: Float, textSp: Float): String {
        val l = layout(text, widthDp, textSp, maxLines)
        val last = l.lineCount - 1
        if (l.getEllipsisCount(last) == 0) return text
        val cut = l.getLineStart(last) + l.getEllipsisStart(last)
        return text.substring(0, cut).trimEnd() + "…"
    }
}
