package com.chinawrj.openvpntapbridge.core

import android.util.Log

/**
 * Interface status snapshot
 */
data class IfaceSnapshot(
    val exists: Boolean,          // Whether interface exists
    val up: Boolean,              // Whether interface is UP
    val carrier: Boolean,         // Whether has carrier
    val rxBytes: Long,            // Accumulated received bytes
    val txBytes: Long,            // Accumulated transmitted bytes
    val rxPackets: Long,          // Accumulated received packets
    val txPackets: Long,          // Accumulated transmitted packets
    val inBridge: Boolean,        // Whether in bridge
    val bridgeName: String?,      // Bridge name it belongs to
    val isDefaultRoute: Boolean,  // Whether carries default route
    val bridgePorts: List<BridgePort>  // All ports of the bridge (only when interface is a bridge)
)

/**
 * Interface status reader
 * Read interface information under /sys/class/net
 */
object IfaceReader {
    private const val TAG = "IfaceReader"

    /**
     * Read complete status snapshot of specified interface
     * @param iface Interface name (e.g. tap0)
     * @return Interface status snapshot
     */
    fun read(iface: String): IfaceSnapshot {
        val basePath = "/sys/class/net/$iface"

        // Check if interface exists
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

        // Read operstate (up/down/unknown)
        val operState = FileReaders.readTextSafe("$basePath/operstate")

        // Read carrier (1/0)
        val carrierText = FileReaders.readTextSafe("$basePath/carrier")
        val carrier = carrierText == "1"

        // When operstate is unknown, use carrier status
        val up = if (operState == "unknown") carrier else (operState == "up")

        // Read statistics
        val rxBytes = FileReaders.readLongSafe("$basePath/statistics/rx_bytes")
        val txBytes = FileReaders.readLongSafe("$basePath/statistics/tx_bytes")
        val rxPackets = FileReaders.readLongSafe("$basePath/statistics/rx_packets")
        val txPackets = FileReaders.readLongSafe("$basePath/statistics/tx_packets")

        // Detect bridge status
        val bridgeName = BridgeDetector.getBridgeName(iface)
        val inBridge = bridgeName != null

        // Detect default route
        val isDefaultRoute = RouteParser.parseIsDefaultVia(iface)

        // If interface is in a bridge, read all ports of the bridge
        val bridgePorts = if (bridgeName != null) {
            BridgeDetector.getBridgePorts(bridgeName)
        } else {
            emptyList()
        }

        Log.d(
            TAG, "Interface $iface: exists=true, operState=$operState, up=$up, carrier=$carrier, " +
                    "rx=${rxBytes}B/${rxPackets}pkt, tx=${txBytes}B/${txPackets}pkt, " +
                    "bridge=$bridgeName, default=$isDefaultRoute, ports=${bridgePorts.size}"
        )

        return IfaceSnapshot(
            exists = true,
            up = up,
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
