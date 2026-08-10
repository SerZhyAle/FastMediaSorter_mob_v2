package com.sza.fastmediasorter.ui.launcher.helpers

import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.LauncherTaskbarBinding
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

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
    private val onRecentsCapacity: (Int) -> Unit = {},
) : DefaultLifecycleObserver {

    /** One recents cell: the icon plus the padding the item layout puts on each side of it. */
    private val recentsItemWidth = with(binding.root.resources) {
        getDimensionPixelSize(R.dimen.launcher_taskbar_icon_size) +
            2 * getDimensionPixelSize(R.dimen.launcher_taskbar_item_spacing)
    }

    /** Reported capacities are deduplicated here so a layout pass that changed nothing re-queries nothing. */
    private var reportedRecentsCapacity = 0

    private val recentsLayoutListener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        reportRecentsCapacity(view.width)
    }

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

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
        binding.taskbarRecents.addOnLayoutChangeListener(recentsLayoutListener)

        lifecycleOwner.collectOnLifecycle(recents) { recentsAdapter.submitIcons(it) }
        lifecycleOwner.collectOnLifecycle(pinned) { pinnedAdapter.submitIcons(it) }
        lifecycleOwner.collectOnLifecycle(composition) { apply(it) }
    }

    /** Symmetric with the listener [bind] attaches - the bar outlives no window, but nothing here relies on it. */
    override fun onDestroy(owner: LifecycleOwner) {
        binding.taskbarRecents.removeOnLayoutChangeListener(recentsLayoutListener)
    }

    /** Edit mode adds an unpin "X" to every pin and a trailing "+"; recents are unaffected. */
    fun setEditMode(on: Boolean) {
        pinnedAdapter.setEditMode(on)
    }

    private fun apply(composition: LauncherTaskbarComposition) {
        binding.taskbarRecents.isVisible = composition.showRecents
        binding.taskbarPinned.isVisible = composition.showPinned
        // S1431 ADR-5: the mode subordinates the tray rather than competing with it. The stored switch is
        // never written here, so turning the mode off restores whatever the user last chose.
        binding.trayContainer.isVisible = composition.showTray && !composition.topStatusStripMode
    }

    /**
     * S1431 ADR-4: the recents list asks for as many entries as this row can actually show, so it grows when
     * the tray leaves the bar and differs between the orientations - one fixed number could do neither.
     *
     * A width of zero is ignored rather than published: the row reports one before its first layout pass,
     * and a capacity derived from it would arrive as "fits nothing" exactly when the list is first shown.
     */
    private fun reportRecentsCapacity(width: Int) {
        if (width <= 0 || recentsItemWidth <= 0) {
            return
        }
        val capacity = width / recentsItemWidth
        if (capacity == reportedRecentsCapacity) {
            return
        }
        reportedRecentsCapacity = capacity
        Timber.d("S1431: taskbar tray visible=%s recents capacity=%d", binding.trayContainer.isVisible, capacity)
        onRecentsCapacity(capacity)
    }
}

/** Which taskbar blocks the user kept (settings-driven, strategic §3.3). */
data class LauncherTaskbarComposition(
    val showRecents: Boolean,
    val showPinned: Boolean,
    val showTray: Boolean,
    /** S1431: while the top strip carries the indicators, the tray is hidden whatever [showTray] says. */
    val topStatusStripMode: Boolean = false,
)
