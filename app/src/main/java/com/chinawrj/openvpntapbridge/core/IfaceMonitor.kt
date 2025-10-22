package com.chinawrj.openvpntapbridge.core

import android.util.Log
import kotlinx.coroutines.*

/**
 * UI data model
 */
data class UiModel(
    val exists: Boolean,          // Whether interface exists
    val up: Boolean,              // Whether interface is UP
    val carrier: Boolean,         // Whether carrier is present
    val inBridge: Boolean,        // Whether in bridge
    val bridgeName: String?,      // Bridge name it belongs to
    val isDefaultRoute: Boolean,  // Whether it carries default route
    val rxBps: Long?,             // Receive rate (bps), null for first sampling
    val txBps: Long?,             // Transmit rate (bps), null for first sampling
    val rxBytes: Long,            // Cumulative received bytes
    val txBytes: Long,            // Cumulative transmitted bytes
    val bridgePorts: List<BridgePort>  // All ports of the bridge
)

/**
 * Interface monitor
 * Periodically samples network interface status and pushes to UI
 */
class IfaceMonitor(
    private val iface: () -> String,           // Support dynamic interface name changes
    private val onUpdate: (UiModel) -> Unit,   // Callback to push to UI
    private val scope: CoroutineScope          // Coroutine scope
) {
    private val TAG = "IfaceMonitor"
    private val rateMeter = RateMeter()
    private var job: Job? = null
    private var wasExists = false

    /**
     * Start monitoring
     * @param pollMsActive Polling interval (milliseconds) when interface exists and is active
     * @param pollMsIdle Polling interval (milliseconds) when interface does not exist or is inactive
     */
    fun start(pollMsActive: Long = 1000, pollMsIdle: Long = 2500) {
        stop()

        job = scope.launch(Dispatchers.IO) {
            Log.d(TAG, "Monitor started for interface: ${iface()}")

            while (isActive) {
                val currentIface = iface()
                val snapshot = IfaceReader.read(currentIface)
                val now = System.currentTimeMillis()

                // If interface changed from non-existent to existent, or from existent to non-existent, reset rate meter
                if (snapshot.exists != wasExists) {
                    Log.d(TAG, "Interface state changed: exists=${snapshot.exists}")
                    rateMeter.reset()
                    wasExists = snapshot.exists
                }

                // Calculate rate
                val bps = if (snapshot.exists) {
                    rateMeter.sample(now, snapshot.rxBytes, snapshot.txBytes)
                } else {
                    rateMeter.reset()
                    null
                }

                // Build UI model
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

                // Push to UI
                withContext(Dispatchers.Main) {
                    onUpdate(uiModel)
                }

                // Decide next polling interval based on interface status
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
     * Stop monitoring
     */
    fun stop() {
        job?.cancel()
        job = null
        rateMeter.reset()
        wasExists = false
        Log.d(TAG, "Monitor stop requested")
    }

    /**
     * Whether currently running
     */
    fun isRunning(): Boolean = job?.isActive == true
}
