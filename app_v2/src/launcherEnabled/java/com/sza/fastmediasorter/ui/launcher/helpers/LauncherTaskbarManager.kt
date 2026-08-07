package com.sza.fastmediasorter.ui.launcher.helpers

import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.databinding.LauncherTaskbarBinding
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.flow.Flow

/**
 * S0404: owns the taskbar's two icon strips and the visibility of its three configurable blocks.
 *
 * The bar is always on screen while the desktop is: playback happens in separate activities
 * ([com.sza.fastmediasorter.ui.player.PlayerActivity] / StreamsActivity), so it is inherently absent
 * during fullscreen playback - strategic §3.1.7 needs no code of its own.
 */
class LauncherTaskbarManager(
    private val lifecycleOwner: LifecycleOwner,
    private val binding: LauncherTaskbarBinding,
    private val onCommand: (LauncherCellCommand) -> Unit,
    private val onStartClick: () -> Unit,
    private val onAllAppsClick: () -> Unit = {},
    private val onAddPin: () -> Unit = {},
    private val onRemovePin: (position: Int) -> Unit = {},
) {

    // Recents now carry any command kind (S1097), so the icon id is the encoded command - decode and
    // rerun it, exactly like the pinned strip, instead of assuming an app package.
    private val recentsAdapter = LauncherTaskbarIconAdapter(
        onIconClick = { icon -> LauncherCellCommand.decode(icon.id)?.let(onCommand) },
    )

    // Only the pinned strip edits: its icons carry a pin position, so unpin routes by position and the
    // trailing "+" pins one more. Recents cannot be pinned/unpinned, so its adapter takes no edit hooks.
    private val pinnedAdapter = LauncherTaskbarIconAdapter(
        onIconClick = { icon -> LauncherCellCommand.decode(icon.id)?.let(onCommand) },
        onRemoveClick = { icon -> onRemovePin(icon.position) },
        onAddClick = onAddPin,
    )

    fun bind(
        recents: Flow<List<LauncherTaskbarIcon>>,
        pinned: Flow<List<LauncherTaskbarIcon>>,
        composition: Flow<LauncherTaskbarComposition>,
    ) {
        binding.btnStart.setOnClickListener { onStartClick() }
        binding.btnAllApps.setOnClickListener { onAllAppsClick() }
        binding.taskbarRecents.layoutManager =
            LinearLayoutManager(binding.root.context, LinearLayoutManager.HORIZONTAL, false)
        binding.taskbarRecents.adapter = recentsAdapter
        binding.taskbarPinned.layoutManager =
            LinearLayoutManager(binding.root.context, LinearLayoutManager.HORIZONTAL, false)
        binding.taskbarPinned.adapter = pinnedAdapter

        lifecycleOwner.collectOnLifecycle(recents) { recentsAdapter.submitIcons(it) }
        lifecycleOwner.collectOnLifecycle(pinned) { pinnedAdapter.submitIcons(it) }
        lifecycleOwner.collectOnLifecycle(composition) { apply(it) }
    }

    /** Edit mode adds an unpin "X" to every pin and a trailing "+"; recents are unaffected. */
    fun setEditMode(on: Boolean) {
        pinnedAdapter.setEditMode(on)
    }

    private fun apply(composition: LauncherTaskbarComposition) {
        binding.taskbarRecents.isVisible = composition.showRecents
        binding.taskbarPinned.isVisible = composition.showPinned
        binding.trayContainer.isVisible = composition.showTray
    }
}

/** Which taskbar blocks the user kept (settings-driven, strategic §3.3). */
data class LauncherTaskbarComposition(
    val showRecents: Boolean,
    val showPinned: Boolean,
    val showTray: Boolean,
)
