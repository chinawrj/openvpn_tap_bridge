package com.chinawrj.openvpntapbridge.core

import android.util.Log

/**
 * 接口状态快照
 */
data class IfaceSnapshot(
    val exists: Boolean,          // 接口是否存在
    val up: Boolean,              // 接口是否UP
    val carrier: Boolean,         // 是否有载波
    val rxBytes: Long,            // 累积接收字节数
    val txBytes: Long,            // 累积发送字节数
    val rxPackets: Long,          // 累积接收包数
    val txPackets: Long,          // 累积发送包数
    val inBridge: Boolean,        // 是否在网桥中
    val bridgeName: String?,      // 所属网桥名称
    val isDefaultRoute: Boolean,  // 是否承载默认路由
    val bridgePorts: List<BridgePort>  // 网桥的所有端口（仅当接口是网桥时）
)

/**
 * 接口状态读取器
 * 读取 /sys/class/net 下的接口信息
 */
object IfaceReader {
    private const val TAG = "IfaceReader"

    /**
     * 读取指定接口的完整状态快照
     * @param iface 接口名称（如 tap0）
     * @return 接口状态快照
     */
    fun read(iface: String): IfaceSnapshot {
        val basePath = "/sys/class/net/$iface"

        // 检查接口是否存在
        if (!FileReaders.exists(basePath)) {
            Log.d(TAG, "Interface $iface does not exist")
            return IfaceSnapshot(
                exists = false,
                up = false,
                carrier = false,
                rxBytes = 0,
                txBytes = 0,
                rxPackets = 0,
                txPackets = 0,
                inBridge = false,
                bridgeName = null,
                isDefaultRoute = false,
                bridgePorts = emptyList()
            )
        }

        // 读取 operstate (up/down/unknown)
        val operState = FileReaders.readTextSafe("$basePath/operstate")
        val up = operState == "up"

        // 读取 carrier (1/0)
        val carrierText = FileReaders.readTextSafe("$basePath/carrier")
        val carrier = carrierText == "1"

        // 当 operstate 为 unknown 时，使用 carrier 状态
        val isUp = if (operState == "unknown") carrier else up

        // 读取统计数据
        val rxBytes = FileReaders.readLongSafe("$basePath/statistics/rx_bytes")
        val txBytes = FileReaders.readLongSafe("$basePath/statistics/tx_bytes")
        val rxPackets = FileReaders.readLongSafe("$basePath/statistics/rx_packets")
        val txPackets = FileReaders.readLongSafe("$basePath/statistics/tx_packets")

        // 检测桥接状态
        val bridgeName = BridgeDetector.getBridgeName(iface)
        val inBridge = bridgeName != null

        // 检测默认路由
        val isDefaultRoute = RouteParser.parseIsDefaultVia(iface)

        // 如果接口在网桥中，读取网桥的所有端口
        val bridgePorts = if (bridgeName != null) {
            BridgeDetector.getBridgePorts(bridgeName)
        } else {
            emptyList()
        }

        Log.d(
            TAG, "Interface $iface: exists=true, operState=$operState, up=$isUp, carrier=$carrier, " +
                    "rx=${rxBytes}B/${rxPackets}pkt, tx=${txBytes}B/${txPackets}pkt, " +
                    "bridge=$bridgeName, default=$isDefaultRoute, ports=${bridgePorts.size}"
        )

        return IfaceSnapshot(
            exists = true,
            up = isUp,
            carrier = carrier,
            rxBytes = rxBytes,
            txBytes = txBytes,
            rxPackets = rxPackets,
            txPackets = txPackets,
            inBridge = inBridge,
            bridgeName = bridgeName,
            isDefaultRoute = isDefaultRoute,
            bridgePorts = bridgePorts
        )
    }
}
