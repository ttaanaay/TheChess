package com.chessbubble.capture

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat
import com.chessbubble.R
import java.io.File
import java.io.FileOutputStream

/**
 * Captures exactly ONE screen frame for the board-calibration preview, then
 * stops itself.
 *
 * IMPORTANT: this is started right after the user grants the MediaProjection
 * permission from MainActivity. At that exact moment OUR OWN app is still in
 * the foreground, so we deliberately WAIT (CAPTURE_DELAY_MS) before grabbing
 * the frame -- MainActivity calls moveTaskToBack() and shows a countdown
 * toast so the user has time to switch back to their chess app. If we
 * captured immediately we'd just get a screenshot of our own (blank) UI.
 *
 * Must be a foreground service of type "mediaProjection" -- as of Android 14,
 * calling MediaProjection.createVirtualDisplay() without an active foreground
 * service of this type throws a SecurityException and crashes the app.
 */
class CalibrationCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithNotification()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)

        if (resultCode == Activity.RESULT_OK && data != null) {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mgr.getMediaProjection(resultCode, data)
            // Required since Android 14 (API 34): createVirtualDisplay() throws if no
            // callback has been registered on the MediaProjection yet.
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { /* no-op: this is a one-shot capture */ }
            }, Handler(Looper.getMainLooper()))
            try {
                captureOneFrame()
            } catch (e: Exception) {
                broadcastFailure()
                stopSelf()
            }
        } else {
            broadcastFailure()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun captureOneFrame() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ChessBubbleCalibration", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, null
        )

        // Give the user time to switch back to their chess app first -- see
        // the class KDoc above for why this delay is essential.
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val image: Image? = reader.acquireLatestImage()
                var savedPath: String? = null
                if (image != null) {
                    val bmp = imageToBitmap(image)
                    savedPath = saveToCache(bmp)
                    image.close()
                }
                virtualDisplay?.release()
                reader.close()
                mediaProjection?.stop()

                if (savedPath != null) broadcastSuccess(savedPath) else broadcastFailure()
            } catch (e: Exception) {
                runCatching { virtualDisplay?.release() }
                runCatching { reader.close() }
                runCatching { mediaProjection?.stop() }
                broadcastFailure()
            } finally {
                stopSelf()
            }
        }, CAPTURE_DELAY_MS)
    }

    private fun saveToCache(bmp: Bitmap): String {
        val file = File(cacheDir, "calibration_frame.png")
        FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return file.absolutePath
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

    private fun broadcastSuccess(path: String) {
        sendBroadcast(Intent(ACTION_FRAME_READY).apply {
            setPackage(packageName)
            putExtra(EXTRA_FRAME_PATH, path)
        })
    }

    private fun broadcastFailure() {
        sendBroadcast(Intent(ACTION_FRAME_FAILED).apply { setPackage(packageName) })
    }

    private fun startForegroundWithNotification() {
        val channelId = "calibration_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, getString(R.string.notif_capture_channel), NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("กำลังจะแคปหน้าจอใน ${CAPTURE_DELAY_MS / 1000} วิ...")
            .setContentText("สลับไปเปิดแอปหมากรุกที่ต้องการตอนนี้")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 44
        private const val CAPTURE_DELAY_MS = 4000L
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val ACTION_FRAME_READY = "com.chessbubble.action.CALIBRATION_FRAME_READY"
        const val ACTION_FRAME_FAILED = "com.chessbubble.action.CALIBRATION_FRAME_FAILED"
        const val EXTRA_FRAME_PATH = "frame_path"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, CalibrationCaptureService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}
