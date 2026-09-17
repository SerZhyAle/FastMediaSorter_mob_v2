package com.sza.fastmediasorter.ui.launcher.helpers

import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.GridLayoutManager
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
    private val callbacks: LauncherTaskbarCallbacks,
) : DefaultLifecycleObserver {

    /** One recents cell: the icon plus the padding the item layout puts on each side of it. */
    private val recentsItemWidth = with(binding.root.resources) {
        getDimensionPixelSize(R.dimen.launcher_taskbar_icon_size) +
            2 * getDimensionPixelSize(R.dimen.launcher_taskbar_item_spacing)
    }

    /** S3131: the height of one taskbar row, and the unit the whole bar's height is a multiple of. */
    private val rowHeight = binding.root.resources.getDimensionPixelSize(R.dimen.launcher_taskbar_height)

    /** Reported capacities are deduplicated here so a layout pass that changed nothing re-queries nothing. */
    private var reportedRecentsCapacity = 0

    /**
     * S3131: the row count the views currently carry. Zero until the first render, so the seeded
     * composition still reaches [applyRows] once even though it holds the default row count.
     */
    private var appliedRows = 0

    private val recentsLayoutListener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        reportRecentsCapacity(view.width)
    }

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    private val recentMenuManager = LauncherTaskbarRecentMenuManager(
        launchCommand = callbacks.onCommand,
        pinCommand = callbacks.onPinRecent,
        removeFromRecents = callbacks.onRemoveRecent,
    )

    // Recents now carry any command kind (S1097), so the icon id is the encoded command - decode and
    // rerun it, exactly like the pinned strip, instead of assuming an app package.
    private val recentsAdapter = LauncherTaskbarIconAdapter(
        onIconClick = { icon -> LauncherCellCommand.decode(icon.id)?.let(callbacks.onCommand) },
        onIconLongClick = { anchor, icon ->
            val command = LauncherCellCommand.decode(icon.id)
            if (command == null) {
                false
            } else {
                recentMenuManager.show(anchor, command)
            }
        },
    )

    // S3208: adapter for upper recents slots located directly above Start and All Apps buttons when rows > 1.
    private val upperRecentsAdapter = LauncherTaskbarIconAdapter(
        onIconClick = { icon -> LauncherCellCommand.decode(icon.id)?.let(callbacks.onCommand) },
        onIconLongClick = { anchor, icon ->
            val command = LauncherCellCommand.decode(icon.id)
            if (command == null) {
                false
            } else {
                recentMenuManager.show(anchor, command)
            }
        },
    )

    private val pinnedAppMenuManager = LauncherTaskbarPinnedAppMenuManager(
        launchCommand = callbacks.onCommand,
        unpin = callbacks.onRemovePin,
    )

    // Only the pinned strip edits: its icons carry a pin position, so unpin routes by position and the
    // trailing "+" pins one more. Outside edit mode, installed apps also expose their narrow launch/unpin menu.
    private val pinnedAdapter = LauncherTaskbarIconAdapter(
        onIconClick = { icon -> LauncherCellCommand.decode(icon.id)?.let(callbacks.onCommand) },
        onIconLongClick = { anchor, icon ->
            val command = LauncherCellCommand.decode(icon.id)
            if (command == null) false else pinnedAppMenuManager.show(anchor, command, icon.position)
        },
        onRemoveClick = { icon -> callbacks.onRemovePin(icon.position) },
        onAddClick = callbacks.onAddPin,
    )

    fun bind(
        recents: Flow<List<LauncherTaskbarIcon>>,
        pinned: Flow<List<LauncherTaskbarIcon>>,
        composition: Flow<LauncherTaskbarComposition>,
    ) {
        binding.btnStart.setOnClickListener { callbacks.onStartClick() }
        binding.btnAllApps.setOnClickListener { callbacks.onAllAppsClick() }
        // S3131: the layout managers are created by applyRows, so the row count has exactly one owner.
        // Qualified, because the flow parameter of this function shadows the field it is collected into.
        applyRows(this.composition.rows)
        binding.taskbarRecents.adapter = recentsAdapter
        binding.taskbarUpperRecents.adapter = upperRecentsAdapter
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
     * S3208: when rows > 1 and total recents exceed the main strip capacity, the older entries
     * spill over into the upper recents slots located above Start & All Apps buttons.
     */
    private fun submitRecents(icons: List<LauncherTaskbarIcon>) {
        val reversed = icons.reversed()
        val extraSlots = if (appliedRows > 1) (appliedRows - 1) * 2 else 0
        val mainWidth = binding.taskbarRecents.width
        val mainCapacity = if (mainWidth > 0 && recentsItemWidth > 0) {
            (mainWidth / recentsItemWidth) * appliedRows
        } else {
            reversed.size
        }

        val overflowCount = if (extraSlots > 0 && reversed.size > mainCapacity) {
            (reversed.size - mainCapacity).coerceIn(0, extraSlots)
        } else {
            0
        }
        upperRecentsFilled = overflowCount > 0
        Timber.d("S3208: recents ${reversed.size}, main capacity $mainCapacity, above actions $overflowCount")
        updateActionsFocusUp()

        if (overflowCount > 0) {
            val upperIcons = reversed.take(overflowCount)
            val mainIcons = reversed.drop(overflowCount)
            upperRecentsAdapter.submitIcons(upperIcons)
            recentsAdapter.submitIcons(mainIcons) {
                val last = recentsAdapter.itemCount - 1
                if (last >= 0) {
                    binding.taskbarRecents.scrollToPosition(last)
                }
            }
        } else {
            upperRecentsAdapter.submitIcons(emptyList())
            recentsAdapter.submitIcons(reversed) {
                val last = recentsAdapter.itemCount - 1
                if (last >= 0) {
                    binding.taskbarRecents.scrollToPosition(last)
                }
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

    /** S3208: whether the last recents submission spilled any icon into the cells above the action buttons. */
    private var upperRecentsFilled = false

    /**
     * S3208: Up from Start / All Apps lands on the recents icon above them when one is drawn there. The
     * static `nextFocusUp` in the layout cannot express that, because the cells above are empty at one row,
     * with recents off, while editing, or when the journal is short.
     */
    private fun updateActionsFocusUp() {
        val target = if (binding.taskbarUpperRecents.isVisible && upperRecentsFilled) {
            R.id.taskbarUpperRecents
        } else {
            R.id.launcherDesktop
        }
        binding.btnStart.nextFocusUpId = target
        binding.btnAllApps.nextFocusUpId = target
        Timber.d("S3208: action buttons focus up -> ${binding.root.resources.getResourceEntryName(target)}")
    }

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
        applyRows(composition.rows)
        binding.taskbarRecents.isVisible = composition.showRecents && !editing
        binding.taskbarUpperRecents.isVisible = composition.showRecents && !editing && composition.rows > 1
        updateActionsFocusUp()
        binding.taskbarPinned.isVisible = composition.showPinned
        // S1431 ADR-5: the mode subordinates the tray rather than competing with it. The stored switch is
        // never written here, so turning the mode off restores whatever the user last chose.
        binding.trayContainer.isVisible = composition.showTray && !composition.topStatusStripMode
        // S2022: the clock stays (not named in the owner's report) - only the indicator row is gated on
        // editing, one level below the container so it can hide without taking the clock with it.
        binding.trayIndicators.root.isVisible = composition.showTray && !composition.topStatusStripMode && !editing
    }

    /**
     * S3131: turns the row count into the three things that actually differ between a one-row bar and a
     * taller one - the bar's own height, the number of tracks each icon strip fills, and whether the tray
     * runs across or down.
     *
     * The height is assigned here rather than in the layout because both `launcher_taskbar.xml` and the
     * `<include>` in `activity_launcher_home.xml` fix it statically, and the include's value is the one
     * that survives inflation; a layout-only change would leave the setting looking dead (strategic §7).
     */
    private fun applyRows(rows: Int) {
        if (rows == appliedRows) {
            return
        }
        appliedRows = rows
        Timber.d("S3131: taskbar applying $rows row(s), height ${rowHeight * rows}px")
        binding.root.updateLayoutParams { height = rowHeight * rows }
        binding.taskbarRecents.layoutManager = stripLayoutManager(rows)
        binding.taskbarUpperRecents.layoutManager =
            if (rows > 1) stripLayoutManager(rows - 1) else null
        binding.taskbarPinned.layoutManager = stripLayoutManager(rows)
        // The tray is the one block whose children are laid out by the container itself, so stacking is
        // an orientation flip rather than a track count (strategic §2.4).
        binding.trayContainer.orientation =
            if (rows > 1) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        reportRecentsCapacity(binding.taskbarRecents.width)
    }

    /** A horizontal grid of [rows] tracks - at one row this is the single strip the bar always had. */
    private fun stripLayoutManager(rows: Int) =
        GridLayoutManager(binding.root.context, rows, GridLayoutManager.HORIZONTAL, false)

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
        // S3131: every row holds a full strip's worth of icons, so the taller bar must ask the journal for
        // that many more entries - otherwise the new rows stay blank (strategic §5.1).
        // S3208: add (appliedRows - 1) * 2 extra slots for the space above Start & All Apps buttons.
        val extraSlots = if (appliedRows > 1 && composition.showRecents) (appliedRows - 1) * 2 else 0
        val capacity = (width / recentsItemWidth * appliedRows) + extraSlots
        if (capacity == reportedRecentsCapacity) {
            return
        }
        reportedRecentsCapacity = capacity
        callbacks.onRecentsCapacity(capacity)
    }
}

/**
 * S2741: the taskbar's eight callbacks travel as one object, mirroring [LauncherDesktopActions].
 *
 * The defaults stay on the fields so no construction site is forced to name a callback it does not use.
 */
class LauncherTaskbarCallbacks(
    val onCommand: (LauncherCellCommand) -> Unit,
    val onStartClick: () -> Unit,
    val onAllAppsClick: () -> Unit = {},
    val onPinRecent: (LauncherCellCommand) -> Unit = {},
    val onRemoveRecent: (LauncherCellCommand) -> Unit = {},
    val onAddPin: () -> Unit = {},
    val onRemovePin: (position: Int) -> Unit = {},
    val onRecentsCapacity: (Int) -> Unit = {},
)

/** Which taskbar blocks the user kept (settings-driven, strategic §3.3). */
data class LauncherTaskbarComposition(
    val showRecents: Boolean,
    val showPinned: Boolean,
    val showTray: Boolean,
    /** S1431: while the top strip carries the indicators, the tray is hidden whatever [showTray] says. */
    val topStatusStripMode: Boolean = false,
    /**
     * S3131: how many rows tall the bar is drawn, already coerced into the settings range by the store.
     * One row is the pre-S3131 bar, so a default composition renders exactly as it did before.
     */
    val rows: Int = 1,
)

private const val OPAQUE_ALPHA = 255
