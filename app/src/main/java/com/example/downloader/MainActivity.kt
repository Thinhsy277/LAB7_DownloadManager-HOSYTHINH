package com.example.downloader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var edtUrl: EditText
    private lateinit var tvStatus: TextView

    private val reqNoti =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* ignore */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            reqNoti.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        edtUrl = findViewById(R.id.edtUrl)
        tvStatus = findViewById(R.id.tvStatus)
        val btn: Button = findViewById(R.id.btnDownload)

        btn.setOnClickListener {
            val url = edtUrl.text.toString().trim()
            if (!url.startsWith("http")) {
                tvStatus.text = "URL không hợp lệ"
                return@setOnClickListener
            }
            tvStatus.text = "Bắt đầu tải…"
            DownloadService.start(this, url)
        }
    }
}
