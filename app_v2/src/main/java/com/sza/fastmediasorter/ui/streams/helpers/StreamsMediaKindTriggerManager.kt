package com.sza.fastmediasorter.ui.streams.helpers

import android.content.res.ColorStateList
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.streams.StreamsViewModel.MediaKindFilter

/**
 * S1473: the inline audio/video facet trigger that sits right of the streams search field.
 *
 * Three states on two icons, per the owner's ruling: the lit icon is the active facet, both
 * neutral means no filter, and tapping the lit icon clears it. The pair holds no state of its own -
 * it renders whatever the shared filter state says, so it can never disagree with the filter dialog.
 *
 * The active tint is a fixed colour rather than a theme attribute because the app ships a red colour
 * theme; the neutral tint follows the row's existing orientation repaint, since the row moves onto
 * the primary-coloured toolbar in landscape where a control tint would vanish.
 */
class StreamsMediaKindTriggerManager(
    private val videoButton: ImageButton,
    private val audioButton: ImageButton,
    private val onKindSelected: (MediaKindFilter) -> Unit,
) {
    private var landscape: Boolean = false
    private var rendered: MediaKindFilter = MediaKindFilter.ALL

    fun bind() {
        videoButton.setOnClickListener { onKindSelected(toggled(MediaKindFilter.VIDEO)) }
        audioButton.setOnClickListener { onKindSelected(toggled(MediaKindFilter.AUDIO)) }
    }

    fun render(mediaKind: MediaKindFilter, isLandscape: Boolean = landscape) {
        landscape = isLandscape
        rendered = mediaKind
        paint(videoButton, mediaKind == MediaKindFilter.VIDEO, R.string.streams_media_filter_video)
        paint(audioButton, mediaKind == MediaKindFilter.AUDIO, R.string.streams_media_filter_audio)
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
