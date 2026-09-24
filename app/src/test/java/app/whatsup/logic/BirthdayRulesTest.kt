package app.whatsup.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BirthdayRulesTest {
    @Test fun `parses full date`() {
        val b = BirthdayRules.parse("Anna", "1986-09-24", "k")!!
        assertEquals(1986, b.birthYear)
        assertEquals(40, BirthdayRules.age(b, LocalDate.of(2026, 9, 24)))
    }

    @Test fun `year-less date has no age`() {
        val b = BirthdayRules.parse("Ben", "--03-12", "k")!!
        assertNull(b.birthYear)
        assertNull(BirthdayRules.age(b, LocalDate.of(2026, 3, 12)))
    }

    @Test fun `apple no-year sentinel is ignored`() {
        assertNull(BirthdayRules.parse("Cem", "1604-05-01", "k")!!.birthYear)
    }

    @Test fun `compact and date-time formats`() {
        assertEquals(12, BirthdayRules.parse("D", "19900112", "k")!!.day)
        assertEquals(7, BirthdayRules.parse("E", "1990-07-03T00:00:00Z", "k")!!.month)
    }

    @Test fun `garbage is rejected`() {
        assertNull(BirthdayRules.parse("F", "next tuesday", "k"))
        assertNull(BirthdayRules.parse("G", "2000-13-01", "k"))
    }

    @Test fun `leap day moves to 28 February`() {
        val b = BirthdayRules.parse("H", "2000-02-29", "k")!!
        assertEquals(LocalDate.of(2027, 2, 28), BirthdayRules.occurrence(b, 2027))
        assertEquals(LocalDate.of(2028, 2, 29), BirthdayRules.occurrence(b, 2028))
    }

    @Test fun `occurrences across new year`() {
        val b = BirthdayRules.parse("I", "--01-02", "k")!!
        val days = BirthdayRules.occurrencesIn(b, LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 20))
        assertEquals(listOf(LocalDate.of(2027, 1, 2)), days)
    }
}
