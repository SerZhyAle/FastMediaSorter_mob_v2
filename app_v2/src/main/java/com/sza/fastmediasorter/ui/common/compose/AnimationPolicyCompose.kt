package com.sza.fastmediasorter.ui.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.sza.fastmediasorter.core.util.AnimationIntent
import com.sza.fastmediasorter.core.util.AnimationPolicy
import timber.log.Timber

/**
 * S2567: the Compose-side reader of [AnimationPolicy].
 *
 * The policy keeps its level in a `@Volatile` field because the sites that ask are custom views,
 * adapters and helper managers reading from a draw or click path, with no injection available
 * (S2250/S2536). Compose cannot subscribe to that: reading the field from a composable produces the
 * right answer once and never recomposes, so an animation frozen at ten percent charge would stay
 * frozen after the battery recovered. This bridge turns the existing listener into snapshot state so
 * a Compose island gets both halves - the current answer and a recomposition when it changes.
 *
 * It lives here rather than inside [AnimationPolicy] on purpose: putting snapshot state in
 * `core/util` would pull `androidx.compose.runtime` into a package that classic view draw paths read,
 * and spread Compose deeper into `app_v2` against the direction of CLAUDE.md Rule 32.
 */
@Composable
fun rememberAnimationAllowed(intent: AnimationIntent): Boolean {
    val allowed by produceState(initialValue = AnimationPolicy.mayAnimate(intent), intent) {
        // The listener runs on whichever thread changed the level, which is the settings collector's
        // rather than the main one. A snapshot write is safe from any thread and the recomposer
        // applies it on its own frame clock, so this needs no handler hop - unlike the view-based
        // sites, which post because they touch views, not because they write state.
        val listener: () -> Unit = {
            value = AnimationPolicy.mayAnimate(intent)
            Timber.d("S2567: bridge heard level=${AnimationPolicy.level} mayAnimate($intent)=$value")
        }
        AnimationPolicy.addLevelListener(listener)
        // A change landing between the seed read above and the subscription fires into nobody, so the
        // island would hold a stale answer until the next change. Re-reading here closes that window.
        value = AnimationPolicy.mayAnimate(intent)
        awaitDispose { AnimationPolicy.removeLevelListener(listener) }
    }
    return allowed
}
