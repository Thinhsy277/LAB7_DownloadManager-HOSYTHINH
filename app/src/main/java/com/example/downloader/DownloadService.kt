package com.example.downloader

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class DownloadService : Service() {

    companion object {
        const val ACT_START = "start"
        const val ACT_PAUSE = "pause"
        const val ACT_RESUME = "resume"
        const val ACT_CANCEL = "cancel"
        const val EXTRA_URL = "extra_url"

        fun start(context: Context, url: String) {
            val i = Intent(context, DownloadService::class.java).apply {
                action = ACT_START
                putExtra(EXTRA_URL, url)
            }
            // Quan trọng cho Android 8+: dùng startForegroundService
            ContextCompat.startForegroundService(context, i)
        }

        fun sendAction(context: Context, action: String) {
            val i = Intent(context, DownloadService::class.java).apply { this.action = action }
            ContextCompat.startForegroundService(context, i)
        }
    }

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() { fun getService() = this@DownloadService }
    override fun onBind(intent: Intent?): IBinder = binder

    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    @Volatile private var paused = false
    @Volatile private var canceled = false

    private var downloadUrl: String = ""
    private lateinit var targetFile: File

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Bảo đảm có kênh trước khi dựng noti
        NotificationHelper.createChannel(this)

        when (intent?.action) {
            ACT_START -> {
                downloadUrl = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                canceled = false; paused = false
                targetFile = File(filesDir, inferFileName(downloadUrl))

                // BẮT BUỘC: gọi startForeground NGAY khi service start
                val noti = NotificationHelper.buildForegroundNotification(
                    this, "Chuẩn bị tải…", 0, true, downloadUrl
                )
                startForeground(NotificationHelper.NOTI_ID, noti)

                startDownload()
            }

            ACT_PAUSE -> paused = true

            ACT_RESUME -> if (paused) {
                paused = false
                startDownload(resume = true)
            }

            ACT_CANCEL -> {
                canceled = true
                scope.launch {
                    job?.cancelAndJoin()
                    if (::targetFile.isInitialized) targetFile.delete()
                    stopSelfSafely()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(resume: Boolean = false) {
        job?.cancel()
        job = scope.launch {
            var downloadedBytes = if (resume && ::targetFile.isInitialized && targetFile.exists())
                targetFile.length() else 0L
            var totalBytes = -1L

            val url = URL(downloadUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                requestMethod = "GET"
                if (downloadedBytes > 0) setRequestProperty("Range", "bytes=$downloadedBytes-")
            }

            try {
                conn.connect()
                val responseCode = conn.responseCode
                if (responseCode !in 200..206) throw Exception("HTTP $responseCode")

                totalBytes = conn.getHeaderField("Content-Length")?.toLong() ?: -1
                if (downloadedBytes > 0 && totalBytes > 0) totalBytes += downloadedBytes

                val input: InputStream = conn.inputStream
                val fos = FileOutputStream(targetFile, downloadedBytes > 0)

                val buffer = ByteArray(8 * 1024)
                var read: Int
                var bytesSinceLastUpdate = 0L

                updateNotification(progressOf(downloadedBytes, totalBytes), totalBytes <= 0, "Đang tải…")

                while (isActive && !canceled) {
                    if (paused) {
                        updateNotification(progressOf(downloadedBytes, totalBytes), false, "Đã tạm dừng")
                        delay(300)
                        continue
                    }

                    read = input.read(buffer)
                    if (read == -1) break
                    fos.write(buffer, 0, read)
                    downloadedBytes += read
                    bytesSinceLastUpdate += read

                    if (bytesSinceLastUpdate >= 64 * 1024) {
                        updateNotification(progressOf(downloadedBytes, totalBytes), false, "Đang tải…")
                        bytesSinceLastUpdate = 0
                    }
                }

                fos.flush(); fos.close(); input.close(); conn.disconnect()

                if (canceled) return@launch

                if (!paused) {
                    updateNotification(100, false, "Hoàn tất: ${targetFile.name}")
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(
                        NotificationHelper.NOTI_ID,
                        NotificationHelper.buildForegroundNotification(
                            this@DownloadService,
                            "Hoàn tất: ${targetFile.name}",
                            100,
                            false,
                            downloadUrl
                        )
                    )
                    stopSelfSafely()
                }
            } catch (e: Exception) {
                updateNotification(0, true, "Lỗi: ${e.message}")
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun progressOf(done: Long, total: Long): Int =
        if (total <= 0) 0 else ((done * 100) / total).toInt().coerceIn(0, 100)

    private fun updateNotification(progress: Int, indeterminate: Boolean, title: String) {
        val noti = NotificationHelper.buildForegroundNotification(
            this, title, progress, indeterminate, downloadUrl
        )
        NotificationManagerCompat.from(this).notify(NotificationHelper.NOTI_ID, noti)
    }

    private fun inferFileName(url: String): String {
        val q = url.substringAfterLast('/')
        return if (q.isBlank()) "download_${System.currentTimeMillis()}" else q
    }

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }
}
