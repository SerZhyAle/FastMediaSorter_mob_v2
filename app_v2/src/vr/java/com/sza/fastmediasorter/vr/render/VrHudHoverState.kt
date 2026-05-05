package com.sza.fastmediasorter.vr.render

/**
 * Tiny per-frame holder for the currently-hovered HUD element id and a
 * "changed since last consumed" flag. Owned by [VrHudSceneDriver]; written
 * from the xr-render-thread by the input manager and read on the same thread
 * during the redraw decision.
 *
 * No allocations on any path. Single-writer (the input manager) — `@Volatile`
 * is sufficient; no atomic CAS.
 *
 * Convention: `0` means "no hover" (matches `VrHudElementRegistry` reserved id).
 */
class VrHudHoverState {

    @Volatile private var current: Int = 0
    @Volatile private var consumed: Int = 0

    /** Assign the latest hover id. Returns true when [id] differs from the previous value. */
    fun setCurrent(id: Int): Boolean {
        if (current == id) return false
        current = id
        return true
    }

    /** Read the latest hover id. */
    fun current(): Int = current

    /** Mark the current id as consumed by the painter. */
    fun markConsumed() {
        consumed = current
    }

    /** True when [setCurrent] has updated the value since the last [markConsumed]. */
    fun hasPendingChange(): Boolean = current != consumed
}
