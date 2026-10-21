package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchTest {
    @Test
    fun `matches ignoring case, accents and separators`() {
        assertTrue(AppSearch.matches("Kalender", "ka"))
        assertTrue(AppSearch.matches("Café", "cafe"))
        assertTrue(AppSearch.matches("Google Maps", "googlemaps"))
        assertFalse(AppSearch.matches("Kalender", "xyz"))
    }

    @Test
    fun `plain match covers the query`() {
        assertEquals(0..1, AppSearch.matchRange("Kalender", "ka"))
        assertEquals(3..5, AppSearch.matchRange("Kalender", " END "))
    }

    @Test
    fun `match ignoring accents covers the accented letters`() {
        assertEquals(0..3, AppSearch.matchRange("Café", "cafe"))
        assertEquals(1..2, AppSearch.matchRange("Zürich", "ur"))
    }

    @Test
    fun `match across a separator covers the separator`() {
        assertEquals(4..8, AppSearch.matchRange("Google Maps", "lema"))
    }

    @Test
    fun `no range without a match or a query`() {
        assertNull(AppSearch.matchRange("Kalender", "xyz"))
        assertNull(AppSearch.matchRange("Kalender", "  "))
    }
}
