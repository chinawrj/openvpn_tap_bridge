package com.chinawrj.openvpntapbridge.core

import android.util.Log

/**
 * Route table parser
 * Parse /proc/net/route to detect default route
 */
object RouteParser {
    private const val TAG = "RouteParser"
    private const val ROUTE_FILE = "/proc/net/route"

    /**
     * Detect if specified interface carries default route
     * @param iface Interface name (e.g. tap0)
     * @return true if this interface carries default route
     */
    fun parseIsDefaultVia(iface: String): Boolean {
        val content = FileReaders.readTextSafe(ROUTE_FILE)
        if (content.isEmpty()) {
            Log.d(TAG, "Failed to read route file")
            return false
        }

        val lines = content.split('\n')
        // Skip header
        // Format: Iface Destination Gateway Flags RefCnt Use Metric Mask MTU Window IRTT
        for (i in 1 until lines.size) {
            val cols = lines[i].split('\t', ' ').filter { it.isNotEmpty() }
            if (cols.size < 4) continue

            val ifName = cols[0]
            val destination = cols[1]
            val flagsHex = cols[3]

            // Parse flags
            val flags = runCatching { flagsHex.toInt(16) }.getOrDefault(0)

            // 00000000 indicates default route (0.0.0.0)
            val isDefault = (destination == "00000000")
            // Check U (Up) and G (Gateway) flags
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
