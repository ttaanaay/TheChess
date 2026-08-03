package com.chessbubble

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.chessbubble.capture.CalibrationCaptureService
import com.chessbubble.capture.ScreenCaptureService
import com.chessbubble.model.BoardCalibration
import com.chessbubble.overlay.OverlayService
import com.chessbubble.ui.BoardCalibrationActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    // Fires once CalibrationCaptureService finishes grabbing the one frame we need.
    private val calibrationFrameReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                CalibrationCaptureService.ACTION_FRAME_READY -> {
                    val path = intent.getStringExtra(CalibrationCaptureService.EXTRA_FRAME_PATH)
                    if (path != null) {
                        calibrationActivityLauncher.launch(
                            Intent(this@MainActivity, BoardCalibrationActivity::class.java)
                                .putExtra(BoardCalibrationActivity.EXTRA_FRAME_PATH, path)
                        )
                    }
                }
                CalibrationCaptureService.ACTION_FRAME_FAILED -> {
                    Toast.makeText(this@MainActivity, "จับภาพหน้าจอไม่สำเร็จ ลองกด \"ตั้งค่าตำแหน่งกระดาน\" อีกครั้ง", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val calibrationActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updateStatus()
    }

    // Requests the MediaProjection permission used ONLY to grab the one-time
    // calibration preview frame.
    private val calibrationProjectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            CalibrationCaptureService.start(this, result.resultCode, result.data!!)
            Toast.makeText(
                this,
                "สลับไปเปิดแอปหมากรุกที่ต้องการตอนนี้เลย! จะแคปหน้าจอใน 4 วินาที แล้วเลื่อนแถบแจ้งเตือนลงมาแตะเพื่อกลับมาตั้งค่ากระดาน",
                Toast.LENGTH_LONG
            ).show()
            // Get our own UI out of the way so the chess app becomes visible
            // again before the delayed capture fires (see CalibrationCaptureService).
            moveTaskToBack(true)
        } else {
            Toast.makeText(this, "ต้องอนุญาตแคปหน้าจอก่อนถึงจะตั้งค่าได้", Toast.LENGTH_SHORT).show()
        }
    }

    // Requests the MediaProjection permission used for the actual live analysis session.
    private val analysisProjectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenCaptureService.start(this, result.resultCode, result.data!!)
            OverlayService.start(this)
            Toast.makeText(this, "เริ่มวิเคราะห์แล้ว", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        requestIgnoreBatteryOptimizations()

        val filter = IntentFilter().apply {
            addAction(CalibrationCaptureService.ACTION_FRAME_READY)
            addAction(CalibrationCaptureService.ACTION_FRAME_FAILED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(calibrationFrameReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(calibrationFrameReceiver, filter)
        }

        findViewById<Button>(R.id.btnCalibrate).setOnClickListener {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            calibrationProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (!hasOverlayPermission()) {
                requestOverlayPermission()
                return@setOnClickListener
            }
            if (BoardCalibration.load(this) == null) {
                Toast.makeText(this, "กรุณาตั้งค่าตำแหน่งกระดานก่อน", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            analysisProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            ScreenCaptureService.stop(this)
            OverlayService.stop(this)
            Toast.makeText(this, "หยุดแล้ว", Toast.LENGTH_SHORT).show()
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(calibrationFrameReceiver) }
    }

    private fun updateStatus() {
        val calibrated = BoardCalibration.load(this) != null
        val overlayOk = hasOverlayPermission()
        statusText.text = buildString {
            append(if (calibrated) "✅ ตั้งค่ากระดานแล้ว\n" else "⛔ ยังไม่ได้ตั้งค่ากระดาน\n")
            append(if (overlayOk) "✅ อนุญาตแสดงทับแอปอื่นแล้ว" else "⛔ ยังไม่ได้อนุญาตแสดงทับแอปอื่น")
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            runCatching {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }
    }

    private fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivity(intent)
        Toast.makeText(this, getString(R.string.permission_overlay_needed), Toast.LENGTH_LONG).show()
    }
}
