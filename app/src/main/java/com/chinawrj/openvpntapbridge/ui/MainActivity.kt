package com.chinawrj.openvpntapbridge.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chinawrj.openvpntapbridge.R
import com.chinawrj.openvpntapbridge.core.FileReaders
import com.chinawrj.openvpntapbridge.core.IfaceMonitor
import com.chinawrj.openvpntapbridge.core.UiModel
import com.chinawrj.openvpntapbridge.data.AppPreferences
import com.chinawrj.openvpntapbridge.service.ForegroundSamplerService
import com.chinawrj.openvpntapbridge.utils.FormatUtils
import com.google.android.material.card.MaterialCardView

/**
 * Main activity
 * Display network interface status and rate information
 */
class MainActivity : AppCompatActivity() {
    private lateinit var prefs: AppPreferences
    private var monitor: IfaceMonitor? = null

    // UI components
    private lateinit var tvInterfaceName: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvCarrier: TextView
    private lateinit var tvBridge: TextView
    private lateinit var tvDefaultRoute: TextView
    private lateinit var tvRxSpeed: TextView
    private lateinit var tvTxSpeed: TextView
    private lateinit var tvRxPackets: TextView
    private lateinit var tvTxPackets: TextView
    private lateinit var cardBridgePorts: MaterialCardView
    private lateinit var layoutBridgePorts: LinearLayout
    private lateinit var tvMessage: TextView

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize configuration
        prefs = AppPreferences.getInstance(this)

        // Bind views
        bindViews()

        // Request notification permission (Android 13+)
        requestNotificationPermissionIfNeeded()

        // Start monitoring
        startMonitoring()
    }

    override fun onDestroy() {
        super.onDestroy()
        monitor?.stop()
        // Close root shell session
        FileReaders.closeRootShell()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_start_service -> {
                ForegroundSamplerService.start(this)
                Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_stop_service -> {
                ForegroundSamplerService.stop(this)
                Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun bindViews() {
        tvInterfaceName = findViewById(R.id.tvInterfaceName)
        tvStatus = findViewById(R.id.tvStatus)
        tvCarrier = findViewById(R.id.tvCarrier)
        tvBridge = findViewById(R.id.tvBridge)
        tvDefaultRoute = findViewById(R.id.tvDefaultRoute)
        tvRxSpeed = findViewById(R.id.tvRxSpeed)
        tvTxSpeed = findViewById(R.id.tvTxSpeed)
        tvRxPackets = findViewById(R.id.tvRxPackets)
        tvTxPackets = findViewById(R.id.tvTxPackets)
        cardBridgePorts = findViewById(R.id.cardBridgePorts)
        layoutBridgePorts = findViewById(R.id.layoutBridgePorts)
        tvMessage = findViewById(R.id.tvMessage)
    }

    private fun startMonitoring() {
        monitor = IfaceMonitor(
            iface = { prefs.interfaceName },
            onUpdate = { model -> updateUI(model) },
            scope = lifecycleScope
        )
        monitor?.start()
    }

    private fun updateUI(model: UiModel) {
        // Update interface name
        tvInterfaceName.text = prefs.interfaceName

        if (!model.exists) {
            // Interface does not exist
            showMessage(getString(R.string.interface_waiting))
            setFieldsEnabled(false)
            cardBridgePorts.visibility = View.GONE
            return
        }

        // Interface exists, hide message
        hideMessage()
        setFieldsEnabled(true)

        // Update status
        val statusText: String
        val statusColor: Int
        if (model.up && model.carrier) {
            statusText = "● ${getString(R.string.status_up)}"
            statusColor = Color.parseColor("#4CAF50") // Green
        } else {
            statusText = "○ ${getString(R.string.status_down)}"
            statusColor = Color.parseColor("#9E9E9E") // Gray
        }
        tvStatus.text = statusText
        tvStatus.setTextColor(statusColor)

        // Update carrier
        tvCarrier.text = if (model.carrier) {
            getString(R.string.carrier_on)
        } else {
            getString(R.string.carrier_off)
        }

        // Update bridge
        tvBridge.text = if (model.inBridge && model.bridgeName != null) {
            getString(R.string.in_bridge, model.bridgeName)
        } else {
            getString(R.string.not_in_bridge)
        }

        // Update default route
        tvDefaultRoute.text = if (model.isDefaultRoute) {
            getString(R.string.is_default_route)
        } else {
            getString(R.string.not_default_route)
        }

        // Update rate
        tvRxSpeed.text = model.rxBps?.let { FormatUtils.formatBps(it) }
            ?: getString(R.string.speed_calculating)

        tvTxSpeed.text = model.txBps?.let { FormatUtils.formatBps(it) }
            ?: getString(R.string.speed_calculating)

        // Update cumulative traffic (bytes)
        tvRxPackets.text = FormatUtils.formatBytes(model.rxBytes)
        tvTxPackets.text = FormatUtils.formatBytes(model.txBytes)

        // Update bridge port list
        updateBridgePorts(model)
    }

    private fun updateBridgePorts(model: UiModel) {
        if (model.bridgePorts.isEmpty()) {
            cardBridgePorts.visibility = View.GONE
            return
        }

        cardBridgePorts.visibility = View.VISIBLE
        layoutBridgePorts.removeAllViews()

        model.bridgePorts.forEach { port ->
            // Create single-line layout
            val portView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8.dpToPx()
                }
            }

            // Port name
            val nameTextView = TextView(this).apply {
                text = port.name
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            // Status
            val statusTextView = TextView(this).apply {
                val statusText = if (port.up) {
                    getString(R.string.port_up)
                } else {
                    getString(R.string.port_down)
                }
                val statusColor = if (port.up) {
                    Color.parseColor("#4CAF50") // Green
                } else {
                    Color.parseColor("#9E9E9E") // Gray
                }

                text = statusText
                setTextColor(statusColor)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            portView.addView(nameTextView)
            portView.addView(statusTextView)
            layoutBridgePorts.addView(portView)
        }
    }

    // Extension function: dp to px
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun showMessage(message: String) {
        tvMessage.text = message
        tvMessage.visibility = View.VISIBLE
    }

    private fun hideMessage() {
        tvMessage.visibility = View.GONE
    }

    private fun setFieldsEnabled(enabled: Boolean) {
        val alpha = if (enabled) 1.0f else 0.5f
        tvStatus.alpha = alpha
        tvCarrier.alpha = alpha
        tvBridge.alpha = alpha
        tvDefaultRoute.alpha = alpha
        tvRxSpeed.alpha = alpha
        tvTxSpeed.alpha = alpha
        tvRxPackets.alpha = alpha
        tvTxPackets.alpha = alpha
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
            }
        }
    }
}