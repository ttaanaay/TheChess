package com.chessbubble.vision

import android.graphics.Bitmap
import com.chessbubble.chess.BoardState
import com.chessbubble.chess.BoardState.Companion.squareOf
import com.chessbubble.model.BoardCalibration

/**
 * Solves the recurring "templates don't match live-capture scale" problem for
 * good: instead of importing template images from an external screenshot
 * (which is always at some different resolution/compression than what
 * MediaProjection actually captures), crop the 14 templates directly from a
 * live captured frame -- the SAME pipeline BoardRecognizer.recognize() uses
 * at runtime -- while the board is known to be at the standard starting
 * position (since the user is instructed to calibrate at game start anyway).
 */
object TemplateGenerator {

    private val startingBoard = BoardState.fromFen(BoardState.START_FEN)

    fun generateFromStartingPosition(frame: Bitmap, calibration: BoardCalibration): Map<String, Bitmap> {
        val result = mutableMapOf<String, Bitmap>()

        for (displayRow in 0 until 8) {
            for (file in 0 until 8) {
                val (boardFile, boardRank) = BoardRecognizer.displayPositionToBoardSquare(calibration, displayRow, file)
                val fenChar = startingBoard.board[squareOf(boardFile, boardRank)]
                val label = labelFor(fenChar, boardFile, boardRank)

                if (label !in result) {
                    val crop = BoardRecognizer.cropCellAtDisplayPosition(frame, calibration, displayRow, file)
                    result[label] = Bitmap.createScaledBitmap(crop, 120, 120, true)
                }
            }
        }
        return result
    }

    private fun labelFor(fenChar: Char, file: Int, rank: Int): String {
        if (fenChar == '.') {
            // Standard board coloring: a1 is dark; (file+rank) even => dark, odd => light.
            return if ((file + rank) % 2 == 0) "empty_dark" else "empty_light"
        }
        val colorPrefix = if (fenChar.isUpperCase()) "w" else "b"
        return colorPrefix + fenChar.uppercaseChar()
    }
}
