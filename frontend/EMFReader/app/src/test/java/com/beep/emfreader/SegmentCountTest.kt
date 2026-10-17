package com.beep.emfreader

import com.beep.emfreader.ui.SegmentCount
import com.beep.emfreader.ui.segmentCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentCountTest {

    @Test
    fun `ten cells in total`() {
        assertEquals(10, SegmentCount)
    }

    @Test
    fun `zero lights nothing and a hundred lights everything`() {
        assertEquals(0, segmentCount(0))
        assertEquals(10, segmentCount(100))
    }

    @Test
    fun `each decile lights one more cell`() {
        assertEquals(1, segmentCount(10))
        assertEquals(2, segmentCount(20))
        assertEquals(3, segmentCount(30))
        assertEquals(4, segmentCount(40))
        assertEquals(5, segmentCount(50))
        assertEquals(6, segmentCount(60))
        assertEquals(9, segmentCount(90))
    }

    @Test
    fun `rounds half up so the meter never drops a cell`() {
        assertEquals(0, segmentCount(4))
        assertEquals(1, segmentCount(5))
        assertEquals(1, segmentCount(14))
        assertEquals(2, segmentCount(15))
        assertEquals(3, segmentCount(25))
        assertEquals(5, segmentCount(54))
        assertEquals(6, segmentCount(55))
        assertEquals(10, segmentCount(96))
    }

    @Test
    fun `rises one cell at a time with no repeats or backsteps`() {
        var previous = -1
        for (percent in 0..100) {
            val count = segmentCount(percent)
            assertTrue(
                "not monotonic at $percent: $previous then $count",
                count in previous..previous + 1
            )
            previous = count
        }
        assertEquals(10, previous)
    }

    @Test
    fun `clamps out of range input`() {
        assertEquals(0, segmentCount(-20))
        assertEquals(10, segmentCount(140))
    }
}
