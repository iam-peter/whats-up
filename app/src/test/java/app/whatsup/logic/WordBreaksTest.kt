package app.whatsup.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class WordBreaksTest {
    private fun lines(text: String, vararg ends: Int) = WordBreaks.linesWithoutMidWordBreak(text, ends.toList())

    @Test fun `single line stays single`() = assertEquals(1, lines("Biotonne", 8))

    @Test fun `break at a space allows two lines`() = assertEquals(2, lines("Gelbe Tonne", 6, 11))

    @Test fun `break inside a word falls back to one line`() = assertEquals(1, lines("Sportzeug!", 6, 10))

    @Test fun `break after a hyphen is a word boundary`() = assertEquals(2, lines("Eltern-Kind", 7, 11))

    @Test fun `wrapping stops at the first mid-word break`() =
        assertEquals(2, lines("Malkurs Jugendzentrum", 8, 15, 21))

    @Test fun `no lines reported counts as one`() = assertEquals(1, lines("", ))
}
