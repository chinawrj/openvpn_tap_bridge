package com.chinawrj.openvpntapbridge.core

import android.util.Log
import kotlinx.coroutines.*

/**
 * UI数据模型
 */
data class UiModel(
    val exists: Boolean,          // 接口是否存在
    val up: Boolean,              // 接口是否UP
    val carrier: Boolean,         // 是否有载波
    val inBridge: Boolean,        // 是否在网桥中
    val bridgeName: String?,      // 所属网桥名称
    val isDefaultRoute: Boolean,  // 是否承载默认路由
    val rxBps: Long?,             // 接收速率 (bps)，null表示首次采样
    val txBps: Long?,             // 发送速率 (bps)，null表示首次采样
    val rxBytes: Long,            // 累积接收字节数
    val txBytes: Long,            // 累积发送字节数
    val bridgePorts: List<BridgePort>  // 网桥的所有端口
)

/**
 * 接口监控器
 * 负责周期性采样网络接口状态并推送到UI
 */
class IfaceMonitor(
    private val iface: () -> String,           // 支持动态修改接口名
    private val onUpdate: (UiModel) -> Unit,   // 推送到UI的回调
    private val scope: CoroutineScope          // 协程作用域
) {
    private val TAG = "IfaceMonitor"
    private val rateMeter = RateMeter()
    private var job: Job? = null
    private var wasExists = false

    /**
     * 启动监控
     * @param pollMsActive 接口存在且活跃时的轮询间隔（毫秒）
     * @param pollMsIdle 接口不存在或不活跃时的轮询间隔（毫秒）
     */
    fun start(pollMsActive: Long = 1000, pollMsIdle: Long = 2500) {
        stop()

        job = scope.launch(Dispatchers.IO) {
            Log.d(TAG, "Monitor started for interface: ${iface()}")

            while (isActive) {
                val currentIface = iface()
                val snapshot = IfaceReader.read(currentIface)
                val now = System.currentTimeMillis()

                // 如果接口从不存在变为存在，或从存在变为不存在，重置速率计量器
                if (snapshot.exists != wasExists) {
                    Log.d(TAG, "Interface state changed: exists=${snapshot.exists}")
                    rateMeter.reset()
                    wasExists = snapshot.exists
                }

                // 计算速率
                val bps = if (snapshot.exists) {
                    rateMeter.sample(now, snapshot.rxBytes, snapshot.txBytes)
                } else {
                    rateMeter.reset()
                    null
                }

                // 构建UI模型
                val uiModel = UiModel(
                    exists = snapshot.exists,
                    up = snapshot.up,
                    carrier = snapshot.carrier,
                    inBridge = snapshot.inBridge,
                    bridgeName = snapshot.bridgeName,
                    isDefaultRoute = snapshot.isDefaultRoute,
                    rxBps = bps?.first,
                    txBps = bps?.second,
                    rxBytes = snapshot.rxBytes,
                    txBytes = snapshot.txBytes,
                    bridgePorts = snapshot.bridgePorts
                )

                // 推送到UI
                withContext(Dispatchers.Main) {
                    onUpdate(uiModel)
                }

                // 根据接口状态决定下次轮询间隔
                val nextPollMs = if (snapshot.exists && (snapshot.up || snapshot.carrier)) {
                    pollMsActive
                } else {
                    pollMsIdle
                }

                delay(nextPollMs)
            }

            Log.d(TAG, "Monitor stopped")
        }
    }

    /**
     * 停止监控
     */
    fun stop() {
        job?.cancel()
        job = null
        rateMeter.reset()
        wasExists = false
        Log.d(TAG, "Monitor stop requested")
    }

    /**
     * 是否正在运行
     */
    fun isRunning(): Boolean = job?.isActive == true
}
