package app.whatsup.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class GooglePaletteTest {
    @Test fun `calendar colours map to the current palette`() {
        // Values as stored on the reference phone.
        assertEquals(0xFFF6BF26.toInt(), GooglePalette.current(-339611))   // Hauptkalender: Banana
        assertEquals(0xFF9E69AF.toInt(), GooglePalette.current(-5997854))  // Amethyst
        assertEquals(0xFFAD1457.toInt(), GooglePalette.current(-3365204))  // Radicchio
    }

    @Test fun `event colours map to the current palette`() =
        assertEquals(0xFFD50000.toInt(), GooglePalette.current(0xFFDC2127.toInt()))

    @Test fun `other colours are kept`() = assertEquals(-13872435, GooglePalette.current(-13872435))
}
