package com.chessbubble.overlay

/**
 * Direct in-process callback bridge between ScreenCaptureService and
 * OverlayService (both run in the same app process by default). Replaces the
 * previous Intent-broadcast mechanism, which added a consistent ~500ms
 * delivery delay -- Android defers broadcast delivery for background apps as
 * a battery-saving measure, and a plain function call has no such overhead.
 *
 * @param bestMoveSan what the engine says should have been played instead,
 *        in SAN (e.g. "Nf3") -- pass null to hide (e.g. the move played WAS
 *        the best move, or the engine had nothing better).
 * @param nextMoveSan the engine's top suggestion for the side to move NOW,
 *        in SAN -- pass null to hide.
 */
object OverlayBridge {
    var listener: ((white: Boolean, san: String, quality: String, color: Int, bestMoveSan: String?, nextMoveSan: String?) -> Unit)? = null

    fun notifyMove(
        white: Boolean,
        san: String,
        quality: String,
        color: Int,
        bestMoveSan: String? = null,
        nextMoveSan: String? = null
    ) {
        listener?.invoke(white, san, quality, color, bestMoveSan, nextMoveSan)
    }
}
