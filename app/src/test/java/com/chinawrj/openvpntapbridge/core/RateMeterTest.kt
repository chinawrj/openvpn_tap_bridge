package com.chinawrj.openvpntapbridge.core

import org.junit.Assert.*
import org.junit.Test

/**
 * RateMeter 单元测试
 */
class RateMeterTest {

    @Test
    fun `first sample returns null`() {
        val meter = RateMeter()
        val result = meter.sample(1000, 100, 50)
        assertNull("首次采样应该返回null", result)
    }

    @Test
    fun `second sample calculates correct bps`() {
        val meter = RateMeter()

        // 第一次采样: t=1000ms, rx=0, tx=0
        meter.sample(1000, 0, 0)

        // 第二次采样: t=2000ms, rx=1000字节, tx=500字节
        // 期望: rx_bps = 1000 * 8 * 1000 / 1000 = 8000 bps
        //       tx_bps = 500 * 8 * 1000 / 1000 = 4000 bps
        val result = meter.sample(2000, 1000, 500)

        assertNotNull("第二次采样应该返回非null", result)
        assertEquals("下行速率应为8000 bps", 8000L, result!!.first)
        assertEquals("上行速率应为4000 bps", 4000L, result.second)
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

        // 计数器回退（不应该发生，但要测试容错）
        val result = meter.sample(2000, 500, 200)

        assertNotNull(result)
        assertEquals("负增量应被限制为0", 0L, result!!.first)
        assertEquals("负增量应被限制为0", 0L, result.second)
    }

    @Test
    fun `zero time delta returns null`() {
        val meter = RateMeter()

        meter.sample(1000, 100, 50)

        // 相同时间戳
        val result = meter.sample(1000, 200, 100)
        assertNull("时间差为0应返回null", result)
    }

    @Test
    fun `reset clears state`() {
        val meter = RateMeter()

        meter.sample(0, 0, 0)
        meter.sample(1000, 1000, 500)

        // 重置
        meter.reset()

        // 重置后首次采样应返回null
        val result = meter.sample(2000, 2000, 1000)
        assertNull("重置后首次采样应返回null", result)
    }

    @Test
    fun `high speed calculation`() {
        val meter = RateMeter()

        meter.sample(1000, 0, 0)

        // 1秒传输100MB = 104857600字节
        // bps = 104857600 * 8 = 838860800 bps (~800 Mbps)
        val result = meter.sample(2000, 104857600, 52428800)

        assertNotNull(result)
        assertEquals(838860800L, result!!.first)
        assertEquals(419430400L, result.second)
    }
}
