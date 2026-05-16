package com.sza.fastmediasorter.ui.player.helpers

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.PlaybackOrderMode

// Keep every playback-order entry point on the same icon and localized mode label.
internal data class PlaybackOrderUiState(
    @DrawableRes val iconResId: Int,
    @StringRes val labelResId: Int
)

internal fun PlaybackOrderMode.toPlaybackOrderUiState(): PlaybackOrderUiState = when (this) {
    PlaybackOrderMode.LOOP_LIST -> PlaybackOrderUiState(
        iconResId = R.drawable.ic_loop_list,
        labelResId = R.string.playback_order_loop_list
    )
    PlaybackOrderMode.PLAY_THROUGH -> PlaybackOrderUiState(
        iconResId = R.drawable.ic_arrow_downward,
        labelResId = R.string.playback_order_play_through
    )
    PlaybackOrderMode.SHUFFLE -> PlaybackOrderUiState(
        iconResId = R.drawable.ic_random_nav,
        labelResId = R.string.playback_order_shuffle
    )
    PlaybackOrderMode.REPEAT_ONE -> PlaybackOrderUiState(
        iconResId = R.drawable.ic_repeat_one,
        labelResId = R.string.playback_order_repeat_one
    )
}