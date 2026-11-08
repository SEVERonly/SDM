package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.data.AppDatabase
import com.example.data.SettingsManager
import com.example.engine.DownloadEngine
import com.example.installer.PackageAndArchiveManager
import com.example.model.DownloadItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class SdmApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: AppDatabase
        private set

    lateinit var settingsManager: SettingsManager
        private set

    lateinit var packageManagerHelper: PackageAndArchiveManager
        private set

    lateinit var downloadEngine: DownloadEngine
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        settingsManager = SettingsManager.getInstance(this)
        packageManagerHelper = PackageAndArchiveManager(this)
        downloadEngine = DownloadEngine(this, database, settingsManager)

        // Setup notification channel for completion
        createCompletionNotificationChannel()

        // Auto-Installer and Completion Alert listener
        downloadEngine.addOnDownloadCompletedListener { item ->
            onDownloadFinished(item)
        }
    }

    private fun onDownloadFinished(item: DownloadItem) {
        val file = File(item.filePath)
        val settings = settingsManager.settings.value

        // Universal Package Auto-Installer
        if (settings.autoInstallApk && item.isApk && file.exists()) {
            applicationScope.launch(Dispatchers.Main) {
                if (item.fileName.endsWith(".xapk", ignoreCase = true)) {
                    packageManagerHelper.installXapk(file)
                } else {
                    packageManagerHelper.installApk(file)
                }
            }
        }

        // Show download complete notification
        if (settings.notifyOnComplete) {
            showCompletionNotification(item)
        }
    }

    private fun createCompletionNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                COMPLETE_CHANNEL_ID,
                "SDM Completed Downloads",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when a download is finished"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun showCompletionNotification(item: DownloadItem) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            item.id.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, COMPLETE_CHANNEL_ID)
            .setContentTitle("Download Complete: ${item.fileName}")
            .setContentText("File saved to ${item.filePath}")
            .setSmallIcon(R.drawable.sdm_app_icon_1794093491863)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(item.id.hashCode(), notification)
    }

    companion object {
        const val COMPLETE_CHANNEL_ID = "sdm_completed_channel"
    }
}
