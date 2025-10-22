package com.chinawrj.openvpntapbridge.utils

import kotlin.math.abs

/**
 * 格式化工具类
 */
object FormatUtils {
    /**
     * 格式化速率（固定使用 Mbps，保留1位小数）
     * @param bps 比特每秒
     * @return 格式化字符串，如 "1.2 Mbps"
     */
    fun formatBps(bps: Long): String {
        val mbps = bps / 1_000_000.0
        return String.format("%.1f Mbps", mbps)
    }

    /**
     * 格式化字节数为人类可读格式
     * @param bytes 字节数
     * @return 格式化字符串，如 "1.5 MB"
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
     * 格式化包数量
     * @param packets 包数量
     * @return 格式化后的字符串（如 "1.2M"）
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
