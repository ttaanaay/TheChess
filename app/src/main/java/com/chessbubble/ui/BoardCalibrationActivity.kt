package com.chessbubble.ui

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chessbubble.R
import com.chessbubble.model.BoardCalibration

/**
 * Shows the already-captured frame (path passed in via EXTRA_FRAME_PATH --
 * capturing happens beforehand in MainActivity + CalibrationCaptureService,
 * NOT here) and lets the user drag the 4 corner handles onto the board.
 */
class BoardCalibrationActivity : AppCompatActivity() {

    private lateinit var cornerOverlay: CornerOverlayView
    private lateinit var framePreview: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_board_calibration)

        framePreview = findViewById(R.id.framePreview)
        cornerOverlay = findViewById(R.id.cornerOverlay)

        val framePath = intent.getStringExtra(EXTRA_FRAME_PATH)
        val bitmap = framePath?.let { BitmapFactory.decodeFile(it) }
        if (bitmap == null) {
            Toast.makeText(this, "ไม่พบภาพหน้าจอที่แคปไว้ กรุณาลองใหม่", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        framePreview.setImageBitmap(bitmap)

        findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            val pts = cornerOverlay.getNormalizedCorners()
            val calibration = BoardCalibration(
                topLeft = pts[0], topRight = pts[1], bottomRight = pts[2], bottomLeft = pts[3]
            )
            val saveOk = BoardCalibration.save(this, calibration)
            val verify = BoardCalibration.load(this)
            Toast.makeText(
                this,
                "DEBUG: save=$saveOk verifyLoaded=${verify != null}",
                Toast.LENGTH_LONG
            ).show()

            // Delay so the debug toast above is actually visible before we navigate away.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                startActivity(
                    Intent(this, com.chessbubble.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                )
                setResult(Activity.RESULT_OK)
                finish()
            }, 2000)
        }
    }

    companion object {
        const val EXTRA_FRAME_PATH = "frame_path"
    }
}
