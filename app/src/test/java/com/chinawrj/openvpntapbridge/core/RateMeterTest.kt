package com.chinawrj.openvpntapbridge.core

import org.junit.Assert.*
import org.junit.Test

/**
 * RateMeter unit tests
 */
class RateMeterTest {

    @Test
    fun `first sample returns null`() {
        val meter = RateMeter()
        val result = meter.sample(1000, 100, 50)
        assertNull("First sample should return null", result)
    }

    @Test
    fun `second sample calculates correct bps`() {
        val meter = RateMeter()

        // First sample: t=1000ms, rx=0, tx=0
        meter.sample(1000, 0, 0)

        // Second sample: t=2000ms, rx=1000 bytes, tx=500 bytes
        // Expected: rx_bps = 1000 * 8 * 1000 / 1000 = 8000 bps
        //           tx_bps = 500 * 8 * 1000 / 1000 = 4000 bps
        val result = meter.sample(2000, 1000, 500)

        assertNotNull("Second sample should return non-null", result)
        assertEquals("Download rate should be 8000 bps", 8000L, result!!.first)
        assertEquals("Upload rate should be 4000 bps", 4000L, result.second)
    }

    @Test
    fun `multiple samples work correctly`() {
        val meter = RateMeter()

        meter.sample(1000, 0, 0)

        // t=2000ms, rx=1000, tx=500
        var result = meter.sample(2000, 1000, 500)
        assertEquals(8000L, result!!.first)
        assertEquals(4000L, result.second)

        // t=3000ms, rx=3000 (+2000), tx=1500 (+1000)
        // rx_bps = 2000 * 8 * 1000 / 1000 = 16000
        // tx_bps = 1000 * 8 * 1000 / 1000 = 8000
        result = meter.sample(3000, 3000, 1500)
        assertEquals(16000L, result!!.first)
        assertEquals(8000L, result.second)
    }

    @Test
    fun `negative delta is coerced to zero`() {
        val meter = RateMeter()

        meter.sample(1000, 1000, 500)

        // Counter rollback (should not happen, but testing fault tolerance)
        val result = meter.sample(2000, 500, 200)

        assertNotNull(result)
        assertEquals("Negative delta should be coerced to 0", 0L, result!!.first)
        assertEquals("Negative delta should be coerced to 0", 0L, result.second)
    }

    @Test
    fun `zero time delta returns null`() {
        val meter = RateMeter()

        meter.sample(1000, 100, 50)

        // Same timestamp
        val result = meter.sample(1000, 200, 100)
        assertNull("Time delta of 0 should return null", result)
    }

    @Test
    fun `reset clears state`() {
        val meter = RateMeter()

        meter.sample(0, 0, 0)
        meter.sample(1000, 1000, 500)

        // Reset
        meter.reset()

        // First sample after reset should return null
        val result = meter.sample(2000, 2000, 1000)
        assertNull("First sample after reset should return null", result)
    }

    @Test
    fun `high speed calculation`() {
        val meter = RateMeter()

        meter.sample(1000, 0, 0)

        // 1 second transmits 100MB = 104857600 bytes
        // bps = 104857600 * 8 = 838860800 bps (~800 Mbps)
        val result = meter.sample(2000, 104857600, 52428800)

        assertNotNull(result)
        assertEquals(838860800L, result!!.first)
        assertEquals(419430400L, result.second)
    }
}
