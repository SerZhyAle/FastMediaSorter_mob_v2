package com.sza.fastmediasorter.wear.ui.player.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sza.fastmediasorter.wear.R

/**
 * S1683: an opaque black sheet over the whole player that any touch dismisses. It is deliberately not
 * a real display timeout, and S2166 halved the reason. The reason still holds with the background
 * playback setting off: the screen pauses on ON_STOP (S0902), so letting the watch sleep would stop
 * the playback this mode exists to keep alive. With the setting on and the track playing, the sleeping
 * watch keeps playing from the service, and what this sheet is left doing is keeping the screen
 * reachable in one touch rather than keeping the sound alive. On an OLED watch the pixels under an
 * opaque black sheet are unlit anyway, which is why the cheaper mode was never worth swapping in.
 *
 * S2815 moved it out of the audio player: the video player takes the same mode, and one exit gesture
 * plus one TalkBack description is a shared requirement rather than a coincidence between two copies.
 */
@Composable
internal fun PlayerDimOverlay(onExit: () -> Unit) {
    val exitDesc = stringResource(R.string.wear_screen_off_exit)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onExit
            )
            .semantics { contentDescription = exitDesc }
    )
}
