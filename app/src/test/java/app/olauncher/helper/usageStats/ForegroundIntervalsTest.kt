package app.olauncher.helper.usageStats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundIntervalsTest {
    private fun stat(begin: Long, end: Long, pkg: String = "com.app") = ComponentForegroundStat(begin, end, pkg)

    @Test
    fun `empty input gives no time`() {
        assertTrue(mergeOverlappingIntervals(emptyList()).isEmpty())
        assertEquals(0L, totalForegroundTime(emptyList()))
    }

    @Test
    fun `single interval`() {
        assertEquals(100L, totalForegroundTime(listOf(stat(0, 100))))
    }

    @Test
    fun `overlapping intervals are counted once`() {
        assertEquals(150L, totalForegroundTime(listOf(stat(0, 100), stat(50, 150))))
    }

    @Test
    fun `nested interval adds nothing`() {
        assertEquals(100L, totalForegroundTime(listOf(stat(0, 100), stat(20, 30))))
    }

    @Test
    fun `adjacent intervals merge`() {
        val merged = mergeOverlappingIntervals(listOf(stat(0, 100), stat(100, 200)))
        assertEquals(listOf(stat(0, 200)), merged)
    }

    @Test
    fun `separate intervals are summed`() {
        assertEquals(30L, totalForegroundTime(listOf(stat(0, 10), stat(100, 120))))
    }

    @Test
    fun `unsorted input is handled`() {
        assertEquals(
            listOf(stat(0, 40), stat(100, 120)),
            mergeOverlappingIntervals(listOf(stat(100, 120), stat(30, 40), stat(0, 35)))
        )
    }

    @Test
    fun `reversed intervals from swapped events do not subtract time`() {
        assertEquals(0L, totalForegroundTime(listOf(stat(100, 50))))
        assertEquals(100L, totalForegroundTime(listOf(stat(0, 100), stat(500, 200))))
    }

    @Test
    fun `reversed interval does not swallow a following one`() {
        // Before the fix, a reversed first interval made later ones look overlapping
        assertEquals(10L, totalForegroundTime(listOf(stat(0, -1000), stat(-500, -490))))
    }

    @Test
    fun `empty intervals are dropped`() {
        assertTrue(mergeOverlappingIntervals(listOf(stat(5, 5))).isEmpty())
        assertEquals(10L, totalForegroundTime(listOf(stat(5, 5), stat(10, 20))))
    }

    @Test
    fun `total is never negative`() {
        val stats = listOf(stat(10, 0), stat(3, 1), stat(7, 7), stat(Long.MAX_VALUE, 0))
        assertEquals(0L, totalForegroundTime(stats))
    }

    @Test
    fun `process names match their package exactly or with a process suffix`() {
        assertTrue(processBelongsToPackage("com.app", "com.app"))
        assertTrue(processBelongsToPackage("com.app:remote", "com.app"))
        assertFalse(processBelongsToPackage("com.apple", "com.app"))
        assertFalse(processBelongsToPackage("com.app.other", "com.app"))
        assertFalse(processBelongsToPackage("org.com.app", "com.app"))
        assertFalse(processBelongsToPackage("com", "com.app"))
    }
}
