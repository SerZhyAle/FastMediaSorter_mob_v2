package com.sza.fastmediasorter.ui.player.helpers

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * WAVE-PARTICLES section 4 as amended in 0.12: a particle that crossed an edge is mirrored back inside
 * and turned inward. Flipping the velocity whenever the position is outside let a particle that a
 * multi-frame tick carried past the edge flip outward again on the next single-frame tick, while it was
 * still outside, and oscillate beyond the view for minutes.
 *
 * Call [inward] before [reflect]: the direction is chosen by the edge the unreflected position crossed.
 */
internal object WaveParticleEdge {

    /** The position after mirroring an overshoot back across the edge; always inside `[0, extent]`. */
    fun reflect(position: Float, extent: Float): Float = when {
        position < 0f -> min(-position, extent)
        position > extent -> max(extent + extent - position, 0f)
        else -> position
    }

    /** The velocity pointing away from the edge [position] crossed; unchanged while it is inside. */
    fun inward(position: Float, velocity: Float, extent: Float): Float = when {
        position < 0f -> abs(velocity)
        position > extent -> -abs(velocity)
        else -> velocity
    }
}
