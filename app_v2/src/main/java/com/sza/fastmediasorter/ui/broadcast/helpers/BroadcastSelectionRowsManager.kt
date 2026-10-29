package com.sza.fastmediasorter.ui.broadcast.helpers

import android.view.View
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.databinding.ActivityBroadcastControlBinding

/**
 * S4336: the broadcast mode and the camera lens as canonical dropdown rows instead of chip rows -
 * the owner moved both choices to a list. The option lists live here: the mode entries are static,
 * the lens entries arrive with every [renderLens] call from the phone's lens enumeration.
 */
class BroadcastSelectionRowsManager {

    /** Modes in display order; the row hands back an index, the screen wants a [BroadcastMode]. */
    private val modeEntries = listOf(
        BroadcastMode.AUDIO_ONLY,
        BroadcastMode.VIDEO_AUDIO,
        BroadcastMode.VIDEO_ONLY,
    )

    /** Ids behind the lens row's current entries, so a lens id from the live state syncs to an index. */
    private var lensOptionIds: List<String> = emptyList()

    private var modeSelectedListener: ((BroadcastMode) -> Unit)? = null
    private var lensSelectedListener: ((String) -> Unit)? = null

    fun setup(
        binding: ActivityBroadcastControlBinding,
        onModeSelected: (BroadcastMode) -> Unit,
        onLensSelected: (String) -> Unit,
    ) {
        modeSelectedListener = onModeSelected
        lensSelectedListener = onLensSelected
        val context = binding.root.context
        binding.rowBroadcastMode.setEntries(
            listOf(
                context.getString(R.string.broadcast_entry_mode_audio_only),
                context.getString(R.string.broadcast_entry_mode_video_audio),
                context.getString(R.string.broadcast_entry_mode_video_only),
            )
        )
        binding.rowBroadcastMode.setSelection(modeEntries.indexOf(BroadcastMode.AUDIO_ONLY))
        binding.rowBroadcastMode.setOnItemSelectedListener { index ->
            modeEntries.getOrNull(index)?.let { modeSelectedListener?.invoke(it) }
        }
        binding.rowLensSelection.setOnItemSelectedListener { index ->
            lensOptionIds.getOrNull(index)?.let { lensSelectedListener?.invoke(it) }
        }
    }

    /**
     * Paints the lens choice: visibility, option list and the standing selection. Entries are
     * rebuilt on every call - the phone's enumeration can change while a session holds the camera.
     */
    fun renderLens(
        binding: ActivityBroadcastControlBinding,
        lensUi: BroadcastEntryUi.LensUi,
    ) {
        binding.rowLensSelection.visibility = if (lensUi.visible) View.VISIBLE else View.GONE
        if (!lensUi.visible) return
        val context = binding.root.context
        lensOptionIds = lensUi.options.map { it.id }
        val labels = BroadcastLensLabelFormatter.labels(lensUi.options) { facing ->
            context.getString(BroadcastLensLabelFormatter.facingNameRes(facing))
        }
        binding.rowLensSelection.setEntries(labels)
        val selected = lensUi.selectedLensId?.let { lensOptionIds.indexOf(it) } ?: -1
        binding.rowLensSelection.setSelection(selected)
    }

    /** Keeps the mode row on the mode on air; a selection made here fires no listener. */
    fun syncMode(binding: ActivityBroadcastControlBinding, modeName: String) {
        val mode = when (modeName) {
            BroadcastMode.VIDEO_AUDIO.name -> BroadcastMode.VIDEO_AUDIO
            BroadcastMode.VIDEO_ONLY.name -> BroadcastMode.VIDEO_ONLY
            else -> BroadcastMode.AUDIO_ONLY
        }
        binding.rowBroadcastMode.setSelection(modeEntries.indexOf(mode))
    }

    /** Keeps the lens row on the lens on air; no answer yet leaves the row as the user left it. */
    fun syncLens(binding: ActivityBroadcastControlBinding, lensId: String?) {
        if (lensId == null) return
        binding.rowLensSelection.setSelection(lensOptionIds.indexOf(lensId))
    }

    /**
     * Drops what the screen handed in. The owner is a singleton that outlives the activity, and both
     * listeners capture that activity and its binding.
     */
    fun clear() {
        modeSelectedListener = null
        lensSelectedListener = null
        lensOptionIds = emptyList()
    }

    /** Both rows follow the live-switch lock: no list opens while the session is re-opening. */
    fun setEnabled(binding: ActivityBroadcastControlBinding, enabled: Boolean) {
        binding.rowBroadcastMode.isEnabled = enabled
        binding.rowLensSelection.isEnabled = enabled
    }
}
