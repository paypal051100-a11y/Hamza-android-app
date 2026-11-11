package com.example.sender

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val REQ = 1001
    private lateinit var statusText: TextView
    private lateinit var startBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        startBtn   = findViewById(R.id.startBtn)

        startBtn.setOnClickListener {
            if (hasPermissions()) start() else askPermissions()
        }

        if (hasPermissions()) start()
    }

    private fun start() {
        val oneTime = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(netConstraint())
            .build()
        WorkManager.getInstance(this).enqueue(oneTime)

        val periodic = PeriodicWorkRequestBuilder<UploadWorker>(15, TimeUnit.MINUTES)
            .setConstraints(netConstraint())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("auto_upload", ExistingPeriodicWorkPolicy.KEEP, periodic)

        startBtn.isEnabled = false
        statusText.text = "✅ يعمل في الخلفية\nسيرفع الملفات الجديدة تلقائياً"
    }

    private fun netConstraint() =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private fun hasPermissions() =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun askPermissions() =
        ActivityCompat.requestPermissions(this, requiredPermissions().toTypedArray(), REQ)

    private fun requiredPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ && grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            start()
        } else {
            statusText.text = "⚠️ لازم تمنح الصلاحيات حتى يشتغل التطبيق"
            startBtn.isEnabled = true
        }
    }
}
