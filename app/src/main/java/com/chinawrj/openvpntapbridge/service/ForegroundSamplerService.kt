package com.chinawrj.openvpntapbridge.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.chinawrj.openvpntapbridge.R
import com.chinawrj.openvpntapbridge.core.IfaceMonitor
import com.chinawrj.openvpntapbridge.core.UiModel
import com.chinawrj.openvpntapbridge.data.AppPreferences
import com.chinawrj.openvpntapbridge.ui.MainActivity
import com.chinawrj.openvpntapbridge.utils.FormatUtils

/**
 * 前台服务，保持应用常驻，持续监控网络接口
 */
class ForegroundSamplerService : LifecycleService() {
    private lateinit var prefs: AppPreferences
    private var monitor: IfaceMonitor? = null
    private var currentModel: UiModel? = null

    companion object {
        private const val TAG = "ForegroundService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "network_monitor_channel"

        fun start(context: Context) {
            val intent = Intent(context, ForegroundSamplerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ForegroundSamplerService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        prefs = AppPreferences.getInstance(this)
        createNotificationChannel()

        // 启动前台通知（Android 9+必须在5秒内显示）
        startForeground(NOTIFICATION_ID, buildNotification("正在启动..."))

        // 启动监控器
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "Service onStartCommand")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        monitor?.stop()
        monitor = null
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun startMonitoring() {
        monitor = IfaceMonitor(
            iface = { prefs.interfaceName },
            onUpdate = { model ->
                currentModel = model
                updateNotification(model)
            },
            scope = lifecycleScope
        )
        monitor?.start()
        Log.d(TAG, "Monitoring started for interface: ${prefs.interfaceName}")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "网络监控"
            val descriptionText = "显示网络接口状态和速率"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VPN TAP Bridge 监控")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(model: UiModel) {
        val text = if (!model.exists) {
            "接口 ${prefs.interfaceName} 不存在"
        } else {
            val status = when {
                model.up && model.carrier -> "● UP"
                else -> "○ DOWN"
            }
            val rx = model.rxBps?.let { FormatUtils.formatBps(it) } ?: "--"
            val tx = model.txBps?.let { FormatUtils.formatBps(it) } ?: "--"
            "$status | ⬇ $rx | ⬆ $tx"
        }

        val notification = buildNotification(text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
