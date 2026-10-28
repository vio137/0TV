package dev.tidytv.app.ui.screens

import dev.tidytv.core.ops.RemoteKey
import kotlin.math.abs

/**
 * Turns a finger dragging over the touchpad into arrow keys: one key for every [step] of
 * movement along the dominant axis. Movement on the other axis is dropped when a key is sent,
 * so a mostly-horizontal swipe never leaks a stray Up or Down. Units are whatever the caller
 * feeds in (pixels); [step] is the same unit (48dp converted to pixels).
 */
class TouchpadMath(private val step: Float) {
    private var ax = 0f
    private var ay = 0f

    init { require(step > 0f) }

    /** Adds a drag delta and returns the keys it earned, in order (usually none or one). */
    fun drag(dx: Float, dy: Float): List<RemoteKey> {
        ax += dx
        ay += dy
        var out: MutableList<RemoteKey>? = null
        while (maxOf(abs(ax), abs(ay)) >= step) {
            val key: RemoteKey
            if (abs(ax) >= abs(ay)) {
                key = if (ax > 0) RemoteKey.RIGHT else RemoteKey.LEFT
                ax -= if (ax > 0) step else -step
                ay = 0f
            } else {
                key = if (ay > 0) RemoteKey.DOWN else RemoteKey.UP
                ay -= if (ay > 0) step else -step
                ax = 0f
            }
            (out ?: mutableListOf<RemoteKey>().also { out = it }).add(key)
        }
        return out ?: emptyList()
    }

    /** A new gesture starts from nothing. */
    fun reset() { ax = 0f; ay = 0f }
}
