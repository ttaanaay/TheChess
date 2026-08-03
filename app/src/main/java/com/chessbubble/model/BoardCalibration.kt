package com.chessbubble.model

import android.content.Context
import org.json.JSONObject

/**
 * The 4 corners of the chess board as seen in the *full captured screen frame*,
 * in order: top-left, top-right, bottom-right, bottom-left.
 * Points are stored as fractions (0f..1f) of the captured frame's width/height
 * so calibration survives resolution changes (e.g. rotation, different device).
 */
data class BoardCalibration(
    val topLeft: PointF2,
    val topRight: PointF2,
    val bottomRight: PointF2,
    val bottomLeft: PointF2,
    /** true if the board is shown flipped (black at the bottom shown as rank 1 visually) */
    val boardFlipped: Boolean = false
) {
    fun toJson(): String {
        val o = JSONObject()
        o.put("tl", topLeft.toJson())
        o.put("tr", topRight.toJson())
        o.put("br", bottomRight.toJson())
        o.put("bl", bottomLeft.toJson())
        o.put("flipped", boardFlipped)
        return o.toString()
    }

    companion object {
        fun fromJson(s: String): BoardCalibration {
            val o = JSONObject(s)
            return BoardCalibration(
                topLeft = PointF2.fromJson(o.getJSONObject("tl")),
                topRight = PointF2.fromJson(o.getJSONObject("tr")),
                bottomRight = PointF2.fromJson(o.getJSONObject("br")),
                bottomLeft = PointF2.fromJson(o.getJSONObject("bl")),
                boardFlipped = o.optBoolean("flipped", false)
            )
        }

        private const val PREFS = "chess_bubble_prefs"
        private const val KEY = "board_calibration"

        fun save(context: Context, calibration: BoardCalibration): Boolean {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, calibration.toJson())
                .commit() // synchronous on purpose: caller finishes the Activity right after
        }

        fun load(context: Context): BoardCalibration? {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null) ?: return null
            return runCatching { fromJson(raw) }.getOrNull()
        }
    }
}

data class PointF2(val x: Float, val y: Float) {
    fun toJson(): JSONObject = JSONObject().put("x", x.toDouble()).put("y", y.toDouble())
    companion object {
        fun fromJson(o: JSONObject) = PointF2(o.getDouble("x").toFloat(), o.getDouble("y").toFloat())
    }
}
