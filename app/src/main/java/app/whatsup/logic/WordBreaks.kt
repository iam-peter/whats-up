package app.whatsup.logic

object WordBreaks {
    /** Characters after which a line break counts as a word boundary. */
    private const val BREAK_AFTER = "-/–—"

    /**
     * Number of lines a title may use without being broken inside a word.
     * [lineEnds] are the end offsets of each wrapped line. Wrapping stops
     * at the first line that ends mid-word; that line is then ellipsised
     * instead, so "Sportzeug!" becomes "Sport…" rather than "Sportz / eug!".
     */
    fun linesWithoutMidWordBreak(text: String, lineEnds: List<Int>): Int {
        for (i in 0 until lineEnds.size - 1) {
            if (breaksInsideWord(text, lineEnds[i])) return i + 1
        }
        return lineEnds.size.coerceAtLeast(1)
    }

    private fun breaksInsideWord(text: String, end: Int): Boolean {
        if (end <= 0 || end >= text.length) return false
        val before = text[end - 1]
        return !before.isWhitespace() && before !in BREAK_AFTER && !text[end].isWhitespace()
    }
}
