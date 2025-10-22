package com.chinawrj.openvpntapbridge.core

/**
 * Rate calculator
 * Calculate instantaneous rate (bps) based on two samplings of time and cumulative bytes
 */
class RateMeter {
    private var lastTimeMs = 0L
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L

    /**
     * Sample and calculate rate
     * @param nowMs Current timestamp (milliseconds)
     * @param rxBytes Cumulative received bytes
     * @param txBytes Cumulative transmitted bytes
     * @return Pair(rx_bps, tx_bps), returns null for first sampling
     */
    fun sample(nowMs: Long, rxBytes: Long, txBytes: Long): Pair<Long, Long>? {
        // First sampling, only record baseline values
        if (lastTimeMs == 0L) {
            lastTimeMs = nowMs
            lastRxBytes = rxBytes
            lastTxBytes = txBytes
            return null
        }

        val deltaTimeMs = nowMs - lastTimeMs
        if (deltaTimeMs <= 0) {
            // Invalid time, do not update
            return null
        }

        // Calculate byte difference (ensure non-negative)
        val deltaRxBytes = (rxBytes - lastRxBytes).coerceAtLeast(0)
        val deltaTxBytes = (txBytes - lastTxBytes).coerceAtLeast(0)

        // Calculate bps = (byte_diff * 8 * 1000) / time_diff(ms)
        val rxBps = (deltaRxBytes * 8L * 1000L) / deltaTimeMs
        val txBps = (deltaTxBytes * 8L * 1000L) / deltaTimeMs

        // Update baseline values
        lastTimeMs = nowMs
        lastRxBytes = rxBytes
        lastTxBytes = txBytes

        return rxBps to txBps
    }

    /**
     * Reset meter state
     * Used to reset when interface is rebuilt or switched
     */
    fun reset() {
        lastTimeMs = 0L
        lastRxBytes = 0L
        lastTxBytes = 0L
    }
}
