package com.hackqing.scale.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hackqing.scale.MainActivity
import com.hackqing.scale.R
import com.hackqing.scale.data.ScaleRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 后台常驻服务：持续扫描并在发现云麦体脂秤时自动连接，
 * 使每天上秤测量后无需打开 App 即可自动接收并保存数据。
 */
@AndroidEntryPoint
class ScaleService : Service() {

    @Inject
    lateinit var repository: ScaleRepository

    companion object {
        const val ACTION_START = "com.hackqing.scale.START"
        const val ACTION_STOP = "com.hackqing.scale.STOP"
        private const val CHANNEL_ID = "hackqing_scale"
        private const val NOTIF_ID = 1001
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastSummary = ""
    private var observing = false

    override fun onCreate() {
        super.onCreate()
        repository.init()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                repository.setAutoReconnect(false)
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundCompat(buildNotification("正在启动…"))
                repository.setAutoReconnect(true, lowPower = true)
                observe()
            }
        }
        return START_STICKY
    }

    private fun observe() {
        if (observing) return
        observing = true
        scope.launch {
            repository.state.collectLatest { s ->
                val summary = when {
                    s.connectedAddress != null && s.liveWeightKg != null ->
                        String.format(Locale.US, "已连接，当前 %.2f kg", s.liveWeightKg)
                    s.connectedAddress != null -> "已连接 ${s.connectedAddress}，等待上秤"
                    s.state == com.hackqing.scale.ble.ScaleGattClient.State.SCANNING -> "正在扫描体脂秤…"
                    else -> "待机中，等待上秤"
                }
                if (summary != lastSummary) {
                    lastSummary = summary
                    notify(buildNotification(summary))
                }
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ScaleService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("hackqing 后台接收中")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_scale)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "停止", stop)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID, "体脂秤后台接收",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "保持连接，自动接收体脂秤测量数据" }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun notify(n: Notification) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, n)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
