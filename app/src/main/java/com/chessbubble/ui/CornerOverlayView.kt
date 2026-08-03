package com.chessbubble.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.chessbubble.model.PointF2

/**
 * Corner handles are positioned/read relative to `imageRect` -- the actual
 * on-screen rectangle where the captured frame bitmap is drawn inside the
 * sibling ImageView (which may be smaller than this view's own full bounds,
 * since ImageView's fitCenter scaleType letterboxes/pillarboxes whenever the
 * bitmap's aspect ratio doesn't exactly match the screen's, e.g. because of
 * status/gesture bar insets). Using imageRect instead of the raw view width/
 * height keeps calibration accurate regardless of any such letterboxing.
 *
 * Caller MUST call setImageDisplayRect() once the ImageView has laid out and
 * the bitmap's drawn bounds are known (see BoardCalibrationActivity).
 */
class CornerOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // Normalized (0..1) positions WITHIN imageRect, default to an inset square.
    private var corners = arrayOf(
        PointF2(0.15f, 0.30f), // top-left
        PointF2(0.85f, 0.30f), // top-right
        PointF2(0.85f, 0.85f), // bottom-right
        PointF2(0.15f, 0.85f)  // bottom-left
    )

    private var imageRect: RectF? = null

    private var draggingIndex = -1
    private val handleRadiusPx = 36f

    private val handlePaint = Paint().apply { color = Color.parseColor("#2ECC71"); style = Paint.Style.FILL }
    private val linePaint = Paint().apply { color = Color.parseColor("#2ECC71"); style = Paint.Style.STROKE; strokeWidth = 4f }

    /** Must be called once the hosting ImageView has measured/laid out the bitmap. */
    fun setImageDisplayRect(rect: RectF) {
        imageRect = rect
        invalidate()
    }

    /** Fractions are relative to the captured bitmap itself (via imageRect), not the raw view size. */
    fun getNormalizedCorners(): Array<PointF2> = corners.copyOf()

    private fun toScreen(p: PointF2): Pair<Float, Float> {
        val r = imageRect ?: RectF(0f, 0f, width.toFloat(), height.toFloat())
        return (r.left + p.x * r.width()) to (r.top + p.y * r.height())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pts = corners.map { toScreen(it) }
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
        val r = imageRect ?: RectF(0f, 0f, width.toFloat(), height.toFloat())
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                draggingIndex = corners.indices.minByOrNull { i ->
                    val (px, py) = toScreen(corners[i])
                    val dx = px - event.x; val dy = py - event.y
                    dx * dx + dy * dy
                }?.takeIf { i ->
                    val (px, py) = toScreen(corners[i])
                    val dx = px - event.x; val dy = py - event.y
                    dx * dx + dy * dy < (handleRadiusPx * 3).let { it * it }
                } ?: -1
                return draggingIndex != -1
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingIndex != -1) {
                    val nx = ((event.x - r.left) / r.width()).coerceIn(0f, 1f)
                    val ny = ((event.y - r.top) / r.height()).coerceIn(0f, 1f)
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
