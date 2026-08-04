package com.chessbubble.overlay

/**
 * Direct in-process callback bridge between ScreenCaptureService and
 * OverlayService (both run in the same app process by default). Replaces the
 * previous Intent-broadcast mechanism, which added a consistent ~500ms
 * delivery delay -- Android defers broadcast delivery for background apps as
 * a battery-saving measure, and a plain function call has no such overhead.
 */
object OverlayBridge {
    var listener: ((white: Boolean, san: String, quality: String, color: Int) -> Unit)? = null

    fun notifyMove(white: Boolean, san: String, quality: String, color: Int) {
        listener?.invoke(white, san, quality, color)
    }
}
