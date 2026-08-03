package com.chessbubble.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.widget.Button
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.chessbubble.R
import com.chessbubble.model.BoardCalibration

class BoardCalibrationActivity : AppCompatActivity() {

    private lateinit var cornerOverlay: CornerOverlayView
    private lateinit var framePreview: ImageView
    private var mediaProjection: MediaProjection? = null

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            captureSingleFrame(res.resultCode, res.data!!)
        } else {
            finish() // user declined screen capture permission; nothing to calibrate against
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_board_calibration)

        framePreview = findViewById(R.id.framePreview)
        cornerOverlay = findViewById(R.id.cornerOverlay)

        findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            val pts = cornerOverlay.getNormalizedCorners()
            val calibration = BoardCalibration(
                topLeft = pts[0], topRight = pts[1], bottomRight = pts[2], bottomLeft = pts[3]
            )
            BoardCalibration.save(this, calibration)
            setResult(Activity.RESULT_OK)
            finish()
        }

        // Existing calibration, if any, pre-fills the default square in CornerOverlayView's
        // constructor; a future improvement is to load and pass those points in explicitly.

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun captureSingleFrame(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ChessBubbleCalibration", width, height, metrics.densityDpi,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, null
        )

        // Grab the first available frame, then immediately tear the projection down --
        // we only need one still image for the user to drag corners over.
        Handler(Looper.getMainLooper()).postDelayed({
            val image: Image? = reader.acquireLatestImage()
            if (image != null) {
                framePreview.setImageBitmap(imageToBitmap(image))
                image.close()
            }
            virtualDisplay?.release()
            reader.close()
            mediaProjection?.stop()
        }, 300)
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return if (bitmap.width != image.width) Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height) else bitmap
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaProjection?.stop()
    }
}
