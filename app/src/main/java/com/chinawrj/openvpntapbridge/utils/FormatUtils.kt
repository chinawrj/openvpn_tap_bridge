package com.chinawrj.openvpntapbridge.utils

import kotlin.math.abs

/**
 * Formatting utility class
 */
object FormatUtils {
    /**
     * Format rate (fixed use Mbps, keep 1 decimal place)
     * @param bps bits per second
     * @return formatted string, e.g. "1.2 Mbps"
     */
    fun formatBps(bps: Long): String {
        val mbps = bps / 1_000_000.0
        return String.format("%.1f Mbps", mbps)
    }

    /**
     * Format bytes to human-readable format
     * @param bytes byte count
     * @return formatted string, e.g. "1.5 MB"
     */
    fun formatBytes(bytes: Long): String {
        val absBytes = abs(bytes).toDouble()
        return when {
            absBytes >= 1_073_741_824 -> String.format("%.2f GB", absBytes / 1_073_741_824)
            absBytes >= 1_048_576 -> String.format("%.2f MB", absBytes / 1_048_576)
            absBytes >= 1_024 -> String.format("%.2f KB", absBytes / 1_024)
            else -> String.format("%d B", bytes)
        }
    }

    /**
     * Format packet count
     * @param packets packet count
     * @return formatted string (e.g. "1.2M")
     */
    fun formatPackets(packets: Long): String {
        val absPackets = abs(packets).toDouble()
        return when {
            absPackets >= 1_000_000_000 -> String.format("%.1fG", absPackets / 1_000_000_000)
            absPackets >= 1_000_000 -> String.format("%.1fM", absPackets / 1_000_000)
            absPackets >= 1_000 -> String.format("%.1fK", absPackets / 1_000)
            else -> packets.toString()
        }
    }
}
