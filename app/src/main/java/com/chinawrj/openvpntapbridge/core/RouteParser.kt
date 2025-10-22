package com.chinawrj.openvpntapbridge.core

import android.util.Log

/**
 * 路由表解析器
 * 解析 /proc/net/route 检测默认路由
 */
object RouteParser {
    private const val TAG = "RouteParser"
    private const val ROUTE_FILE = "/proc/net/route"

    /**
     * 检测指定接口是否承载默认路由
     * @param iface 接口名（如 tap0）
     * @return true 如果该接口承载默认路由
     */
    fun parseIsDefaultVia(iface: String): Boolean {
        val content = FileReaders.readTextSafe(ROUTE_FILE)
        if (content.isEmpty()) {
            Log.d(TAG, "Failed to read route file")
            return false
        }

        val lines = content.split('\n')
        // 跳过表头
        // 格式: Iface Destination Gateway Flags RefCnt Use Metric Mask MTU Window IRTT
        for (i in 1 until lines.size) {
            val cols = lines[i].split('\t', ' ').filter { it.isNotEmpty() }
            if (cols.size < 4) continue

            val ifName = cols[0]
            val destination = cols[1]
            val flagsHex = cols[3]

            // 解析标志位
            val flags = runCatching { flagsHex.toInt(16) }.getOrDefault(0)

            // 00000000 表示默认路由 (0.0.0.0)
            val isDefault = (destination == "00000000")
            // 检查 U (Up) 和 G (Gateway) 标志
            // U = 0x0001, G = 0x0002
            val hasUG = (flags and 0x3) == 0x3

            if (isDefault && hasUG && ifName == iface) {
                Log.d(TAG, "Found default route via $iface")
                return true
            }
        }

        Log.d(TAG, "No default route via $iface")
        return false
    }
}
