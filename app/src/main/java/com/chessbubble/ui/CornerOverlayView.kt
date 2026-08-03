package com.chessbubble.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.chessbubble.model.PointF2

/**
 * Assumes the underlying preview ImageView fills the same bounds as this view
 * and shows the captured frame at the same aspect ratio (true for a full
 * screen capture shown full screen), so normalized [0,1] coordinates here map
 * 1:1 to normalized coordinates in the captured frame.
 */
class CornerOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // Normalized (0..1) positions, default to an inset square in the middle of the screen.
    private var corners = arrayOf(
        PointF2(0.15f, 0.30f), // top-left
        PointF2(0.85f, 0.30f), // top-right
        PointF2(0.85f, 0.85f), // bottom-right
        PointF2(0.15f, 0.85f)  // bottom-left
    )

    private var draggingIndex = -1
    private val handleRadiusPx = 36f

    private val handlePaint = Paint().apply { color = Color.parseColor("#2ECC71"); style = Paint.Style.FILL }
    private val linePaint = Paint().apply { color = Color.parseColor("#2ECC71"); style = Paint.Style.STROKE; strokeWidth = 4f }

    fun getNormalizedCorners(): Array<PointF2> = corners.copyOf()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pts = corners.map { it.x * width to it.y * height }
        for (i in pts.indices) {
            val (x1, y1) = pts[i]
            val (x2, y2) = pts[(i + 1) % pts.size]
            canvas.drawLine(x1, y1, x2, y2, linePaint)
        }
        for ((x, y) in pts) {
            canvas.drawCircle(x, y, handleRadiusPx, handlePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                draggingIndex = corners.indices.minByOrNull { i ->
                    val px = corners[i].x * width; val py = corners[i].y * height
                    val dx = px - event.x; val dy = py - event.y
                    dx * dx + dy * dy
                }?.takeIf { i ->
                    val px = corners[i].x * width; val py = corners[i].y * height
                    val dx = px - event.x; val dy = py - event.y
                    dx * dx + dy * dy < (handleRadiusPx * 3).let { it * it }
                } ?: -1
                return draggingIndex != -1
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingIndex != -1) {
                    val nx = (event.x / width).coerceIn(0f, 1f)
                    val ny = (event.y / height).coerceIn(0f, 1f)
                    corners[draggingIndex] = PointF2(nx, ny)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingIndex = -1
            }
        }
        return super.onTouchEvent(event)
    }
}
