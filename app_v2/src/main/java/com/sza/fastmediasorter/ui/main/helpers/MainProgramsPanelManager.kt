package com.sza.fastmediasorter.ui.main.helpers

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ViewMainProgramsPanelBinding
import timber.log.Timber

/**
 * S0755: renders the main-window programs panel as a visual mirror of the three-dots programs menu.
 * Rather than re-declare the item set, it populates a throwaway [PopupMenu] through the same
 * [populateMenu] the dropdown uses, then lays each menu item out as an icon (+ label in landscape)
 * button - so the order, per-item gates and icons stay a single source of truth.
 *
 * Items that do not fit the row move under a trailing overflow button (owner decision: fixed visible
 * set + overflow, never a horizontal scroll). When [excludeStreams] is set (the streams panel S0756 is
 * visible) the "Streams" item is dropped here to avoid duplicating that entry point.
 *
 * S0770: each visible item gets a per-item menu (Open / optional Open-in-new-window / optional Remove).
 * The trailing three-dots button shows it in label mode; a long-press on the body opens it in compact
 * mode. The host supplies [newWindowActionFor]/[removeActionFor] (null = that action is absent).
 */
class MainProgramsPanelManager(
    private val panel: ViewMainProgramsPanelBinding,
    private val populateMenu: (PopupMenu, Boolean) -> Unit,
    private val onItemSelected: (Int) -> Unit,
    private val newWindowActionFor: (Int) -> (() -> Unit)?,
    private val removeActionFor: (Int) -> (() -> Unit)?,
) {

    private data class PanelItem(val id: Int, val title: CharSequence, val icon: Drawable?)

    private var visible = false
    private var excludeStreams = false
    private var models: List<PanelItem> = emptyList()
    private val overflowItems = mutableListOf<PanelItem>()

    /** Show/hide and (when shown) rebuild the panel from the current menu + dedup flag. */
    fun update(visible: Boolean, excludeStreams: Boolean) {
        this.visible = visible
        this.excludeStreams = excludeStreams
        if (!visible) {
            panel.root.visibility = View.GONE
            return
        }
        panel.root.visibility = View.VISIBLE
        rebuild()
    }

    /** Re-render after a rotation / width change so the icon-vs-icon+label rule and overflow re-apply. */
    fun refresh() {
        if (visible) rebuild()
    }

    private fun rebuild() {
        val context = panel.root.context
        val showLabels = context.resources.getBoolean(R.bool.main_programs_panel_show_labels)

        // Build the same menu the dropdown uses, then read its (order-sorted) items as the panel model.
        val scratch = PopupMenu(context, panel.root)
        populateMenu(scratch, excludeStreams)
        val menu = scratch.menu
        models = (0 until menu.size()).mapNotNull { i ->
            val item = menu.getItem(i)
            if (!item.isVisible) null else PanelItem(item.itemId, item.title ?: "", item.icon)
        }

        Timber.d("S0755: programs panel rebuilt items=${models.size} labels=$showLabels exclStreams=$excludeStreams")
        val container = panel.programsPanelItems
        container.removeAllViews()
        val inflater = LayoutInflater.from(context)
        for (model in models) {
            val itemView = inflater.inflate(R.layout.item_main_program, container, false)
            val button = itemView.findViewById<MaterialButton>(R.id.btnProgram)
            val menuButton = itemView.findViewById<View>(R.id.btnProgramMenu)
            button.icon = model.icon
            button.text = if (showLabels) model.title else ""
            button.contentDescription = model.title
            button.setOnClickListener { onItemSelected(model.id) }
            // S0770: visible three-dots in label mode; long-press on the body covers compact mode.
            menuButton.visibility = if (showLabels) View.VISIBLE else View.GONE
            menuButton.setOnClickListener { showItemMenu(model, menuButton) }
            button.setOnLongClickListener {
                showItemMenu(model, button)
                true
            }
            container.addView(itemView)
        }

        // Overflow distribution needs a measured container; defer until it has a width.
        container.post { applyOverflow() }
    }

    /** S0770: per-item menu - Open always, plus Open-in-new-window / Remove when the host supplies them. */
    private fun showItemMenu(model: PanelItem, anchor: View) {
        Timber.d("S0770: programs panel item menu id=${model.id}")
        val actions = mutableListOf<PanelItemContextMenu.Action>()
        actions += PanelItemContextMenu.Action(R.string.action_open) { onItemSelected(model.id) }
        newWindowActionFor(model.id)?.let {
            actions += PanelItemContextMenu.Action(R.string.action_open_in_separate_window, it)
        }
        removeActionFor(model.id)?.let {
            actions += PanelItemContextMenu.Action(R.string.remove_action, it)
        }
        PanelItemContextMenu.show(anchor, actions)
    }

    private fun applyOverflow() {
        val container = panel.programsPanelItems
        val overflowButton = panel.btnProgramsPanelOverflow
        val available = container.width
        if (available <= 0 || container.childCount == 0) {
            overflowButton.visibility = View.GONE
            return
        }

        overflowItems.clear()
        for (i in 0 until container.childCount) container.getChildAt(i).visibility = View.VISIBLE

        val widths = IntArray(container.childCount) { measureItemWidth(container.getChildAt(it)) }
        val total = widths.sum()
        if (total <= available) {
            overflowButton.visibility = View.GONE
            return
        }

        // Reserve the overflow button's width, then keep items while they fit; the rest go to the popup.
        val budget = available - measureItemWidth(overflowButton)
        var used = 0
        var overflowing = false
        for (i in 0 until container.childCount) {
            if (!overflowing && used + widths[i] <= budget) {
                used += widths[i]
            } else {
                overflowing = true
                container.getChildAt(i).visibility = View.GONE
                models.getOrNull(i)?.let { overflowItems.add(it) }
            }
        }
        overflowButton.visibility = View.VISIBLE
        overflowButton.setOnClickListener { showOverflowPopup() }
    }

    private fun showOverflowPopup() {
        if (overflowItems.isEmpty()) return
        val popup = PopupMenu(panel.btnProgramsPanelOverflow.context, panel.btnProgramsPanelOverflow)
        overflowItems.forEachIndexed { index, item ->
            popup.menu.add(0, item.id, index, item.title).icon = item.icon
        }
        popup.setForceShowIcon(true)
        popup.setOnMenuItemClickListener { menuItem ->
            onItemSelected(menuItem.itemId)
            true
        }
        popup.show()
    }

    private fun measureItemWidth(view: View): Int {
        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams
        val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        return view.measuredWidth + (lp?.leftMargin ?: 0) + (lp?.rightMargin ?: 0)
    }
}
