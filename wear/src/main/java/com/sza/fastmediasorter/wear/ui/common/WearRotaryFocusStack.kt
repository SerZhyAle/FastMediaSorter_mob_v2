package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Which rotary consumer currently owns the crown, ordered by the moment each entered the composition
 * (S2763).
 *
 * A rotary event reaches the focused node and nothing else, and this module composes several rotary
 * consumers over one another: a screen's list, the action cloud that opens on top of it, a dialog's own
 * list, a player's stepped volume. Each of them asks for focus once when it appears, so the last one to
 * appear wins - and when it leaves, the consumer underneath never asks again and the crown goes dead on
 * a screen where it worked a moment earlier. Ordering them fixes both halves at once: only the top of
 * the stack requests focus, and a removal makes the one below it top, which is a state change its own
 * effect can key on.
 *
 * Identity comparison, not equality: a token is an opaque marker whose only job is to be distinct from
 * every other live one.
 */
class WearRotaryFocusStack {

    private val owners = mutableStateListOf<Any>()

    /** Registers [token] as the newest rotary consumer; a token already present keeps its place. */
    fun push(token: Any) {
        if (owners.none { it === token }) {
            owners.add(token)
        }
    }

    /** Withdraws [token], handing ownership back to whichever consumer registered before it. */
    fun remove(token: Any) {
        val index = owners.indexOfLast { it === token }
        if (index >= 0) {
            owners.removeAt(index)
        }
    }

    /** True while [token] is the newest live consumer, and so the one rotation belongs to. */
    fun isTop(token: Any): Boolean = owners.lastOrNull() === token
}

/**
 * The stack shared by a screen and everything drawn over it, provided once for the whole watch UI.
 *
 * Null is a working default rather than a misconfiguration: an entry point that provides no stack - a
 * tile's configuration activity, a preview, a test - gets the single-consumer behaviour that preceded
 * S2763, where a rotary consumer simply asks for focus when it appears.
 */
val LocalWearRotaryFocusStack = staticCompositionLocalOf<WearRotaryFocusStack?> { null }
