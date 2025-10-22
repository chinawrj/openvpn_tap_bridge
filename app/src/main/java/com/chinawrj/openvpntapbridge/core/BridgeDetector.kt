package com.chinawrj.openvpntapbridge.core

import android.util.Log

/**
 * 网桥端口信息
 */
data class BridgePort(
    val name: String,
    val up: Boolean
)

/**
 * 桥接检测器
 * 检测网络接口是否被加入到网桥
 */
object BridgeDetector {
    private const val TAG = "BridgeDetector"

    /**
     * 检测接口是否被enslaved到指定网桥
     * @param iface 接口名（如 tap0）
     * @param bridge 网桥名（默认 br0）
     * @return true 如果接口在指定网桥中
     */
    fun isEnslavedTo(iface: String, bridge: String = "br0"): Boolean {
        // 路径: /sys/class/net/<iface>/brport/bridge
        // 这是一个符号链接，指向所属网桥的目录
        val brportPath = "/sys/class/net/$iface/brport/bridge"

        if (!FileReaders.exists(brportPath)) {
            Log.d(TAG, "Interface $iface is not in any bridge (brport not found)")
            return false
        }

        val target = FileReaders.readSymbolicLink(brportPath)
        val result = target.endsWith("/$bridge")

        Log.d(TAG, "Interface $iface bridge check: target=$target, in_$bridge=$result")
        return result
    }

    /**
     * 获取接口所属的网桥名称
     * @param iface 接口名
     * @return 网桥名称，如果不在任何网桥中则返回null
     */
    fun getBridgeName(iface: String): String? {
        val brportPath = "/sys/class/net/$iface/brport/bridge"

        if (!FileReaders.exists(brportPath)) {
            return null
        }

        val target = FileReaders.readSymbolicLink(brportPath)
        // 从完整路径中提取网桥名称
        // 如 "/sys/devices/virtual/net/br0" -> "br0"
        // 或 "../../../br0" -> "br0"
        val bridgeName = target.substringAfterLast('/')
        
        Log.d(TAG, "Interface $iface bridge: target=$target, name=$bridgeName")
        return bridgeName
    }

    /**
     * 获取网桥的所有端口
     * @param bridge 网桥名称（如 br0）
     * @return 端口列表，如果不是网桥或读取失败则返回空列表
     */
    fun getBridgePorts(bridge: String): List<BridgePort> {
        val brIfPath = "/sys/class/net/$bridge/brif"
        
        if (!FileReaders.exists(brIfPath)) {
            Log.d(TAG, "Bridge $bridge does not exist or has no ports")
            return emptyList()
        }

        // 读取 brif 目录下的所有端口
        val ports = FileReaders.listDirectory(brIfPath)
        
        if (ports.isEmpty()) {
            Log.d(TAG, "Bridge $bridge has no ports")
            return emptyList()
        }

        // 检查每个端口的状态
        val result = ports.mapNotNull { portName ->
            val operState = FileReaders.readTextSafe("/sys/class/net/$portName/operstate")
            val carrierText = FileReaders.readTextSafe("/sys/class/net/$portName/carrier")
            val carrier = carrierText == "1"
            
            // 当 operstate 为 unknown 时，使用 carrier 状态
            val isUp = if (operState == "unknown") carrier else (operState == "up")
            
            BridgePort(name = portName, up = isUp)
        }

        Log.d(TAG, "Bridge $bridge has ${result.size} ports: ${result.map { "${it.name}(${if (it.up) "UP" else "DOWN"})" }}")
        return result
    }
}
