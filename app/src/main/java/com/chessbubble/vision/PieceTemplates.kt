package com.chessbubble.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Template images for the 12 piece types + empty light/dark squares, used for
 * simple template-matching recognition. Populate these once per board theme:
 *
 *   assets/templates/<themeName>/wP.png, wN.png, wB.png, wR.png, wQ.png, wK.png,
 *                                 bP.png, bN.png, bB.png, bR.png, bQ.png, bK.png,
 *                                 empty_light.png, empty_dark.png
 *
 * Crop each template tightly to a single square from a screenshot of the
 * target chess app/site, all at the same resolution the app will run at
 * (or this class will resize them to match at load time).
 */
class PieceTemplates private constructor(
    val templates: Map<String, Bitmap>
) {
    companion object {
        val LABELS = listOf(
            "wP", "wN", "wB", "wR", "wQ", "wK",
            "bP", "bN", "bB", "bR", "bQ", "bK",
            "empty_light", "empty_dark"
        )

        /** FEN piece char for each non-empty label. */
        val LABEL_TO_FEN_CHAR: Map<String, Char> = mapOf(
            "wP" to 'P', "wN" to 'N', "wB" to 'B', "wR" to 'R', "wQ" to 'Q', "wK" to 'K',
            "bP" to 'p', "bN" to 'n', "bB" to 'b', "bR" to 'r', "bQ" to 'q', "bK" to 'k'
        )

        fun loadFromAssets(context: Context, themeName: String): PieceTemplates {
            val map = mutableMapOf<String, Bitmap>()
            for (label in LABELS) {
                val path = "templates/$themeName/$label.png"
                context.assets.open(path).use { stream ->
                    map[label] = BitmapFactory.decodeStream(stream)
                }
            }
            return PieceTemplates(map)
        }
    }
}
