package com.sza.fastmediasorter.ui.streams.helpers

import android.content.res.ColorStateList
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.streams.StreamsViewModel.MediaKindFilter
import timber.log.Timber

/**
 * S1473/S3061: the inline video/audio/own category trigger beside the streams search field.
 *
 * The lit icon is the active category, all neutral means no filter, and tapping the lit icon clears
 * it. The group holds no state of its own; it renders the shared filter state so it cannot disagree
 * with the filter dialog.
 *
 * The active tint is a fixed colour rather than a theme attribute because the app ships a red colour
 * theme; the neutral tint follows the row's existing orientation repaint, since the row moves onto
 * the primary-coloured toolbar in landscape where a control tint would vanish.
 */
class StreamsMediaKindTriggerManager(
    private val videoButton: ImageButton,
    private val audioButton: ImageButton,
    private val ownButton: ImageButton,
    private val onKindSelected: (MediaKindFilter) -> Unit,
) {
    private var landscape: Boolean = false
    private var rendered: MediaKindFilter = MediaKindFilter.ALL

    fun bind() {
        Timber.d("S3061: video/audio/own category trigger bound")
        videoButton.setOnClickListener { onKindSelected(toggled(MediaKindFilter.VIDEO)) }
        audioButton.setOnClickListener { onKindSelected(toggled(MediaKindFilter.AUDIO)) }
        ownButton.setOnClickListener { onKindSelected(toggled(MediaKindFilter.OWN)) }
    }

    fun render(mediaKind: MediaKindFilter, isLandscape: Boolean = landscape) {
        landscape = isLandscape
        rendered = mediaKind
        paint(videoButton, mediaKind == MediaKindFilter.VIDEO, R.string.streams_media_filter_video)
        paint(audioButton, mediaKind == MediaKindFilter.AUDIO, R.string.streams_media_filter_audio)
        paint(ownButton, mediaKind == MediaKindFilter.OWN, R.string.streams_media_filter_own)
    }

    private fun toggled(tapped: MediaKindFilter): MediaKindFilter =
        if (rendered == tapped) MediaKindFilter.ALL else tapped

    private fun paint(button: ImageButton, isActive: Boolean, selectLabel: Int) {
        button.imageTintList = if (isActive) activeTint(button) else neutralTint(button)
        // Selection state and the spoken label both carry the state, so colour is never the only signal.
        button.isSelected = isActive
        button.contentDescription =
            button.context.getString(if (isActive) R.string.streams_media_filter_clear else selectLabel)
    }

    private fun activeTint(button: ImageButton): ColorStateList =
        ColorStateList.valueOf(
            ContextCompat.getColor(button.context, R.color.streams_media_filter_active)
        )

    private fun neutralTint(button: ImageButton): ColorStateList {
        val attr = if (landscape) {
            com.google.android.material.R.attr.colorOnPrimary
        } else {
            androidx.appcompat.R.attr.colorControlNormal
        }
        return ColorStateList.valueOf(MaterialColors.getColor(button, attr))
    }
}
