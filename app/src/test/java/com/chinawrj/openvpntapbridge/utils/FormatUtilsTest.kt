package com.chinawrj.openvpntapbridge.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * FormatUtils unit tests
 */
class FormatUtilsTest {

    @Test
    fun `format bps - bps range`() {
        assertEquals("500 bps", FormatUtils.formatBps(500))
        assertEquals("999 bps", FormatUtils.formatBps(999))
    }

    @Test
    fun `format bps - Kbps range`() {
        assertEquals("1.00 Kbps", FormatUtils.formatBps(1_000))
        assertEquals("10.50 Kbps", FormatUtils.formatBps(10_500))
        assertEquals("999.00 Kbps", FormatUtils.formatBps(999_000))
    }

    @Test
    fun `format bps - Mbps range`() {
        assertEquals("1.00 Mbps", FormatUtils.formatBps(1_000_000))
        assertEquals("10.50 Mbps", FormatUtils.formatBps(10_500_000))
        assertEquals("100.00 Mbps", FormatUtils.formatBps(100_000_000))
    }

    @Test
    fun `format bps - Gbps range`() {
        assertEquals("1.00 Gbps", FormatUtils.formatBps(1_000_000_000))
        assertEquals("10.50 Gbps", FormatUtils.formatBps(10_500_000_000))
    }

    @Test
    fun `format bps - zero`() {
        assertEquals("0 bps", FormatUtils.formatBps(0))
    }

    @Test
    fun `format bytes - bytes range`() {
        assertEquals("500 B", FormatUtils.formatBytes(500))
        assertEquals("1023 B", FormatUtils.formatBytes(1023))
    }

    @Test
    fun `format bytes - KB range`() {
        assertEquals("1.00 KB", FormatUtils.formatBytes(1_024))
        assertEquals("10.50 KB", FormatUtils.formatBytes(10_752)) // 10.5 * 1024
    }

    @Test
    fun `format bytes - MB range`() {
        assertEquals("1.00 MB", FormatUtils.formatBytes(1_048_576))
        assertEquals("100.00 MB", FormatUtils.formatBytes(104_857_600))
    }

    @Test
    fun `format bytes - GB range`() {
        assertEquals("1.00 GB", FormatUtils.formatBytes(1_073_741_824))
        assertEquals("10.00 GB", FormatUtils.formatBytes(10_737_418_240))
    }
}
