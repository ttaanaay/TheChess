package com.chessbubble.capture

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
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
import com.chessbubble.chess.GameStateTracker
import com.chessbubble.chess.ResolveResult
import com.chessbubble.engine.ChessEngine
import com.chessbubble.engine.StubEngine
import com.chessbubble.model.BoardCalibration
import com.chessbubble.model.MoveQuality
import com.chessbubble.overlay.OverlayContract
import com.chessbubble.vision.BoardRecognizer
import com.chessbubble.vision.PieceTemplates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    // Dedicated background thread for the capture loop -- image acquisition +
    // bitmap conversion used to run on the main looper, which added avoidable
    // per-cycle latency. Everything capture-related now happens off the main thread.
    private val captureThread = android.os.HandlerThread("ChessBubbleCapture").apply { start() }
    private val captureHandler = Handler(captureThread.looper)
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private lateinit var tracker: GameStateTracker
    private var engine: ChessEngine = StubEngine() // swap for StockfishJniEngine once wired up
    private var templates: PieceTemplates? = null
    private var calibration: BoardCalibration? = null

    private var isCapturing = false
    private val captureIntervalMs = 200L
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        tracker = GameStateTracker()
        calibration = BoardCalibration.load(this)
        templates = PieceTemplates.loadGenerated(this)
            ?: runCatching { PieceTemplates.loadFromAssets(this, "default") }
                .onFailure { android.util.Log.e(TAG, "Failed to load piece templates from assets", it) }
                .getOrNull()
        android.util.Log.d(TAG, "onCreate: calibration=${calibration != null} templates=${templates != null}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)
        android.util.Log.d(TAG, "onStartCommand: resultCode=$resultCode hasData=${data != null} isCapturing=$isCapturing")

        startForegroundWithNotification()

        if (resultCode == Activity.RESULT_OK && data != null && !isCapturing) {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mgr.getMediaProjection(resultCode, data)
            // Required since Android 14 (API 34): createVirtualDisplay() throws if no
            // callback has been registered on the MediaProjection yet.
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    android.util.Log.d(TAG, "MediaProjection.Callback.onStop() -- projection was revoked/stopped")
                    isCapturing = false
                    releaseWakeLock()
                }
            }, Handler(Looper.getMainLooper()))
            setupVirtualDisplay()
            isCapturing = true
            acquireWakeLock()
            android.util.Log.d(TAG, "Capture loop starting, interval=${captureIntervalMs}ms")
            scheduleNextCapture()
        }
        return START_STICKY
    }

    private fun setupVirtualDisplay() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ChessBubbleCapture", width, height, density,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = pm.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK, "ChessBubble::CaptureWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(30 * 60 * 1000L /* 30 min safety timeout, renewed implicitly by re-acquire on restart */)
        }
        android.util.Log.d(TAG, "WakeLock acquired")
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    private fun scheduleNextCapture() {
        if (!isCapturing) return
        captureHandler.postDelayed({
            captureOnce()
            scheduleNextCapture()
        }, captureIntervalMs)
    }

    private fun captureOnce() {
        try {
            val reader = imageReader
            if (reader == null) { android.util.Log.d(TAG, "captureOnce: imageReader is null, skipping"); return }
            val cal = calibration
            if (cal == null) { android.util.Log.d(TAG, "captureOnce: calibration is null, skipping"); return }
            val tmpl = templates
            if (tmpl == null) { android.util.Log.d(TAG, "captureOnce: templates is null (assets not loaded?), skipping"); return }

            val image: Image? = reader.acquireLatestImage()
            if (image == null) { android.util.Log.d(TAG, "captureOnce: acquireLatestImage returned null"); return }
            val bitmap = imageToBitmap(image)
            image.close()

            scope.launch {
                try {
                    // Debug grid snapshot is purely diagnostic -- run it in the background
                    // so it never delays scheduling the next capture (this used to run
                    // synchronously on the main thread and was adding real per-cycle lag).
                    runCatching {
                        val debugBmp = BoardRecognizer.drawDebugGrid(bitmap, cal)
                        java.io.FileOutputStream(java.io.File(filesDir, "debug_frame.png")).use {
                            debugBmp.compress(Bitmap.CompressFormat.PNG, 90, it)
                        }
                    }.onFailure { android.util.Log.e(TAG, "Failed to save debug frame", it) }

                    val placement = BoardRecognizer.recognize(bitmap, cal, tmpl)
                    val placementFen = com.chessbubble.chess.BoardState(placement).toPlacementFen()
                    val before = tracker.current
                    val result = tracker.submitRecognizedPlacement(placement)
                    android.util.Log.d(TAG, "recognized=$placementFen previous=${before.toPlacementFen()} result=${result::class.simpleName}")

                    if (result is ResolveResult.Resolved) {
                        val moverWasWhite = before.whiteToMove
                        val bestEvalWhitePerspective = engine.evaluateCp(before.toFen())
                        val afterEvalWhitePerspective = engine.evaluateCp(result.newState.toFen())

                        val moverBest = if (moverWasWhite) bestEvalWhitePerspective else -bestEvalWhitePerspective
                        val moverActual = if (moverWasWhite) afterEvalWhitePerspective else -afterEvalWhitePerspective
                        val cpLoss = (moverBest - moverActual).coerceAtLeast(0)
                        val quality = MoveQuality.fromCentipawnLoss(cpLoss)

                        android.util.Log.d(TAG, "MOVE DETECTED: san=${result.san} quality=${quality.label} visionErrorSquares=${result.visionErrorSquares}")
                        broadcastMove(moverWasWhite, result.san, quality)
                    }
                    // ResolveResult.NoMatch -> vision misread a square or missed a move; simply wait
                    // for the next capture frame rather than guessing.
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error processing captured frame", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error capturing frame", e)
        }
    }

    private fun broadcastMove(white: Boolean, san: String, quality: MoveQuality) {
        val color = when (quality) {
            MoveQuality.BEST, MoveQuality.EXCELLENT -> 0xFF2ECC71.toInt()
            MoveQuality.GREAT -> 0xFF27AE60.toInt()
            MoveQuality.GOOD -> 0xFF3498DB.toInt()
            MoveQuality.INACCURACY -> 0xFFF1C40F.toInt()
            MoveQuality.MISTAKE -> 0xFFE67E22.toInt()
            MoveQuality.MISS, MoveQuality.BLUNDER -> 0xFFE74C3C.toInt()
        }
        val intent = Intent(OverlayContract.ACTION_SHOW_MOVE).apply {
            setPackage(packageName)
            putExtra(OverlayContract.EXTRA_SIDE_WHITE, white)
            putExtra(OverlayContract.EXTRA_SAN, san)
            putExtra(OverlayContract.EXTRA_QUALITY_LABEL, quality.label)
            putExtra(OverlayContract.EXTRA_QUALITY_COLOR, color)
        }
        sendBroadcast(intent)
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

    private fun startForegroundWithNotification() {
        val channelId = "capture_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, getString(R.string.notif_capture_channel), NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }

        // Tapping the notification jumps straight into DebugFrameActivity, bypassing
        // MainActivity entirely -- if the user opened MainActivity first, the very
        // act of switching to it would trigger a new capture of OUR OWN black
        // screen, overwriting the debug frame right before they get to view it.
        val debugIntent = Intent(this, com.chessbubble.ui.DebugFrameActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val debugPendingIntent = android.app.PendingIntent.getActivity(
            this, 0, debugIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notif_capture_title))
            .setContentText("แตะเพื่อดูภาพ debug ล่าสุด")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(debugPendingIntent)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        isCapturing = false
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        engine.close()
        captureThread.quitSafely()
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val NOTIF_ID = 43
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenCaptureService::class.java))
        }
    }
}
