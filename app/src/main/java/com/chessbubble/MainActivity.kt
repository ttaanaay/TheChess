package com.chessbubble

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import com.chessbubble.capture.ScreenCaptureService
import com.chessbubble.model.BoardCalibration
import com.chessbubble.overlay.OverlayService
import com.chessbubble.ui.BoardCalibrationActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    private val calibrationLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updateStatus()
    }

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenCaptureService.start(this, result.resultCode, result.data!!)
            OverlayService.start(this)
            Toast.makeText(this, "เริ่มวิเคราะห์แล้ว", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.btnCalibrate).setOnClickListener {
            calibrationLauncher.launch(Intent(this, BoardCalibrationActivity::class.java))
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
            projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
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

    private fun updateStatus() {
        val calibrated = BoardCalibration.load(this) != null
        val overlayOk = hasOverlayPermission()
        statusText.text = buildString {
            append(if (calibrated) "✅ ตั้งค่ากระดานแล้ว\n" else "⛔ ยังไม่ได้ตั้งค่ากระดาน\n")
            append(if (overlayOk) "✅ อนุญาตแสดงทับแอปอื่นแล้ว" else "⛔ ยังไม่ได้อนุญาตแสดงทับแอปอื่น")
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
