package com.sza.fastmediasorter.ui.launcher.helpers

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.GridLayoutManager
import com.sza.fastmediasorter.R
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
    private val callbacks: LauncherTaskbarCallbacks,
) : DefaultLifecycleObserver {

    /** One recents cell: the icon plus the padding the item layout puts on each side of it. */
    private val recentsItemWidth = with(binding.root.resources) {
        getDimensionPixelSize(R.dimen.launcher_taskbar_icon_size) +
            2 * getDimensionPixelSize(R.dimen.launcher_taskbar_item_spacing)
    }

    /** S3131: the height of one taskbar row, and the unit the whole bar's height is a multiple of. */
    private val rowHeight = binding.root.resources.getDimensionPixelSize(R.dimen.launcher_taskbar_height)

    private val itemSpacing = binding.root.resources.getDimensionPixelSize(R.dimen.launcher_taskbar_item_spacing)

    /** S3412: dimensions for Start & All Apps actions container. */
    private val allAppsWidth = binding.root.resources.getDimensionPixelSize(R.dimen.launcher_taskbar_all_apps_width)
    private val marginSmall = binding.root.resources.getDimensionPixelSize(R.dimen.margin_small)

    /** Reported capacities are deduplicated here so a layout pass that changed nothing re-queries nothing. */
    private var reportedRecentsCapacity = 0

    /**
     * S3131: the row count the views currently carry. Zero until the first render, so the seeded
     * composition still reaches [applyRows] once even though it holds the default row count.
     */
    private var appliedRows = 0

    /** S3523: whether the bar is drawn as a column for a side edge; set by [setEdge]. */
    private var vertical = false

    /** S3523: the orientation the views currently carry; null until the first render, like [appliedRows]. */
    private var appliedVertical: Boolean? = null

    /**
     * S3523: the D-pad chain the layout declares for a horizontal bar, read before any code rewrites it, so
     * leaving a side edge restores exactly what the XML said instead of a second copy of it kept here.
     */
    private val xmlFocusIds: Map<View, IntArray> = barFocusables().associateWith { view ->
        intArrayOf(view.nextFocusLeftId, view.nextFocusUpId, view.nextFocusRightId, view.nextFocusDownId)
    }

    /** S3523: the row padding the style and layout give the bar, transposed for a column. */
    private val rowPadding = with(binding.root) { BarPadding(paddingStart, paddingTop, paddingEnd, paddingBottom) }

    private val recentsLayoutListener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        reportRecentsCapacity(view)
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
        binding.taskbarPinned.adapter = pinnedAdapter
        binding.taskbarRecents.addOnLayoutChangeListener(recentsLayoutListener)

        lifecycleOwner.collectOnLifecycle(recents) { submitRecents(it) }
        lifecycleOwner.collectOnLifecycle(pinned) { pinnedAdapter.submitIcons(it) }
        lifecycleOwner.collectOnLifecycle(composition) { apply(it) }
    }

    /**
     * S3412: recents are sorted by frequency (most frequent at position 0, on the left)
     * and scroll horizontally on the grid.
     */
    private fun submitRecents(icons: List<LauncherTaskbarIcon>) {
        recentsAdapter.submitIcons(icons)
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
    /**
     * S3523: the placement manager hands the edge over before it re-constrains the root, so the constraint
     * set it clones already carries this bar's new thickness and orientation.
     */
    fun setEdge(edge: LauncherTaskbarEdge) {
        vertical = edge.isVertical
        render()
    }

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
     * runs across or down. S3523: for a side edge the same count is the number of columns, so the bar's
     * width is the multiple instead of its height.
     */
    private fun applyRows(rows: Int) {
        if (rows == appliedRows && vertical == appliedVertical) {
            return
        }
        val orientationChanged = vertical != appliedVertical
        appliedRows = rows
        appliedVertical = vertical
        val thickness = rowHeight * rows
        // Zero is MATCH_CONSTRAINT: the long side spans whatever the placement manager anchors it between.
        binding.root.updateLayoutParams {
            width = if (vertical) thickness else 0
            height = if (vertical) 0 else thickness
        }
        if (orientationChanged) {
            applyOrientation()
        }
        if (vertical) applyColumnActions(rows) else applyRowActions(rows)

        binding.taskbarRecents.layoutManager = stripLayoutManager(rows)
        binding.taskbarPinned.layoutManager = stripLayoutManager(rows)
        // The tray is the one block whose children are laid out by the container itself, so stacking is
        // an orientation flip rather than a track count (strategic §2.4). A column always stacks it.
        binding.trayContainer.orientation =
            if (vertical || rows > 1) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        reportRecentsCapacity(binding.taskbarRecents)
    }

    /** S3412: at 2-3 rows, Start & All Apps buttons stack vertically occupying the width of one button. */
    private fun applyRowActions(rows: Int) {
        if (rows == 1) {
            binding.taskbarActionsContainer.orientation = LinearLayout.HORIZONTAL
            binding.taskbarActionsContainer.updateLayoutParams<ViewGroup.LayoutParams> {
                width = ViewGroup.LayoutParams.WRAP_CONTENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            sizeButton(binding.btnStart, rowHeight, ViewGroup.LayoutParams.MATCH_PARENT)
            sizeButton(binding.btnAllApps, allAppsWidth, ViewGroup.LayoutParams.MATCH_PARENT, startGap = marginSmall)
            binding.btnStart.nextFocusRightId = R.id.btnAllApps
            binding.btnStart.nextFocusDownId = View.NO_ID
            binding.btnAllApps.nextFocusLeftId = R.id.btnStart
            binding.btnAllApps.nextFocusDownId = View.NO_ID
        } else {
            binding.taskbarActionsContainer.orientation = LinearLayout.VERTICAL
            binding.taskbarActionsContainer.updateLayoutParams<ViewGroup.LayoutParams> {
                width = rowHeight
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            sizeButton(binding.btnStart, ViewGroup.LayoutParams.MATCH_PARENT, 0, weight = 1f)
            sizeButton(binding.btnAllApps, ViewGroup.LayoutParams.MATCH_PARENT, 0, weight = 1f)
            binding.btnStart.nextFocusRightId = R.id.taskbarRecents
            binding.btnStart.nextFocusDownId = R.id.btnAllApps
            binding.btnAllApps.nextFocusLeftId = View.NO_ID
            binding.btnAllApps.nextFocusUpId = R.id.btnStart
        }
    }

    /**
     * S3523: the transpose of [applyRowActions] - one column stacks Start over All Apps, two or three put
     * them side by side in a band one row tall. Focus is left to the geometric search ([applyFocusChain]).
     */
    private fun applyColumnActions(columns: Int) {
        if (columns == 1) {
            binding.taskbarActionsContainer.orientation = LinearLayout.VERTICAL
            binding.taskbarActionsContainer.updateLayoutParams<ViewGroup.LayoutParams> {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            sizeButton(binding.btnStart, ViewGroup.LayoutParams.MATCH_PARENT, rowHeight)
            sizeButton(binding.btnAllApps, ViewGroup.LayoutParams.MATCH_PARENT, allAppsWidth, topGap = marginSmall)
        } else {
            binding.taskbarActionsContainer.orientation = LinearLayout.HORIZONTAL
            binding.taskbarActionsContainer.updateLayoutParams<ViewGroup.LayoutParams> {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = rowHeight
            }
            sizeButton(binding.btnStart, 0, ViewGroup.LayoutParams.MATCH_PARENT, weight = 1f)
            sizeButton(binding.btnAllApps, 0, ViewGroup.LayoutParams.MATCH_PARENT, weight = 1f)
        }
    }

    private fun sizeButton(
        button: View,
        width: Int,
        height: Int,
        weight: Float = 0f,
        startGap: Int = 0,
        topGap: Int = 0,
    ) {
        button.updateLayoutParams<LinearLayout.LayoutParams> {
            this.width = width
            this.height = height
            this.weight = weight
            marginStart = startGap
            topMargin = topGap
        }
    }

    /**
     * S3523: everything that flips between a row and a column other than the row count - the axis of the
     * bar and of every block in it, the padding and gravity that follow that axis, and the dividers.
     */
    private fun applyOrientation() {
        val axis = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        val crossCenter = if (vertical) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
        val dividerRes =
            if (vertical) R.drawable.launcher_taskbar_divider_horizontal else R.drawable.launcher_taskbar_divider
        val padding = if (vertical) rowPadding.transposed() else rowPadding
        with(binding.root) {
            orientation = axis
            gravity = crossCenter
            dividerDrawable = ContextCompat.getDrawable(context, dividerRes)
            updatePaddingRelative(padding.start, padding.top, padding.end, padding.bottom)
        }
        with(binding.taskbarStrips) {
            orientation = axis
            gravity = crossCenter
            dividerDrawable = ContextCompat.getDrawable(context, dividerRes)
        }
        alongBar(binding.taskbarStrips, fill = true)
        alongBar(binding.taskbarRecents, fill = true)
        alongBar(binding.taskbarPinned, fill = false)
        binding.taskbarRecents.isHorizontalFadingEdgeEnabled = !vertical
        binding.taskbarRecents.isVerticalFadingEdgeEnabled = vertical
        binding.trayContainer.gravity = crossCenter
        alongBar(binding.trayContainer, fill = false)
        if (vertical) {
            binding.trayContainer.updatePaddingRelative(start = 0, top = itemSpacing, end = 0, bottom = itemSpacing)
        } else {
            binding.trayContainer.updatePaddingRelative(start = itemSpacing, top = 0, end = itemSpacing, bottom = 0)
        }
        binding.trayIndicators.root.orientation = axis
        binding.trayIndicators.root.gravity = crossCenter
        alongBar(binding.launcherAddCell, fill = false)
        alongBar(binding.launcherEditDone, fill = false)
        recentsAdapter.setVertical(vertical)
        pinnedAdapter.setVertical(vertical)
        applyFocusChain()
    }

    /**
     * Sizes a child of a bar-axis LinearLayout: across the bar it always fills; along the bar it either
     * takes the weighted slack ([fill]) or wraps its content.
     */
    private fun alongBar(view: View, fill: Boolean) {
        view.updateLayoutParams<LinearLayout.LayoutParams> {
            val along = if (fill) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
            width = if (vertical) ViewGroup.LayoutParams.MATCH_PARENT else along
            height = if (vertical) along else ViewGroup.LayoutParams.MATCH_PARENT
            weight = if (fill) 1f else 0f
        }
    }

    /**
     * S3523: a column clears the bar's explicit D-pad chain - the XML names left/right neighbours that now
     * sit above and below - and the geometric search walks a single column and finds the desktop beside it.
     * A row gets the XML chain back; [applyRowActions] then adjusts the two buttons as it always did.
     */
    private fun applyFocusChain() {
        xmlFocusIds.forEach { (view, ids) ->
            view.nextFocusLeftId = if (vertical) View.NO_ID else ids[FOCUS_LEFT]
            view.nextFocusUpId = if (vertical) View.NO_ID else ids[FOCUS_UP]
            view.nextFocusRightId = if (vertical) View.NO_ID else ids[FOCUS_RIGHT]
            view.nextFocusDownId = if (vertical) View.NO_ID else ids[FOCUS_DOWN]
        }
    }

    private fun barFocusables(): List<View> = listOf(
        binding.btnStart,
        binding.btnAllApps,
        binding.taskbarRecents,
        binding.taskbarPinned,
        binding.launcherAddCell,
        binding.launcherEditDone,
    )

    /**
     * A grid of [rows] tracks along the bar - at one row this is the single strip the bar always had. For a
     * column the tracks are columns and the strip scrolls down.
     */
    private fun stripLayoutManager(rows: Int) = GridLayoutManager(
        binding.root.context,
        rows,
        if (vertical) GridLayoutManager.VERTICAL else GridLayoutManager.HORIZONTAL,
        false,
    )

    /**
     * S1431 ADR-4: the recents list asks for as many entries as this row can actually show, so it grows when
     * the tray leaves the bar and differs between the orientations - one fixed number could do neither.
     */
    private fun reportRecentsCapacity(strip: View) {
        // S3523: a column lays the recents out downward, so the strip's length is its height there.
        val length = if (vertical) strip.height else strip.width
        if (length <= 0 || recentsItemWidth <= 0) {
            return
        }
        val capacity = length / recentsItemWidth * appliedRows
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

/** S3523: the bar's relative padding; a column swaps its axes, so start/end become top/bottom. */
private data class BarPadding(val start: Int, val top: Int, val end: Int, val bottom: Int) {
    fun transposed() = BarPadding(start = top, top = start, end = bottom, bottom = end)
}

private const val OPAQUE_ALPHA = 255
private const val FOCUS_LEFT = 0
private const val FOCUS_UP = 1
private const val FOCUS_RIGHT = 2
private const val FOCUS_DOWN = 3
