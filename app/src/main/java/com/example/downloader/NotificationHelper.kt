package com.example.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

object NotificationHelper {
    const val CHANNEL_ID = "download_channel"
    const val NOTI_ID = 1001

    const val ACTION_PAUSE = "com.example.downloader.ACTION_PAUSE"
    const val ACTION_RESUME = "com.example.downloader.ACTION_RESUME"
    const val ACTION_CANCEL = "com.example.downloader.ACTION_CANCEL"

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Download",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun buildForegroundNotification(
        ctx: Context,
        title: String,
        progress: Int,
        indeterminate: Boolean,
        link: String = ""
    ): Notification {
        val remote = RemoteViews(ctx.packageName, R.layout.notification_download).apply {
            setTextViewText(R.id.txtTitle, title)
            setTextViewText(R.id.txtLink, "Link: $link")
            setTextViewText(R.id.txtPercent, "Complete: $progress%")
            setProgressBar(R.id.progressBar, 100, progress, indeterminate)

            setOnClickPendingIntent(R.id.btnPause, broadcast(ctx, ACTION_PAUSE))
            setOnClickPendingIntent(R.id.btnResume, broadcast(ctx, ACTION_RESUME))
            setOnClickPendingIntent(R.id.btnCancel, broadcast(ctx, ACTION_CANCEL))
        }

        val openApp = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Fallback nếu custom RemoteViews có vấn đề
        return try {
            NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Download Manager")
                .setContentIntent(openApp)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCustomContentView(remote)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .build()
        } catch (e: Exception) {
            NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText("Complete: $progress%")
                .setProgress(100, progress, indeterminate)
                .setOngoing(true)
                .build()
        }
    }

    private fun broadcast(ctx: Context, action: String): PendingIntent {
        val intent = Intent(ctx, DownloadActionReceiver::class.java).apply { this.action = action }
        return PendingIntent.getBroadcast(
            ctx,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
