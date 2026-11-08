package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.SdmApplication
import com.example.model.DownloadItem
import com.example.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val app = application as? SdmApplication
        val engine = app?.downloadEngine

        // Monitor active downloads and update foreground notification
        scope.launch {
            engine?.liveDownloadsFlow?.collectLatest { liveMap ->
                val downloadingItems = liveMap.values.filter { it.status == DownloadStatus.DOWNLOADING }
                if (downloadingItems.isNotEmpty()) {
                    val primaryItem = downloadingItems.first()
                    val totalSpeed = engine.totalSpeedBytes.value
                    updateNotification(primaryItem, downloadingItems.size, totalSpeed)
                } else {
                    // No active downloads, gracefully detach from foreground
                    try {
                        stopForeground(STOP_FOREGROUND_DETACH)
                    } catch (e: Exception) {
                        Log.w("SDM", "Error stopping foreground: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // If intent is null or no active downloads, stop gracefully without throwing
        val app = application as? SdmApplication
        val hasActive = app?.downloadEngine?.liveDownloadsFlow?.value?.values?.any {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING
        } ?: false

        if (intent == null && !hasActive) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val initialNotification = buildInitialNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            // Catches ForegroundServiceStartNotAllowedException on Android 12+ (API 31+) & Android 14+ (API 34+)
            Log.w("SDM", "startForeground safely prevented crash: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SDM Active Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live download speed and progress"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildInitialNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SDM Engine Active")
            .setContentText("Monitoring downloads...")
            .setSmallIcon(R.drawable.sdm_app_icon_1794093491863)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun updateNotification(item: DownloadItem, totalActive: Int, speedBytes: Long) {
        val speedStr = formatSpeed(speedBytes)
        val progressInt = (item.progress * 100).toInt()

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (totalActive > 1) {
            "Downloading: ${item.fileName} (+${totalActive - 1} more)"
        } else {
            "Downloading: ${item.fileName}"
        }

        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText("$speedStr • $progressInt%")
                .setSmallIcon(R.drawable.sdm_app_icon_1794093491863)
                .setProgress(100, progressInt, item.fileSize <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentIntent)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w("SDM", "Error updating notification: ${e.message}")
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.2f MB/s", bytesPerSec / (1024.0 * 1024.0))
            bytesPerSec >= 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024.0)
            else -> "$bytesPerSec B/s"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "sdm_downloads_channel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            try {
                val intent = Intent(context, DownloadService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Prevents ForegroundServiceStartNotAllowedException when called from background states
                Log.w("SDM", "Could not start DownloadService: ${e.message}")
            }
        }
    }
}
