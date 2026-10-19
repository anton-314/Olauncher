package app.olauncher.helper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class WallpaperDownloadTest {

    @Test
    fun `image no larger than the screen is not subsampled`() {
        assertEquals(1, calculateInSampleSize(1080, 2400, 1080, 2400))
        assertEquals(1, calculateInSampleSize(500, 500, 1080, 2400))
        assertEquals(1, calculateInSampleSize(2159, 4799, 1080, 2400))
    }

    @Test
    fun `large image is subsampled but stays at least screen sized`() {
        assertEquals(2, calculateInSampleSize(2160, 4800, 1080, 2400))
        assertEquals(4, calculateInSampleSize(6000, 12000, 1080, 2400))
        val sample = calculateInSampleSize(12000, 9000, 1080, 2400)
        assertEquals(2, sample)
        assert(12000 / sample >= 1080 && 9000 / sample >= 2400)
    }

    @Test
    fun `huge image is reduced to a few screens`() {
        val sample = calculateInSampleSize(60000, 60000, 1080, 2400)
        assertEquals(16, sample)
    }

    @Test
    fun `unknown or invalid sizes do not subsample`() {
        assertEquals(1, calculateInSampleSize(-1, -1, 1080, 2400)) // decode of bounds failed
        assertEquals(1, calculateInSampleSize(0, 0, 1080, 2400))
        assertEquals(1, calculateInSampleSize(4000, 4000, 0, 0))
        assertEquals(1, calculateInSampleSize(4000, 4000, -5, 100))
    }

    @Test
    fun `no overflow for extreme sizes`() {
        val sample = calculateInSampleSize(Int.MAX_VALUE, Int.MAX_VALUE, 1, 1)
        assert(sample > 1)
    }

    @Test
    fun `reads a stream within the limit completely`() {
        val data = ByteArray(20_000) { it.toByte() }
        assertArrayEquals(data, ByteArrayInputStream(data).readBounded(20_000))
        assertArrayEquals(ByteArray(0), ByteArrayInputStream(ByteArray(0)).readBounded(10))
    }

    @Test
    fun `stops and returns null as soon as the stream exceeds the limit`() {
        assertNull(ByteArrayInputStream(ByteArray(20_001)).readBounded(20_000))
        // An endless response must not be read until memory runs out
        var served = 0L
        val endless = object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                served += len
                return len
            }
        }
        assertNull(endless.readBounded(1_000_000))
        assert(served < 1_100_000)
    }
}
