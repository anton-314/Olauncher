package app.olauncher.helper

import app.olauncher.data.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TimeExtensionsTest {

    @Test
    fun `midnight has no time of day and is idempotent`() {
        val now = System.currentTimeMillis()
        val midnight = now.convertEpochToMidnight()
        val calendar = Calendar.getInstance().apply { timeInMillis = midnight }
        assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calendar.get(Calendar.MINUTE))
        assertEquals(0, calendar.get(Calendar.SECOND))
        assertEquals(0, calendar.get(Calendar.MILLISECOND))
        assertTrue(midnight <= now)
        assertEquals(midnight, midnight.convertEpochToMidnight())
    }

    @Test
    fun `days since today is zero`() {
        assertEquals(0, System.currentTimeMillis().isDaySince())
    }

    private fun <T> inTimeZone(id: String, block: () -> T): T {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(id))
        try {
            return block()
        } finally {
            TimeZone.setDefault(original)
        }
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance().apply { clear(); set(year, month - 1, day, hour, 0) }.timeInMillis

    @Test
    fun `days since is exact across the spring daylight saving change`() = inTimeZone("Europe/Berlin") {
        // 29 March 2026 has only 23 hours in Berlin
        assertEquals(2, at(2026, 3, 28, 12).isDaySince(now = at(2026, 3, 30, 12)))
        assertEquals(1, at(2026, 3, 29, 23).isDaySince(now = at(2026, 3, 30, 0)))
        assertEquals(10, at(2026, 3, 25, 9).isDaySince(now = at(2026, 4, 4, 9)))
    }

    @Test
    fun `days since is exact across the autumn daylight saving change`() = inTimeZone("Europe/Berlin") {
        // 25 October 2026 has 25 hours in Berlin
        assertEquals(2, at(2026, 10, 24, 12).isDaySince(now = at(2026, 10, 26, 12)))
        assertEquals(70, at(2026, 8, 20, 18).isDaySince(now = at(2026, 10, 29, 18)))
    }

    @Test
    fun `days since counts calendar days, not 24 hour periods`() = inTimeZone("UTC") {
        assertEquals(1, at(2026, 1, 1, 23).isDaySince(now = at(2026, 1, 2, 0)))
        assertEquals(0, at(2026, 1, 2, 0).isDaySince(now = at(2026, 1, 2, 23)))
    }

    @Test
    fun `a timestamp in the future gives a negative day count`() {
        // e.g. after the clock was moved back; callers compare with >= so this must not look old
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        assertTrue(tomorrow.isDaySince() < 0)
        assertFalse(tomorrow.hasBeenHours(1))
        assertFalse(tomorrow.hasBeenMinutes(1))
    }

    @Test
    fun `never set timestamp counts as long ago`() {
        assertTrue(0L.hasBeenHours(4))
        assertTrue(0L.hasBeenMinutes(1))
    }

    @Test
    fun `has been thresholds`() {
        val now = System.currentTimeMillis()
        assertTrue(now.hasBeenMinutes(0))
        assertFalse(now.hasBeenMinutes(1))
        assertTrue((now - 61_000).hasBeenMinutes(1))
        assertTrue((now - Constants.ONE_HOUR_IN_MILLIS * 4 - 1000).hasBeenHours(4))
        assertFalse((now - Constants.ONE_HOUR_IN_MILLIS * 3).hasBeenHours(4))
    }

    @Test
    fun `date time visibility`() {
        assertTrue(Constants.DateTime.isTimeVisible(Constants.DateTime.ON))
        assertFalse(Constants.DateTime.isTimeVisible(Constants.DateTime.DATE_ONLY))
        assertFalse(Constants.DateTime.isTimeVisible(Constants.DateTime.OFF))
        assertTrue(Constants.DateTime.isDateVisible(Constants.DateTime.ON))
        assertTrue(Constants.DateTime.isDateVisible(Constants.DateTime.DATE_ONLY))
        assertFalse(Constants.DateTime.isDateVisible(Constants.DateTime.OFF))
        // Unknown stored values hide both
        assertFalse(Constants.DateTime.isTimeVisible(42))
        assertFalse(Constants.DateTime.isDateVisible(-1))
    }
}
