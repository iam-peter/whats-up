package app.whatsup.logic

/**
 * Android's calendar provider stores Google colours from Google's original
 * palette, but the Google Calendar app shows them in its current palette.
 * Converting keeps the widget's colours the same as in Google Calendar
 * (spec FR-E4). Colours not in the old palette are left alone.
 */
object GooglePalette {
    /** Birthday colour of the Google Calendar app ("Sage"). */
    const val BIRTHDAY = 0xFF33B679.toInt()

    private val classicToCurrent: Map<Int, Int> = listOf(
        // Calendar colours (24).
        0xAC725E to 0x795548, // Cocoa
        0xD06B64 to 0xE67C73, // Flamingo
        0xF83A22 to 0xD50000, // Tomato
        0xFA573C to 0xF4511E, // Tangerine
        0xFF7537 to 0xEF6C00, // Pumpkin
        0xFFAD46 to 0xF09300, // Mango
        0x42D692 to 0x009688, // Eucalyptus
        0x16A765 to 0x0B8043, // Basil
        0x7BD148 to 0x7CB342, // Pistachio
        0xB3DC6C to 0xC0CA33, // Avocado
        0xFBE983 to 0xE4C441, // Citron
        0xFAD165 to 0xF6BF26, // Banana
        0x92E1C0 to 0x33B679, // Sage
        0x9FE1E7 to 0x039BE5, // Peacock
        0x9FC6E7 to 0x4285F4, // Cobalt
        0x4986E7 to 0x3F51B5, // Blueberry
        0x9A9CFF to 0x7986CB, // Lavender
        0xB99AFF to 0xB39DDB, // Wisteria
        0xC2C2C2 to 0x616161, // Graphite
        0xCABDBF to 0xA79B8E, // Birch
        0xCCA6AC to 0xAD1457, // Radicchio
        0xF691B2 to 0xD81B60, // Cherry blossom
        0xCD74E6 to 0x8E24AA, // Grape
        0xA47AE2 to 0x9E69AF, // Amethyst
        // Event colours (11).
        0xA4BDFC to 0x7986CB, // Lavender
        0x7AE7BF to 0x33B679, // Sage
        0xDBADFF to 0x8E24AA, // Grape
        0xFF887C to 0xE67C73, // Flamingo
        0xFBD75B to 0xF6BF26, // Banana
        0xFFB878 to 0xF4511E, // Tangerine
        0x46D6DB to 0x039BE5, // Peacock
        0xE1E1E1 to 0x616161, // Graphite
        0x5484ED to 0x3F51B5, // Blueberry
        0x51B749 to 0x0B8043, // Basil
        0xDC2127 to 0xD50000, // Tomato
    ).associate { (old, new) -> (old or OPAQUE) to (new or OPAQUE) }

    /** The colours offered for birthdays: Google Calendar's event colours. */
    val eventColors: List<Int> = listOf(
        0x7986CB, 0x33B679, 0x8E24AA, 0xE67C73, 0xF6BF26, 0xF4511E, 0x039BE5, 0x616161, 0x3F51B5, 0x0B8043, 0xD50000,
    ).map { it or OPAQUE }

    fun current(argb: Int): Int = classicToCurrent[argb or OPAQUE] ?: argb

    private const val OPAQUE = 0xFF000000.toInt()
}
