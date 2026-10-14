package com.sza.fastmediasorter.ui.streams.helpers

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.databinding.ItemDeviceBroadcastRowBinding
import com.sza.fastmediasorter.domain.model.ExchangeDeviceBroadcast
import com.sza.fastmediasorter.ui.streams.DeviceBroadcastsSectionState

/**
 * Renders the "Broadcasts of my devices" section of the streams list (S4116 phase 07): a header that opens and
 * closes it, a status line, and one row per broadcast. Rows are plain views - the list is a handful of live
 * broadcasts, never a catalog - and every one is D-pad and TalkBack reachable through its own focus.
 */
class StreamDeviceBroadcastsManager(
    private val section: View,
    private val header: View,
    private val chevron: ImageView,
    private val status: TextView,
    private val list: LinearLayout,
    private val onToggle: () -> Unit,
    private val onChosen: (ExchangeDeviceBroadcast) -> Unit,
) {

    init {
        header.setOnClickListener { onToggle() }
    }

    fun setAvailable(available: Boolean) {
        section.isVisible = available
    }

    fun render(state: DeviceBroadcastsSectionState) {
        val context = chevron.context
        chevron.setImageResource(if (state.expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
        val toggleLabel = if (state.expanded) R.string.streams_section_collapse else R.string.streams_section_expand
        chevron.contentDescription = context.getString(toggleLabel)
        header.contentDescription =
            "${context.getString(R.string.streams_section_device_broadcasts)}, ${context.getString(toggleLabel)}"
        val listed = state.listed.takeIf { state.expanded }
        val items = listed?.items.orEmpty()
        val statusText: Int? = when {
            !state.expanded -> null
            listed == null || (items.isEmpty() && listed.pending) -> R.string.streams_device_broadcasts_loading
            items.isEmpty() -> R.string.streams_device_broadcasts_empty
            listed.incomplete -> R.string.exchange_directory_some_unreachable
            else -> null
        }
        status.isVisible = statusText != null
        statusText?.let(status::setText)
        list.isVisible = items.isNotEmpty()
        list.removeAllViews()
        items.forEach { list.addView(row(it)) }
    }

    private fun row(item: ExchangeDeviceBroadcast): View {
        val context = list.context
        val row = ItemDeviceBroadcastRowBinding.inflate(LayoutInflater.from(context), list, false)
        row.tvBroadcastTitle.text = item.title?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.streams_device_broadcast_untitled)
        val parts = listOfNotNull(
            item.deviceName?.takeIf { it.isNotBlank() },
            context.getString(modeLabel(item.mode)),
            context.getString(R.string.streams_device_broadcast_unsupported).takeIf { !item.playable },
        )
        row.tvBroadcastDetails.text = parts.joinToString(DETAILS_SEPARATOR)
        row.root.setOnClickListener { onChosen(item) }
        return row.root
    }

    @StringRes
    private fun modeLabel(mode: String): Int = when (mode) {
        BroadcastMode.AUDIO_ONLY.name -> R.string.broadcast_entry_mode_audio_only
        BroadcastMode.VIDEO_ONLY.name -> R.string.broadcast_entry_mode_video_only
        else -> R.string.broadcast_entry_mode_video_audio
    }

    private companion object {
        const val DETAILS_SEPARATOR = " - "
    }
}
