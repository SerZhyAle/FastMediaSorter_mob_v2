package com.sza.fastmediasorter.ui.launcher.grid

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemLauncherCellGadgetBinding
import com.sza.fastmediasorter.databinding.ItemLauncherCellShortcutBinding
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import timber.log.Timber

/**
 * S0404: fills [LauncherDesktopLayout] with cell views. Shortcuts draw an icon + label here, gadgets
 * get an empty framed container that [gadgetBinder] fills with the gadget's own view (Phase 06).
 * Every visual arrives pre-resolved on [LauncherCellUi] - this binder never touches PackageManager or
 * the repositories.
 *
 * Rebuilding every view on each emission is deliberate: the desktop is dozens of cells and only
 * changes when the user edits it, so the DiffUtil/ListAdapter machinery this replaced existed to serve
 * RecyclerView, not the user. Do not "restore" diffing here - it would buy nothing and reintroduce the
 * stale-bind problem that killed the RecyclerView renderer (ADR-9).
 */
class LauncherCellViewBinder(
    private val onCellClick: (LauncherCellUi) -> Unit,
    private val onEmptySlotClick: (row: Int, col: Int) -> Unit = { _, _ -> },
    private val onRemoveClick: (LauncherCellUi) -> Unit = {},
    private val onCellDragStart: (android.view.View, LauncherCellUi) -> Unit = { _, _ -> },
    // S0427: a resting shortcut cell's long press. Returns whether it was consumed, so a cell with
    // nothing to expand keeps behaving like an ordinary un-long-pressable cell.
    private val onCellLongPress: (View, LauncherCellUi) -> Boolean = { _, _ -> false },
    private val onAttachResizeHandle: (handle: android.view.View, cellUi: LauncherCellUi) -> Unit = { _, _ -> },
) {

    /** Set by the host to inject a gadget's view into its cell; null renders an empty frame. */
    var gadgetBinder: ((LauncherCellUi, FrameLayout) -> Unit)? = null

    private var lastBound: Triple<List<LauncherCellUi>, Int, Boolean>? = null

    /**
     * Rebuilds the desktop. Cheap to call repeatedly by design: the rendered tree is a pure function of
     * (cells, columns), so an unchanged pair is skipped.
     *
     * That guard is load-bearing, not an optimisation. The host has two collectors that both land here
     * on ON_START (cells and the density factor, both StateFlows, both replaying), plus rotation - so
     * every single return to Home would otherwise tear down and rebuild every cell two or three times,
     * destroying each gadget's view (and its just-started work) microseconds after creating it. Keeping
     * views alive across a Home visit is also what lets a gadget's own caches survive.
     */
    fun bind(
        container: LauncherDesktopLayout,
        cells: List<LauncherCellUi>,
        columns: Int,
        editMode: Boolean = false,
    ) {
        if (lastBound == Triple(cells, columns, editMode)) return
        lastBound = Triple(cells, columns, editMode)
        Timber.d("S1173: desktop render cells=%d editMode=%b", cells.size, editMode)
        container.removeAllViews()
        container.columns = columns
        container.rows = rowsToShow(cells, editMode)
        val inflater = LayoutInflater.from(container.context)
        cells.forEach { item ->
            val view = when (item.cell.kind) {
                LauncherCellKind.SHORTCUT -> bindShortcut(inflater, container, item)
                LauncherCellKind.GADGET -> bindGadget(inflater, container, item)
            }
            applyCellSurface(view, item, editMode)
            if (editMode) decorateForEdit(inflater, view, item)
            container.addView(
                view,
                LauncherDesktopLayout.CellLayoutParams(
                    row = item.cell.rowIndex,
                    col = item.cell.colIndex,
                    spanW = item.cell.spanW,
                    spanH = item.cell.spanH,
                ),
            )
        }
        if (editMode) addEmptySlots(inflater, container, cells, columns)
    }

    /**
     * S1100/S1173: the card look is an editing affordance, not a permanent decoration. At rest the
     * desktop reads cleaner without it (owner, 2026-07-18), and since S1173 a shortcut is fully
     * transparent so the wallpaper shows through it (owner, 2026-07-29) - contrast lives in the icon's
     * and caption's own contour instead. Edit mode brings the card back for both kinds, because the
     * surface, stroke and elevation are what make cell boundaries and drop targets visible while
     * arranging.
     *
     * One switch owns the whole resting-versus-editing appearance on purpose: when the stroke lived here
     * and the background in the layout, the two could disagree about which mode the cell was in.
     *
     * A gadget keeps its surface in both modes - it renders its own content, which needs something
     * behind it (strategic §2 non-goals).
     */
    private fun applyCellSurface(view: View, item: LauncherCellUi, editMode: Boolean) {
        val card = view as? MaterialCardView ?: return
        if (!editMode) {
            card.strokeWidth = 0
        } else if (item.cell.kind == LauncherCellKind.SHORTCUT) {
            // A gadget's own layout already carries the surface, stroke and lift in both modes, and the
            // inflated 1dp stroke is intact here because a mode change re-inflates every cell. So only a
            // shortcut - transparent at rest since S1173 - needs the card look put back for arranging.
            card.setCardBackgroundColor(
                MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurface),
            )
            card.cardElevation = card.resources.getDimension(R.dimen.launcher_cell_edit_elevation)
        }
    }

    /**
     * Edit mode shows two spare rows past the last occupied one, so the desktop can always grow
     * downward (strategic §3.3 - one screen plus scroll, no pages). At rest it is exactly as tall as
     * its content: empty squares are an editing affordance, not part of the desktop.
     */
    private fun rowsToShow(cells: List<LauncherCellUi>, editMode: Boolean): Int {
        val occupied = LauncherGridGeometry.rowsFor(cells.map { it.cell })
        return if (editMode) occupied + SPARE_EDIT_ROWS else occupied
    }

    /**
     * Empty squares are derived from geometry, never stored or emitted: a free square is simply a
     * coordinate no cell covers. That is the whole point of the 2D container (ADR-9) - the alternative,
     * synthetic "empty slot" items in the data stream, would put layout state in the ViewModel.
     */
    private fun addEmptySlots(
        inflater: LayoutInflater,
        container: LauncherDesktopLayout,
        cells: List<LauncherCellUi>,
        columns: Int,
    ) {
        val taken = HashSet<Long>(cells.size * MAX_FOOTPRINT_SQUARES)
        cells.forEach { item ->
            // Same footprint the renderer lays out, from the same helper - a private copy of these
            // clamps here would mark a different square than the one on screen (see [footprint]).
            val footprint = LauncherGridGeometry.footprintOf(item.cell, columns)
            footprint.rows.forEach { r ->
                footprint.cols.forEach { c -> taken.add(key(r, c)) }
            }
        }
        for (row in 0 until container.rows) {
            for (col in 0 until columns) {
                if (taken.contains(key(row, col))) continue
                val slot = inflater.inflate(R.layout.item_launcher_cell_empty, container, false)
                slot.contentDescription = container.context.getString(R.string.launcher_edit_empty_slot)
                slot.setOnClickListener { onEmptySlotClick(row, col) }
                container.addView(slot, LauncherDesktopLayout.CellLayoutParams(row, col, 1, 1))
            }
        }
    }

    private fun key(row: Int, col: Int): Long = row.toLong() shl KEY_SHIFT or col.toLong()

    /**
     * The remove badge is a child of the cell view, not of the grid: it must sit on top of whatever the
     * cell draws, including a gadget that owns its own touches.
     */
    private fun decorateForEdit(
        inflater: LayoutInflater,
        view: android.view.View,
        item: LauncherCellUi,
    ) {
        // Every cell root is a MaterialCardView, which extends FrameLayout - the badge needs a frame to
        // sit on top of the cell's content instead of beside it.
        if (view !is FrameLayout) return
        // S1096: while editing, a single transparent scrim over the cell's content intercepts every
        // touch so the cell's own action never fires. The shortcut root is guarded at the ViewModel too,
        // but an interactive gadget (ADR-5) owns the taps inside its own view - a clock tap would launch
        // the alarm mid-edit and swallow the long-press needed to drag it. The scrim is the one place
        // that neutralises both, and it carries the drag gesture so a gadget cannot steal it first.
        val editScrim = android.view.View(view.context).apply {
            isClickable = true
            isLongClickable = true
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnClickListener { /* swallow: cells do not launch while editing */ }
            setOnLongClickListener {
                onCellDragStart(view, item)
                true
            }
        }
        view.addView(
            editScrim,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        // Added after the scrim so it draws and handles touches on top of it.
        val badge = inflater.inflate(R.layout.item_launcher_cell_remove_badge, view, false)
        badge.contentDescription = removeDescriptionFor(view)
        badge.setOnClickListener { onRemoveClick(item) }
        view.addView(badge)

        // S1093: only gadgets resize; a shortcut stays 1x1 and gets no handle. The handle is the last
        // child so its drag is taken before the scrim's move gesture underneath it.
        if (item.cell.kind == LauncherCellKind.GADGET) {
            val handle = inflater.inflate(R.layout.item_launcher_cell_resize_handle, view, false)
            handle.contentDescription = view.context.getString(R.string.launcher_edit_resize_handle)
            view.addView(handle)
            onAttachResizeHandle(handle, item)
        }
    }

    /**
     * Names the cell the badge would remove, reusing whatever that cell already announces. Without the
     * name every badge on the desktop reads out identically, which tells a screen-reader user what the
     * button does but not which of a dozen cells it does it to.
     */
    private fun removeDescriptionFor(view: android.view.View): CharSequence {
        val label = view.contentDescription
        return if (label.isNullOrBlank()) {
            view.context.getString(R.string.launcher_edit_remove_cell)
        } else {
            view.context.getString(R.string.launcher_edit_remove_cell_named, label)
        }
    }

    private fun bindShortcut(
        inflater: LayoutInflater,
        container: LauncherDesktopLayout,
        item: LauncherCellUi,
    ): android.view.View {
        val binding = ItemLauncherCellShortcutBinding.inflate(inflater, container, false)
        val visual = item.visual
        // S1173: the dim goes on the icon and the caption, never on the root. With no card behind them
        // the root's alpha would fade their contour too, and an unavailable cell would stop being
        // readable over a busy wallpaper - which is the one case where the user most needs to read it.
        val contentAlpha = if (visual == null) UNAVAILABLE_ALPHA else FULL_ALPHA
        binding.cellIcon.alpha = contentAlpha
        binding.cellLabel.alpha = contentAlpha
        if (visual == null) {
            // Only a SHORTCUT can be "unavailable" this way: LauncherCellUi.visual is null for every
            // GADGET by contract, so this check must never gate the gadget path.
            binding.cellLabel.setText(R.string.launcher_home_cell_unavailable)
            binding.cellIcon.setImageResource(R.drawable.ic_launcher_mode)
        } else {
            binding.cellLabel.text = visual.label
            if (visual.iconDrawable != null) {
                binding.cellIcon.setImageDrawable(visual.iconDrawable)
            } else {
                binding.cellIcon.setImageResource(visual.iconRes ?: R.drawable.ic_launcher_mode)
            }
        }
        bindMonogram(binding, visual?.monogramSeed)
        bindModeBadge(binding, item.modeBadge)
        binding.root.contentDescription = describe(binding, item)
        binding.root.setOnClickListener { onCellClick(item) }
        binding.root.setOnLongClickListener { onCellLongPress(binding.root, item) }
        return binding.root
    }

    private fun bindGadget(
        inflater: LayoutInflater,
        container: LauncherDesktopLayout,
        item: LauncherCellUi,
    ): android.view.View {
        val binding = ItemLauncherCellGadgetBinding.inflate(inflater, container, false)
        gadgetBinder?.invoke(item, binding.gadgetContainer)
        return binding.root
    }

    /**
     * S1176: a person is drawn as their initials over a colour, because a shared glyph would make every
     * contact cell look identical and the name under it is one line of small text.
     *
     * The colour is picked from the theme's own container roles rather than a hand-written palette, so
     * it stays legible in light and dark alike; the seed decides which role, so the same person keeps
     * the same colour across restarts, devices and a later rename.
     *
     * Colour is never the sole difference between two contacts - the name is always under the disc, and
     * the initials themselves are on it (strategic §3.2, accessibility).
     */
    private fun bindMonogram(binding: ItemLauncherCellShortcutBinding, seed: String?) {
        binding.cellMonogram.isVisible = seed != null
        // The icon and the disc share the same 44dp box: showing both would stack a glyph on initials.
        binding.cellIcon.isVisible = seed == null
        if (seed == null) return
        val view = binding.cellMonogram
        // Rem-mapped rather than absolute: hashCode() can be Int.MIN_VALUE, whose absolute value is
        // still negative, and that would index off the front of the list.
        val role = MONOGRAM_ROLES[((seed.hashCode() % MONOGRAM_ROLES.size) + MONOGRAM_ROLES.size) % MONOGRAM_ROLES.size]
        view.backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(view, role.first))
        view.setTextColor(MaterialColors.getColor(view, role.second))
        view.text = initialsOf(binding.cellLabel.text)
    }

    /**
     * First letters of the first two words. A number-only contact has digits for a label and gets its
     * leading digits, which is still a stable, distinguishing mark - better than a shared placeholder.
     */
    private fun initialsOf(label: CharSequence): String = label.toString().trim()
        .split(WHITESPACE)
        .filter { it.isNotEmpty() }
        .take(MONOGRAM_LETTERS)
        .joinToString("") { it.first().uppercaseChar().toString() }

    /**
     * The badge is the only thing telling two cells for the same resource apart, so it has to be
     * spoken - a purely visual badge would make them identical to a screen reader.
     */
    private fun describe(binding: ItemLauncherCellShortcutBinding, item: LauncherCellUi): CharSequence {
        // A contact cell says what tapping it does, not just whose name is on it (strategic §11.5).
        val label = item.visual?.spokenLabel ?: binding.cellLabel.text
        val modeRes = item.modeBadge?.let { modeLabelRes(it) } ?: return label
        val context = binding.root.context
        return context.getString(R.string.launcher_home_cell_with_mode, label, context.getString(modeRes))
    }

    private fun bindModeBadge(binding: ItemLauncherCellShortcutBinding, mode: LauncherResourceMode?) {
        binding.cellModeBadge.isVisible = mode != null
        if (mode == null) return
        binding.cellModeBadge.setImageResource(
            when (mode) {
                LauncherResourceMode.BROWSE -> R.drawable.ic_open_in_browse
                LauncherResourceMode.SLIDESHOW -> R.drawable.ic_slideshow
                LauncherResourceMode.PLAY -> R.drawable.ic_play
            }
        )
    }

    @StringRes
    private fun modeLabelRes(mode: LauncherResourceMode): Int = when (mode) {
        LauncherResourceMode.BROWSE -> R.string.launcher_home_mode_browse
        LauncherResourceMode.SLIDESHOW -> R.string.launcher_home_mode_slideshow
        LauncherResourceMode.PLAY -> R.string.launcher_home_mode_play
    }

    companion object {
        private const val FULL_ALPHA = 1.0f

        /** Public because the gadget path dims an unknown-key cell the same way; one value, one meaning. */
        const val UNAVAILABLE_ALPHA = 0.45f

        /** Room to grow while editing - the desktop scrolls, so this is not a ceiling. */
        private const val SPARE_EDIT_ROWS = 2

        /** A 2x2 gadget is the largest footprint, so it covers at most this many squares - a capacity hint. */
        private const val MAX_FOOTPRINT_SQUARES = 4

        /** Packs (row, col) into one Long key; a desktop never approaches 2^20 columns. */
        private const val KEY_SHIFT = 20

        /** S1176: how many leading words of a name become initials. */
        private const val MONOGRAM_LETTERS = 2

        private val WHITESPACE = Regex("\\s+")

        /**
         * Container / on-container pairs from the theme itself, so a monogram is readable in light and
         * dark without anyone maintaining a palette. Four is enough to tell neighbouring cells apart;
         * more would start pairing colours the theme never intended to sit side by side.
         */
        private val MONOGRAM_ROLES = listOf(
            com.google.android.material.R.attr.colorPrimaryContainer to
                com.google.android.material.R.attr.colorOnPrimaryContainer,
            com.google.android.material.R.attr.colorSecondaryContainer to
                com.google.android.material.R.attr.colorOnSecondaryContainer,
            com.google.android.material.R.attr.colorTertiaryContainer to
                com.google.android.material.R.attr.colorOnTertiaryContainer,
            com.google.android.material.R.attr.colorSurfaceVariant to
                com.google.android.material.R.attr.colorOnSurfaceVariant,
        )
    }
}
