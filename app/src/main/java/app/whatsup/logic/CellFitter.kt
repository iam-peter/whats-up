package app.whatsup.logic

data class FitItem<T>(val item: T, val lines: Int)
data class FitResult<T>(val visible: List<FitItem<T>>, val hidden: Int)

/**
 * Decides which entries of a day cell are shown and how many lines each
 * gets (spec FR-L5): every shown entry gets one line, spare lines go to
 * entries in order (up to [maxLinesPerItem]), and if not everything fits,
 * the last line is reserved for "+N".
 */
object CellFitter {
    fun <T> fit(
        items: List<T>,
        capacityLines: Int,
        maxLinesPerItem: Int = 2,
        maxItems: Int = Int.MAX_VALUE,
        linesNeeded: (T) -> Int,
    ): FitResult<T> {
        if (items.isEmpty()) return FitResult(emptyList(), 0)
        if (capacityLines <= 0) return FitResult(emptyList(), items.size)
        val needs = items.map { linesNeeded(it).coerceIn(1, maxLinesPerItem) }

        if (items.size <= maxItems && needs.sum() <= capacityLines) {
            return FitResult(items.zip(needs) { item, n -> FitItem(item, n) }, 0)
        }
        val fitsOneLineEach = items.size <= capacityLines && items.size <= maxItems
        val shown = if (fitsOneLineEach) items.size else minOf(capacityLines - 1, maxItems)
        val hidden = items.size - shown
        var spare = capacityLines - shown - (if (hidden > 0) 1 else 0)
        val visible = items.take(shown).mapIndexed { i, item ->
            val extra = minOf(needs[i] - 1, spare)
            spare -= extra
            FitItem(item, 1 + extra)
        }
        return FitResult(visible, hidden)
    }
}
