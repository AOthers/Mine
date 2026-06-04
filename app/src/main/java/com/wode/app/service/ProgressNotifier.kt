package com.wode.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wode.app.R

class ProgressNotifier(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "备份与恢复进度",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "显示应用备份和恢复下载进度"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun showProgress(id: Int, title: String, text: String, progress: Int, indeterminate: Boolean = false) {
        if (!canPostNotifications()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_progress)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), indeterminate)
            .build()
        notificationManager.notify(id, notification)
    }

    fun showComplete(id: Int, title: String, text: String) {
        if (!canPostNotifications()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_progress)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, false)
            .build()
        notificationManager.notify(id, notification)
    }

    fun cancel(id: Int) {
        notificationManager.cancel(id)
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val CHANNEL_ID = "backup_restore_progress"
        const val BACKUP_ID = 1001
        const val RESTORE_ID = 1002
    }
}
