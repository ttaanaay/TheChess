package com.chessbubble.vision

import android.graphics.Bitmap
import com.chessbubble.chess.BoardState.Companion.squareOf
import com.chessbubble.model.BoardCalibration
import com.chessbubble.model.PointF2
import kotlin.math.pow

/**
 * Turns one captured screen frame into a 64-length FEN-style placement array
 * ('.' for empty), using perspective-free bilinear quad sampling (works for
 * on-screen rendered boards, which are flat UI -- not a photographed 3D
 * board, so no real perspective distortion, just possible slight skew from
 * imprecise calibration) + nearest-template matching per square.
 */
object BoardRecognizer {

    private const val TEMPLATE_SAMPLE_SIZE = 24 // downsample squares to 24x24 before comparing

    fun recognize(frame: Bitmap, calibration: BoardCalibration, templates: PieceTemplates): CharArray {
        val result = CharArray(64) { '.' }

        for (rank in 0 until 8) {
            for (file in 0 until 8) {
                // (u, v) center of this cell within the calibrated quad, in [0,1]x[0,1].
                // v=0 is the TOP edge of the quad (topLeft/topRight) as shown on screen.
                val displayRow = rank // 0 = top row of the quad as captured
                val u = (file + 0.5f) / 8f
                val v = (displayRow + 0.5f) / 8f

                val (px, py) = bilinearPoint(calibration, u, v, frame.width, frame.height)
                val cellBitmap = safeCrop(frame, px, py, cellPixelSize(calibration, frame))
                val label = classifySquare(cellBitmap, templates)

                // Map (displayRow, file) -> actual board square, honoring orientation.
                // If NOT flipped: displayRow 0 = rank 8 (top of screen), displayRow 7 = rank 1.
                // If flipped: displayRow 0 = rank 1, displayRow 7 = rank 8, and files mirror too.
                val (boardFile, boardRankIdx) = if (!calibration.boardFlipped) {
                    file to (7 - displayRow)
                } else {
                    (7 - file) to displayRow
                }

                val fenChar = PieceTemplates.LABEL_TO_FEN_CHAR[label] ?: '.'
                result[squareOf(boardFile, boardRankIdx)] = fenChar
            }
        }
        return result
    }

    private fun bilinearPoint(cal: BoardCalibration, u: Float, v: Float, frameW: Int, frameH: Int): Pair<Int, Int> {
        fun lerp(a: PointF2, b: PointF2, t: Float) = PointF2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        val top = lerp(cal.topLeft, cal.topRight, u)
        val bottom = lerp(cal.bottomLeft, cal.bottomRight, u)
        val p = lerp(top, bottom, v)
        return (p.x * frameW).toInt().coerceIn(0, frameW - 1) to (p.y * frameH).toInt().coerceIn(0, frameH - 1)
    }

    private fun cellPixelSize(cal: BoardCalibration, frame: Bitmap): Int {
        val widthPx = (cal.topRight.x - cal.topLeft.x) * frame.width
        return (kotlin.math.abs(widthPx) / 8f).toInt().coerceAtLeast(8)
    }

    private fun safeCrop(frame: Bitmap, cx: Int, cy: Int, size: Int): Bitmap {
        val half = size / 2
        val left = (cx - half).coerceIn(0, frame.width - 1)
        val top = (cy - half).coerceIn(0, frame.height - 1)
        val w = size.coerceAtMost(frame.width - left)
        val h = size.coerceAtMost(frame.height - top)
        return Bitmap.createBitmap(frame, left, top, w.coerceAtLeast(1), h.coerceAtLeast(1))
    }

    private fun classifySquare(cell: Bitmap, templates: PieceTemplates): String {
        val sample = Bitmap.createScaledBitmap(cell, TEMPLATE_SAMPLE_SIZE, TEMPLATE_SAMPLE_SIZE, true)
        var bestLabel = "empty_light"
        var bestScore = Double.MAX_VALUE
        for ((label, template) in templates.templates) {
            val scaledTemplate = Bitmap.createScaledBitmap(template, TEMPLATE_SAMPLE_SIZE, TEMPLATE_SAMPLE_SIZE, true)
            val score = sumSquaredDiff(sample, scaledTemplate)
            if (score < bestScore) {
                bestScore = score
                bestLabel = label
            }
        }
        return bestLabel
    }

    private fun sumSquaredDiff(a: Bitmap, b: Bitmap): Double {
        var total = 0.0
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                val pa = a.getPixel(x, y)
                val pb = b.getPixel(x, y)
                val dr = ((pa shr 16) and 0xFF) - ((pb shr 16) and 0xFF)
                val dg = ((pa shr 8) and 0xFF) - ((pb shr 8) and 0xFF)
                val db = (pa and 0xFF) - (pb and 0xFF)
                total += dr.toDouble().pow(2) + dg.toDouble().pow(2) + db.toDouble().pow(2)
            }
        }
        return total
    }

    /**
     * Draws the calibrated 8x8 grid + corner markers onto a COPY of the frame,
     * purely for debugging calibration accuracy (not used in the recognition
     * pipeline itself). Green lines = grid, red dots = the 4 calibrated corners.
     */
    fun drawDebugGrid(frame: Bitmap, calibration: BoardCalibration): Bitmap {
        val out = frame.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(out)
        val linePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.GREEN
            strokeWidth = 4f
            style = android.graphics.Paint.Style.STROKE
        }
        val dotPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.RED
            style = android.graphics.Paint.Style.FILL
        }

        for (i in 0..8) {
            val v = i / 8f
            val (x1, y1) = bilinearPoint(calibration, 0f, v, out.width, out.height)
            val (x2, y2) = bilinearPoint(calibration, 1f, v, out.width, out.height)
            canvas.drawLine(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), linePaint)
        }
        for (i in 0..8) {
            val u = i / 8f
            val (x1, y1) = bilinearPoint(calibration, u, 0f, out.width, out.height)
            val (x2, y2) = bilinearPoint(calibration, u, 1f, out.width, out.height)
            canvas.drawLine(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), linePaint)
        }
        for ((u, v) in listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f)) {
            val (x, y) = bilinearPoint(calibration, u, v, out.width, out.height)
            canvas.drawCircle(x.toFloat(), y.toFloat(), 14f, dotPaint)
        }
        return out
    }
}
