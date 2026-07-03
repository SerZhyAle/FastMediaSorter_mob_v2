package com.sza.fastmediasorter.ui.main.helpers

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ViewMainProgramsPanelBinding
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
 *
 * S0807: a leading three-dots button opens the panel-level menu (Configure panel / Collapse panel /
 * Hide panel). Collapse swaps the item row for a labelled strip (mirror of the S0781 resource strip),
 * persisting the state; Hide routes to [onHidePanel] which drops the panel and restores the top-bar button.
 */
@Suppress("LongParameterList") // View-manager: its views + per-item callbacks + the shared-row collapse chip (S0809).
class MainProgramsPanelManager(
    private val panel: ViewMainProgramsPanelBinding,
    private val populateMenu: (PopupMenu, Boolean) -> Unit,
    private val onItemSelected: (Int) -> Unit,
    private val newWindowActionFor: (Int) -> (() -> Unit)?,
    private val removeActionFor: (Int) -> (() -> Unit)?,
    // S0780: open Settings on the program/scenario behaviour group (shared by every panel item + header).
    private val onConfigure: () -> Unit,
    // S0807: hide the whole panel (write the visibility flag); the three-dots returns to the top command bar.
    private val onHidePanel: () -> Unit,
    // S0807: persist the collapsed-strip state, mirroring the S0781 resource-tabs collapse manager.
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    // S0809: the collapsed representation is now a chip in the shared collapsed-panels row (activity layout),
    // not an in-panel strip; the manager toggles it exactly as before.
    private val collapsedChip: View,
) {

    private data class PanelItem(val id: Int, val title: CharSequence, val icon: Drawable?)

    private var available = false
    private var excludeStreams = false
    private var collapsed = false
    private var models: List<PanelItem> = emptyList()
    private val overflowItems = mutableListOf<PanelItem>()

    // S0830: the overflow list is a real anchored popup of item rows; keep a handle to dismiss it on rebuild.
    private var overflowPopup: PopupWindow? = null

    /**
     * S0807: wire the leading header menu + collapsed-strip tap and load the persisted collapsed state.
     * Call once during initial setup (like the streams panel's setup()).
     */
    fun install() {
        panel.btnProgramsPanelMenu.setOnClickListener { showPanelMenu(panel.btnProgramsPanelMenu) }
        collapsedChip.setOnClickListener { setCollapsed(false) }
        scope.launch {
            collapsed = settingsRepository.getSettings().first().programsPanelCollapsed
            applyVisibility()
        }
    }

    /** Show/hide and (when shown) rebuild the panel from the current menu + dedup flag. */
    fun update(visible: Boolean, excludeStreams: Boolean) {
        this.available = visible
        this.excludeStreams = excludeStreams
        applyVisibility()
    }

    /** Re-render after a rotation / width change so the icon-vs-icon+label rule and overflow re-apply. */
    fun refresh() {
        if (available && !collapsed) rebuild()
    }

    /** S0807: root shows when enabled; inside, the item row and the collapsed strip are mutually exclusive. */
    private fun applyVisibility() {
        panel.root.isVisible = available
        val (contentVisible, stripVisible) = resolveVisibility(available, collapsed)
        panel.programsPanelContent.isVisible = contentVisible
        collapsedChip.isVisible = stripVisible
        if (contentVisible) rebuild()
    }

    /** S0807: header menu "Collapse panel" (true) and strip tap (false); persists + re-applies visibility. */
    private fun setCollapsed(value: Boolean) {
        if (collapsed == value) return
        // A GONE view loses focus, so hand it over before swapping the row for the strip (or back).
        val contentHadFocus = panel.programsPanelContent.hasFocus()
        val stripHadFocus = collapsedChip.hasFocus()
        collapsed = value
        applyVisibility()
        if (value && contentHadFocus) {
            collapsedChip.requestFocus()
        } else if (!value && stripHadFocus) {
            panel.btnProgramsPanelMenu.requestFocus()
        }
        scope.launch {
            val current = settingsRepository.getSettings().first()
            settingsRepository.updateSettings(current.copy(programsPanelCollapsed = value))
        }
    }

    /** S0807: panel-level menu - Configure panel (deep-link) / Collapse panel (strip) / Hide panel. */
    private fun showPanelMenu(anchor: View) {
        Timber.d("S0807: programs panel header menu opened")
        val actions = listOf(
            PanelItemContextMenu.Action(R.string.programs_panel_configure_action) { onConfigure() },
            PanelItemContextMenu.Action(R.string.programs_panel_collapse_action) { setCollapsed(true) },
            PanelItemContextMenu.Action(R.string.programs_panel_hide_action) { onHidePanel() },
        )
        PanelItemContextMenu.show(anchor, actions)
    }

    private fun rebuild() {
        // A rotation / width change rebuilds the row; drop any open overflow popup anchored to the old view.
        overflowPopup?.dismiss()
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
        // S0914: the panel body now sits on its accent colour, so item icon + text switch to the shared
        // light foreground here - not in item_main_program.xml, which the overflow popup reuses on the
        // neutral theme surface where white would be illegible.
        val accentForeground = ContextCompat.getColor(context, R.color.main_panel_accent_foreground)
        Timber.d("S0914: programs panel items tinted for accent background count=${models.size}")
        for (model in models) {
            val itemView = inflater.inflate(R.layout.item_main_program, container, false)
            val button = itemView.findViewById<MaterialButton>(R.id.btnProgram)
            val menuButton = itemView.findViewById<MaterialButton>(R.id.btnProgramMenu)
            button.icon = model.icon
            button.text = if (showLabels) model.title else ""
            button.contentDescription = model.title
            button.setTextColor(accentForeground)
            button.iconTint = ColorStateList.valueOf(accentForeground)
            menuButton.iconTint = ColorStateList.valueOf(accentForeground)
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
        // S0780: jump to the settings group holding program/scenario behaviour options.
        actions += PanelItemContextMenu.Action(R.string.action_configure) {
            Timber.d("S0780: programs panel configure tapped")
            onConfigure()
        }
        removeActionFor(model.id)?.let {
            actions += PanelItemContextMenu.Action(R.string.remove_action, it)
        }
        // S0810: header shows the item's name (icon-only in the panel is otherwise ambiguous).
        PanelItemContextMenu.show(anchor, actions, title = model.title)
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

    /**
     * S0830: the overflow surface is a real anchored [PopupWindow] whose rows reuse the visible-item
     * layout ([R.layout.item_main_program]) and wiring, so an overflow item behaves exactly like a
     * visible one - a short tap launches it, a long-press or the three-dots opens the per-item menu.
     * A flat [PopupMenu] cannot offer two gestures per row (single click listener, no per-row view),
     * which is why the previous single-menu approach broke the launch affordance.
     */
    private fun showOverflowPopup() {
        if (overflowItems.isEmpty()) return
        overflowPopup?.dismiss()
        val anchor = panel.btnProgramsPanelOverflow
        val context = anchor.context
        Timber.d("S0830: overflow popup items=${overflowItems.size} tap=launch long/dots=menu")

        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val popup = PopupWindow(list, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        overflowPopup = popup

        val inflater = LayoutInflater.from(context)
        val rows = overflowItems.map { model ->
            val itemView = inflater.inflate(R.layout.item_main_program, list, false)
            val button = itemView.findViewById<MaterialButton>(R.id.btnProgram)
            val menuButton = itemView.findViewById<MaterialButton>(R.id.btnProgramMenu)
            button.icon = model.icon
            button.text = model.title
            button.contentDescription = model.title
            button.setOnClickListener {
                popup.dismiss()
                onItemSelected(model.id)
            }
            button.setOnLongClickListener {
                popup.dismiss()
                showItemMenu(model, anchor)
                true
            }
            menuButton.isVisible = true
            menuButton.setOnClickListener {
                popup.dismiss()
                showItemMenu(model, anchor)
            }
            itemView
        }

        // A vertical list needs full-width rows so the trailing three-dots aligns; let the body expand.
        val width = rows.maxOf { measureItemWidth(it) }
        rows.forEach { row ->
            val body = row.findViewById<MaterialButton>(R.id.btnProgram)
            body.layoutParams = (body.layoutParams as LinearLayout.LayoutParams).apply {
                this.width = 0
                weight = 1f
            }
            val rowParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            list.addView(row, rowParams)
        }

        popup.width = width
        popup.setBackgroundDrawable(resolvePopupBackground(context))
        popup.elevation = context.resources.getDimension(R.dimen.card_elevation)
        popup.showAsDropDown(anchor)
    }

    /** Theme popup-menu background so the overflow window reads as a menu and outside taps dismiss it. */
    private fun resolvePopupBackground(context: Context): Drawable {
        val bgAttr = com.google.android.material.R.attr.popupMenuBackground
        val attrs = context.obtainStyledAttributes(intArrayOf(bgAttr))
        val drawable = attrs.getDrawable(0)
        attrs.recycle()
        if (drawable != null) return drawable
        val surface = TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, surface, true)
        return ColorDrawable(surface.data)
    }

    private fun measureItemWidth(view: View): Int {
        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams
        val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        return view.measuredWidth + (lp?.leftMargin ?: 0) + (lp?.rightMargin ?: 0)
    }

    companion object {
        /**
         * S0807: (contentVisible, stripVisible) for the given availability + collapsed state. The panel
         * root owns availability; when hidden both are false. The item row and strip are never both shown.
         */
        internal fun resolveVisibility(available: Boolean, collapsed: Boolean): Pair<Boolean, Boolean> =
            (available && !collapsed) to (available && collapsed)
    }
}
