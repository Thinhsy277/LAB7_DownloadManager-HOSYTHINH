package com.example.downloader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DownloadActionReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when(intent.action){
            NotificationHelper.ACTION_PAUSE -> DownloadService.sendAction(context, DownloadService.ACT_PAUSE)
            NotificationHelper.ACTION_RESUME -> DownloadService.sendAction(context, DownloadService.ACT_RESUME)
            NotificationHelper.ACTION_CANCEL -> DownloadService.sendAction(context, DownloadService.ACT_CANCEL)
        }
    }
}