package com.chinawrj.openvpntapbridge.core

import android.util.Log

/**
 * Bridge port information
 */
data class BridgePort(
    val name: String,
    val up: Boolean
)

/**
 * Bridge detector
 * Detect if network interface is added to a bridge
 */
object BridgeDetector {
    private const val TAG = "BridgeDetector"

    /**
     * Detect if interface is enslaved to specified bridge
     * @param iface Interface name (e.g. tap0)
     * @param bridge Bridge name (default br0)
     * @return true if interface is in specified bridge
     */
    fun isEnslavedTo(iface: String, bridge: String = "br0"): Boolean {
        // Path: /sys/class/net/<iface>/brport/bridge
        // This is a symbolic link pointing to the bridge directory it belongs to
        val brportPath = "/sys/class/net/$iface/brport/bridge"

        if (!FileReaders.exists(brportPath)) {
            Log.d(TAG, "Interface $iface is not in any bridge (brport not found)")
            return false
        }

        val target = FileReaders.readlink(brportPath) ?: return false
        val result = target.endsWith("/$bridge")

        Log.d(TAG, "Interface $iface bridge check: target=$target, in_$bridge=$result")
        return result
    }

    /**
     * Get the bridge name that the interface belongs to
     * @param iface Interface name
     * @return Bridge name, or null if not in any bridge
     */
    fun getBridgeName(iface: String): String? {
        val brportPath = "/sys/class/net/$iface/brport/bridge"

        if (!FileReaders.exists(brportPath)) {
            return null
        }

        val target = FileReaders.readlink(brportPath) ?: return null
        // Extract bridge name from full path
        // e.g. "/sys/devices/virtual/net/br0" -> "br0"
        // or "../../../br0" -> "br0"
        val bridgeName = target.substringAfterLast('/')
        
        Log.d(TAG, "Interface $iface bridge: target=$target, name=$bridgeName")
        return bridgeName
    }

    /**
     * Get all ports of the bridge
     * @param bridge Bridge name (e.g. br0)
     * @return Port list, or empty list if not a bridge or read failed
     */
    fun getBridgePorts(bridge: String): List<BridgePort> {
        val brIfPath = "/sys/class/net/$bridge/brif"
        
        if (!FileReaders.exists(brIfPath)) {
            Log.d(TAG, "Bridge $bridge does not exist or has no ports")
            return emptyList()
        }

        // Read all ports under brif directory
        val ports = FileReaders.listDir(brIfPath)
        
        if (ports.isEmpty()) {
            Log.d(TAG, "Bridge $bridge has no ports")
            return emptyList()
        }

        // Check status of each port
        val result = ports.mapNotNull { portName ->
            val operState = FileReaders.readTextSafe("/sys/class/net/$portName/operstate")
            val carrierText = FileReaders.readTextSafe("/sys/class/net/$portName/carrier")
            val carrier = carrierText == "1"
            
            // When operstate is unknown, use carrier status
            val isUp = if (operState == "unknown") carrier else (operState == "up")
            
            BridgePort(name = portName, up = isUp)
        }

        Log.d(TAG, "Bridge $bridge has ${result.size} ports: ${result.map { "${it.name}(${if (it.up) "UP" else "DOWN"})" }}")
        return result
    }
}
