package com.chessbubble.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Template images keyed as "<wP|wN|...|bK>_<light|dark>" for pieces, or
 * "empty_<light|dark>" for empty squares -- see TemplateGenerator for why
 * templates are split by square color. Queen and king only ever get ONE
 * color variant from the starting position (they don't appear on both
 * colors initially), so not every key is guaranteed to exist.
 */
class PieceTemplates internal constructor(
    val templates: Map<String, Bitmap>
) {
    companion object {
        /** Legacy fixed set, still used by the bundled-assets fallback path. */
        val LABELS = listOf(
            "wP", "wN", "wB", "wR", "wQ", "wK",
            "bP", "bN", "bB", "bR", "bQ", "bK",
            "empty_light", "empty_dark"
        )

        /** Maps a template key ("wP_light", "bK_dark", "empty_light", ...) to its FEN char ('.' for empty). */
        fun fenCharForKey(key: String): Char {
            if (key.startsWith("empty")) return '.'
            val base = key.substringBefore("_") // e.g. "wP"
            val colorPrefix = base[0]
            val pieceLetter = base[1]
            return if (colorPrefix == 'w') pieceLetter.uppercaseChar() else pieceLetter.lowercaseChar()
        }

        /** Bundled fallback templates (no light/dark split) -- only used if no generated set exists yet. */
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

        private fun generatedDir(context: Context) = java.io.File(context.filesDir, "templates/default")

        /** Loads whatever auto-generated template keys exist (see TemplateGenerator), or null if none. */
        fun loadGenerated(context: Context): PieceTemplates? {
            val dir = generatedDir(context)
            val files = dir.listFiles { f -> f.extension == "png" } ?: return null
            if (files.isEmpty()) return null
            val map = mutableMapOf<String, Bitmap>()
            for (file in files) {
                val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: continue
                map[file.nameWithoutExtension] = bmp
            }
            return if (map.isEmpty()) null else PieceTemplates(map)
        }

        /** Persists a freshly generated template set (see TemplateGenerator) to internal storage, replacing any previous set. */
        fun saveGenerated(context: Context, bitmaps: Map<String, Bitmap>) {
            val dir = generatedDir(context)
            dir.listFiles()?.forEach { it.delete() } // clear any stale keys from a previous calibration
            dir.mkdirs()
            for ((key, bmp) in bitmaps) {
                java.io.FileOutputStream(java.io.File(dir, "$key.png")).use {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }
}
