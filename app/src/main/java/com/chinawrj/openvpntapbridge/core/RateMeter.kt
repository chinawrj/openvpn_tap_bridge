package com.chinawrj.openvpntapbridge.core

/**
 * 速率计算器
 * 根据两次采样的时间和累积字节数计算瞬时速率（bps）
 */
class RateMeter {
    private var lastTimeMs = 0L
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L

    /**
     * 采样并计算速率
     * @param nowMs 当前时间戳（毫秒）
     * @param rxBytes 累积接收字节数
     * @param txBytes 累积发送字节数
     * @return Pair(rx_bps, tx_bps)，首次采样返回null
     */
    fun sample(nowMs: Long, rxBytes: Long, txBytes: Long): Pair<Long, Long>? {
        // 首次采样，只记录基准值
        if (lastTimeMs == 0L) {
            lastTimeMs = nowMs
            lastRxBytes = rxBytes
            lastTxBytes = txBytes
            return null
        }

        val deltaTimeMs = nowMs - lastTimeMs
        if (deltaTimeMs <= 0) {
            // 时间无效，不更新
            return null
        }

        // 计算字节差值（确保非负）
        val deltaRxBytes = (rxBytes - lastRxBytes).coerceAtLeast(0)
        val deltaTxBytes = (txBytes - lastTxBytes).coerceAtLeast(0)

        // 计算 bps = (字节差 * 8 * 1000) / 时间差(ms)
        val rxBps = (deltaRxBytes * 8L * 1000L) / deltaTimeMs
        val txBps = (deltaTxBytes * 8L * 1000L) / deltaTimeMs

        // 更新基准值
        lastTimeMs = nowMs
        lastRxBytes = rxBytes
        lastTxBytes = txBytes

        return rxBps to txBps
    }

    /**
     * 重置计量器状态
     * 用于接口重建或切换时清零
     */
    fun reset() {
        lastTimeMs = 0L
        lastRxBytes = 0L
        lastTxBytes = 0L
    }
}
