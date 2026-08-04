package com.chessbubble.vision

import android.graphics.Bitmap
import com.chessbubble.chess.BoardState
import com.chessbubble.chess.BoardState.Companion.squareOf
import com.chessbubble.model.BoardCalibration

/**
 * Generates piece templates directly from a live captured frame at the
 * standard starting position -- the SAME pipeline BoardRecognizer.recognize()
 * uses at runtime, so scale/compression always matches exactly.
 *
 * IMPORTANT: each template crop includes its square's background color along
 * with the piece, and a chessboard alternates light/dark squares. A single
 * template per piece type (e.g. one black-pawn crop) only really matches
 * pieces standing on the SAME background color; pieces of the same type on
 * the opposite color get misclassified because the background mismatch adds
 * enough pixel error to tip the match toward some other template. So we
 * capture a template PER (piece type, square color) combination whenever the
 * starting position offers one (it offers both colors for every piece type
 * except queen and king, which only ever start on one color each).
 */
object TemplateGenerator {

    private val startingBoard = BoardState.fromFen(BoardState.START_FEN)

    /** Key format: "wP_light", "bK_dark", "empty_light", etc. */
    fun generateFromStartingPosition(frame: Bitmap, calibration: BoardCalibration): Map<String, Bitmap> {
        val result = mutableMapOf<String, Bitmap>()

        for (displayRow in 0 until 8) {
            for (file in 0 until 8) {
                val (boardFile, boardRank) = BoardRecognizer.displayPositionToBoardSquare(calibration, displayRow, file)
                val fenChar = startingBoard.board[squareOf(boardFile, boardRank)]
                val isLight = (boardFile + boardRank) % 2 == 1
                val key = keyFor(fenChar, isLight)

                if (key !in result) {
                    val crop = BoardRecognizer.cropCellAtDisplayPosition(frame, calibration, displayRow, file)
                    result[key] = Bitmap.createScaledBitmap(crop, 120, 120, true)
                }
            }
        }
        return result
    }

    private fun keyFor(fenChar: Char, isLight: Boolean): String {
        val colorSuffix = if (isLight) "light" else "dark"
        if (fenChar == '.') return "empty_$colorSuffix"
        val colorPrefix = if (fenChar.isUpperCase()) "w" else "b"
        return "$colorPrefix${fenChar.uppercaseChar()}_$colorSuffix"
    }
}
