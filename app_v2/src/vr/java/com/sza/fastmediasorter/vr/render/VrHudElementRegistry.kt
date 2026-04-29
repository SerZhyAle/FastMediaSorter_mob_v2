package com.sza.fastmediasorter.vr.render

import android.graphics.RectF

/**
 * Per-frame registry of interactive HUD elements. Owned by [VrHudSceneComposer]:
 * cleared at the start of each draw pass via [beginFrame] and refilled as the
 * composer paints zones.
 *
 * Storage is two parallel mutable lists reused across frames; after warm-up no
 * allocations occur on hot paths. `O(N)` linear scan in [elementAt] is the
 * explicit policy from S0024 §3.2 (N << 50 in practice).
 */
class VrHudElementRegistry(
    val width: Int,
    val height: Int,
) {

    private val elements: MutableList<VrHudElement> = ArrayList(INITIAL_CAPACITY)
    private val callbacks: MutableList<() -> Unit> = ArrayList(INITIAL_CAPACITY)
    private val boundsPool: MutableList<RectF> = ArrayList(INITIAL_CAPACITY)
    private var size: Int = 0

    /** Drop all entries from the previous frame. */
    fun beginFrame() {
        size = 0
        elements.clear()
        callbacks.clear()
    }

    /**
     * Append a new element. Throws when [id] was already registered in the
     * current frame — duplicate ids would make hit-testing and dispatch
     * non-deterministic. The [bounds] rect is copied into a pooled instance,
     * so the caller may reuse a single tmp RectF across all registrations.
     */
    fun register(id: Int, bounds: RectF, label: String, onClick: () -> Unit) {
        for (i in elements.indices) {
            if (elements[i].id == id) {
                throw IllegalStateException("Duplicate HUD element id=$id (label=$label) in current frame")
            }
        }
        val pooled = if (size < boundsPool.size) boundsPool[size] else RectF().also { boundsPool.add(it) }
        pooled.set(bounds)
        elements.add(VrHudElement(id, pooled, label))
        callbacks.add(onClick)
        size++
    }

    /**
     * Linear scan for the element under UV coordinates. Returns the first match
     * or `null` if the UV is outside every registered rect.
     */
    fun elementAt(u: Float, v: Float): VrHudElement? {
        val px = u * width
        val py = v * height
        for (i in elements.indices) {
            val e = elements[i]
            if (e.bounds.contains(px, py)) return e
        }
        return null
    }

    /** Callback associated with [id], or `null` if unknown in the current frame. */
    fun callbackOf(id: Int): (() -> Unit)? {
        for (i in elements.indices) {
            if (elements[i].id == id) return callbacks[i]
        }
        return null
    }

    companion object {
        private const val INITIAL_CAPACITY = 16
    }
}
