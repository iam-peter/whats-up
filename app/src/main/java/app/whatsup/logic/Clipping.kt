package app.whatsup.logic

object Clipping {
    /**
     * Removes break opportunities, so a single-line TextView can only cut the
     * text at its edge (between characters) rather than before a word.
     */
    fun unbreakable(text: String) = text.replace(' ', ' ').replace('-', '‑')
}
