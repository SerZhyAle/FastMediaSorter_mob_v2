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
    private val onPinRecent: (LauncherCellCommand) -> Unit = {},
    private val onRemoveRecent: (LauncherCellCommand) -> Unit = {},
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

    private val recentMenuManager = LauncherTaskbarRecentMenuManager(
        launchCommand = onCommand,
        pinCommand = onPinRecent,
        removeFromRecents = onRemoveRecent,
    )

    // Recents now carry any command kind (S1097), so the icon id is the encoded command - decode and
    // rerun it, exactly like the pinned strip, instead of assuming an app package.
    private val recentsAdapter = LauncherTaskbarIconAdapter(
        onIconClick = { icon -> LauncherCellCommand.decode(icon.id)?.let(onCommand) },
        onIconLongClick = { anchor, icon ->
            val command = LauncherCellCommand.decode(icon.id)
            if (command == null) {
                false
            } else {
                Timber.d("S2391: recents icon long press for %s", command)
                recentMenuManager.show(anchor, command)
            }
        },
    )

    private val pinnedAppMenuManager = LauncherTaskbarPinnedAppMenuManager(
        launchCommand = onCommand,
        unpin = onRemovePin,
    )

    // Only the pinned strip edits: its icons carry a pin position, so unpin routes by position and the
    // trailing "+" pins one more. Outside edit mode, installed apps also expose their narrow launch/unpin menu.
    private val pinnedAdapter = LauncherTaskbarIconAdapter(
        onIconClick = { icon -> LauncherCellCommand.decode(icon.id)?.let(onCommand) },
        onIconLongClick = { anchor, icon ->
            val command = LauncherCellCommand.decode(icon.id)
            if (command == null) false else pinnedAppMenuManager.show(anchor, command, icon.position)
        },
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

        lifecycleOwner.collectOnLifecycle(recents) { submitRecents(it) }
        lifecycleOwner.collectOnLifecycle(pinned) { pinnedAdapter.submitIcons(it) }
        lifecycleOwner.collectOnLifecycle(composition) { apply(it) }
    }

    /**
     * S2393: the rightmost recents cell is always the freshest launch (owner requirement).
     *
     * The journal hands its newest entry first and the row draws position 0 at the left edge, so the
     * strip is reversed here rather than upstream - "newest first" is the order every other reader of
     * the journal expects, and which end of a row a launch lands on is a rendering decision.
     *
     * Reversing alone is not enough: RecyclerView keeps the anchor the user was looking at, so on a
     * row narrower than the list the fresh end stays off screen until something scrolls to it. The
     * scroll waits for the commit callback because the diff is asynchronous - `itemCount` is still the
     * previous list's until then.
     */
    private fun submitRecents(icons: List<LauncherTaskbarIcon>) {
        Timber.d("S2393: recents strip submit, size=%d", icons.size)
        recentsAdapter.submitIcons(icons.reversed()) {
            val last = recentsAdapter.itemCount - 1
            if (last >= 0) {
                binding.taskbarRecents.scrollToPosition(last)
            }
        }
    }

    /** Symmetric with the listener [bind] attaches - the bar outlives no window, but nothing here relies on it. */
    override fun onDestroy(owner: LifecycleOwner) {
        binding.taskbarRecents.removeOnLayoutChangeListener(recentsLayoutListener)
        pinnedAppMenuManager.dismiss()
        recentMenuManager.dismiss()
    }

    /**
     * S2022: the last composition the settings flow delivered, re-applied whenever edit mode flips so the
     * two triggers never fight over the same views. Seeded to match the `AppSettings` defaults, so a
     * `setEditMode` call that lands before the first composition emission renders the same state the
     * first real emission would.
     */
    private var composition = LauncherTaskbarComposition(showRecents = true, showPinned = true, showTray = true)

    /** S2022: true while the desktop is being edited - the render step below reads it beside [composition]. */
    private var editing = false

    /**
     * S2022: recents and pinned add an unpin "X" / trailing "+" while editing (pinned only - it is the
     * strip edit mode's affordances live on); recents and the tray's indicator row are hidden outright
     * instead, because with everything on (indicators, recents, the "Add" and "Apply" buttons) the bar is
     * wider than the screen and the last two - Add, Apply - are the ones pushed off (owner report,
     * strategic §0).
     */
    fun setEditMode(on: Boolean) {
        pinnedAdapter.setEditMode(on)
        editing = on
        render()
    }

    /** Keep the taskbar surface in the same wallpaper-visible layer as desktop cell backdrops. */
    fun applyBackdropAlpha(alpha: Float) {
        binding.root.background.mutate().alpha = (alpha.coerceIn(0f, 1f) * OPAQUE_ALPHA).toInt()
    }

    private fun apply(newComposition: LauncherTaskbarComposition) {
        composition = newComposition
        render()
    }

    private fun render() {
        binding.taskbarRecents.isVisible = composition.showRecents && !editing
        binding.taskbarPinned.isVisible = composition.showPinned
        // S1431 ADR-5: the mode subordinates the tray rather than competing with it. The stored switch is
        // never written here, so turning the mode off restores whatever the user last chose.
        binding.trayContainer.isVisible = composition.showTray && !composition.topStatusStripMode
        // S2022: the clock stays (not named in the owner's report) - only the indicator row is gated on
        // editing, one level below the container so it can hide without taking the clock with it.
        binding.trayIndicators.root.isVisible = composition.showTray && !composition.topStatusStripMode && !editing
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

private const val OPAQUE_ALPHA = 255
