package app.whatsup.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class CellFitterTest {
    private fun fit(needs: List<Int>, capacity: Int, maxItems: Int = Int.MAX_VALUE) =
        CellFitter.fit(needs.indices.toList(), capacity, maxItems = maxItems) { needs[it] }

    @Test fun `everything fits with wrapping`() {
        val r = fit(listOf(2, 1, 2), capacity = 5)
        assertEquals(listOf(2, 1, 2), r.visible.map { it.lines })
        assertEquals(0, r.hidden)
    }

    @Test fun `wrapping is capped at two lines`() {
        val r = fit(listOf(5), capacity = 4)
        assertEquals(listOf(2), r.visible.map { it.lines })
    }

    @Test fun `spare lines go to earlier entries first`() {
        val r = fit(listOf(2, 2, 2), capacity = 4)
        assertEquals(listOf(2, 1, 1), r.visible.map { it.lines })
        assertEquals(0, r.hidden)
    }

    @Test fun `last line is reserved for plus N`() {
        val r = fit(listOf(1, 1, 1, 1, 1), capacity = 3)
        assertEquals(2, r.visible.size)
        assertEquals(3, r.hidden)
    }

    @Test fun `single line cell shows only plus N when crowded`() {
        val r = fit(listOf(1, 1), capacity = 1)
        assertEquals(0, r.visible.size)
        assertEquals(2, r.hidden)
    }

    @Test fun `no space hides everything`() {
        assertEquals(3, fit(listOf(1, 1, 1), capacity = 0).hidden)
    }

    @Test fun `max items respects Glance child limit`() {
        val r = fit(List(12) { 1 }, capacity = 20, maxItems = 8)
        assertEquals(8, r.visible.size)
        assertEquals(4, r.hidden)
    }
}
