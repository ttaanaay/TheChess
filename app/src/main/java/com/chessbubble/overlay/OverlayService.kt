package com.chessbubble.overlay

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.chessbubble.R

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: android.view.View? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != OverlayContract.ACTION_SHOW_MOVE) return
            val white = intent.getBooleanExtra(OverlayContract.EXTRA_SIDE_WHITE, true)
            val san = intent.getStringExtra(OverlayContract.EXTRA_SAN) ?: return
            val quality = intent.getStringExtra(OverlayContract.EXTRA_QUALITY_LABEL) ?: ""
            val color = intent.getIntExtra(OverlayContract.EXTRA_QUALITY_COLOR, 0xFF3498DB.toInt())
            android.util.Log.d("OverlayService", "BROADCAST RECEIVED: san=$san")
            showMove(white, san, quality, color)
            android.util.Log.d("OverlayService", "BUBBLE UPDATED: san=$san")
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        registerReceiver(receiver, IntentFilter(OverlayContract.ACTION_SHOW_MOVE), RECEIVER_NOT_EXPORTED_COMPAT)
        startForegroundWithNotification()
        addBubbleView()
    }

    private val RECEIVER_NOT_EXPORTED_COMPAT: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0

    private fun startForegroundWithNotification() {
        val channelId = "overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, getString(R.string.notif_overlay_channel), NotificationManager.IMPORTANCE_MIN
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notif_overlay_title))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification)
    }

    private fun addBubbleView() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.view_bubble, null)
        bubbleView = view

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.END
        params.x = 24
        params.y = 200

        makeDraggable(view, params)
        view.alpha = 0f
        windowManager.addView(view, params)
    }

    private fun makeDraggable(view: android.view.View, params: WindowManager.LayoutParams) {
        var initialX = 0; var initialY = 0
        var touchX = 0f; var touchY = 0f
        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    touchX = event.rawX; touchY = event.rawY
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    // gravity is TOP|END, so dragging left increases x-from-right; keep it simple with END gravity math
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    params.x = (initialX - dx).coerceAtLeast(0)
                    params.y = (initialY + dy).coerceAtLeast(0)
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun showMove(white: Boolean, san: String, quality: String, color: Int) {
        val view = bubbleView ?: return
        view.findViewById<TextView>(R.id.txtSide).text = if (white) "White" else "Black"
        view.findViewById<TextView>(R.id.txtMove).text = san
        view.findViewById<TextView>(R.id.txtQuality).apply {
            text = quality
            setTextColor(color)
        }
        // Stays visible permanently (shows the latest move) until the next
        // update replaces it -- no more auto-hide after a few seconds.
        view.animate().alpha(1f).setDuration(150).start()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(receiver) }
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 42
        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
