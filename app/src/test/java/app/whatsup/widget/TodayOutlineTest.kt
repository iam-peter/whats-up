package app.whatsup.widget

import app.whatsup.R
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayOutlineTest {
    private fun outline(
        column: Int,
        firstWeek: Boolean = false,
        lastWeek: Boolean = false,
        showWeekNumbers: Boolean = false,
        cornerInsetDp: Float = 7.4f,
        rtl: Boolean = false,
    ) = todayOutline(column, 6, firstWeek, lastWeek, showWeekNumbers, cornerInsetDp, rtl)

    @Test fun `corner cells follow the widget's corners`() {
        assertEquals(R.drawable.today_outline_top_left, outline(0, firstWeek = true))
        assertEquals(R.drawable.today_outline_top_right, outline(6, firstWeek = true))
        assertEquals(R.drawable.today_outline_bottom_left, outline(0, lastWeek = true))
        assertEquals(R.drawable.today_outline_bottom_right, outline(6, lastWeek = true))
    }

    @Test fun `a single week rounds both corners of a side`() {
        assertEquals(R.drawable.today_outline_left, outline(0, firstWeek = true, lastWeek = true))
        assertEquals(R.drawable.today_outline_right, outline(6, firstWeek = true, lastWeek = true))
    }

    @Test fun `other cells keep the plain border`() {
        assertEquals(R.drawable.today_outline, outline(3, firstWeek = true))
        assertEquals(R.drawable.today_outline, outline(0))
    }

    @Test fun `the wide edge margin keeps the plain border`() =
        assertEquals(R.drawable.today_outline, outline(0, firstWeek = true, cornerInsetDp = 0f))

    @Test fun `week numbers take the left corners`() =
        assertEquals(R.drawable.today_outline, outline(0, firstWeek = true, showWeekNumbers = true))

    @Test fun `right-to-left mirrors the corners`() {
        assertEquals(R.drawable.today_outline_top_right, outline(0, firstWeek = true, rtl = true))
        assertEquals(R.drawable.today_outline_bottom_left, outline(6, lastWeek = true, rtl = true))
        // The week numbers are at the right edge then.
        assertEquals(R.drawable.today_outline, outline(0, firstWeek = true, showWeekNumbers = true, rtl = true))
    }
}
