package com.chessbubble.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Shows the most recent captured frame with the calibration grid drawn on top
 * (green lines = the 8x8 grid as currently calibrated, red dots = the 4
 * corners). Purely a troubleshooting tool: if the green grid doesn't line up
 * with the real chess board, recalibrate.
 */
class DebugFrameActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        setContentView(imageView)

        val file = File(filesDir, "debug_frame.png")
        if (!file.exists()) {
            Toast.makeText(this, "ยังไม่มีภาพ debug — เริ่มวิเคราะห์ก่อนอย่างน้อย 1 เฟรม", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        imageView.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
    }
}
