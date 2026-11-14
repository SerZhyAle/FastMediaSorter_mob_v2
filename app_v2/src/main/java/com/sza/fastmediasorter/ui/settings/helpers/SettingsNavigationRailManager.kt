package com.sza.fastmediasorter.ui.settings.helpers

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemSettingsRailDestinationBinding
import com.sza.fastmediasorter.ui.settings.SettingsDestination
import timber.log.Timber

/**
 * S4621: the settings page menu - one list, two presentations (SETTINGS-NAVIGATION 2.1 section 2).
 *
 * Wide windows show it as a rail beside the content pane. Narrow windows get the item B navigation
 * view: the list fills the window, a destination opens its page in the same window, and Back returns
 * to the list with that destination still marked current.
 */
class SettingsNavigationRailManager(
    private val rail: RecyclerView,
    private val contentPane: View,
    private val destinations: List<SettingsDestination>,
    private val onDestinationSelected: (SettingsDestination) -> Unit,
    private val onPaneChanged: () -> Unit,
) {

    enum class NarrowPane { LIST, PAGE }

    var selectedId: String = destinations.first().id
        private set

    var isWide: Boolean = true
        private set

    var narrowPane: NarrowPane = NarrowPane.LIST
        private set

    /** True when the window shows a page without the menu beside it. */
    val isShowingNarrowPage: Boolean get() = !isWide && narrowPane == NarrowPane.PAGE

    private val adapter = RailAdapter()

    fun bind() {
        rail.layoutManager = LinearLayoutManager(rail.context)
        rail.adapter = adapter
        rail.itemAnimator = null
    }

    /** Marks [id] current without asking the host to change page - the host already did. */
    fun markSelected(id: String) {
        if (selectedId == id) return
        val old = destinations.indexOfFirst { it.id == selectedId }
        selectedId = id
        if (old >= 0) adapter.notifyItemChanged(old)
        val new = destinations.indexOfFirst { it.id == id }
        if (new >= 0) adapter.notifyItemChanged(new)
    }

    fun applyPresentation(isWide: Boolean) {
        this.isWide = isWide
        val params = rail.layoutParams as LinearLayout.LayoutParams
        when {
            isWide -> {
                params.width = rail.resources.getDimensionPixelSize(R.dimen.settings_rail_width)
                rail.isVisible = true
                contentPane.isVisible = true
            }
            narrowPane == NarrowPane.LIST -> {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                rail.isVisible = true
                contentPane.isVisible = false
            }
            else -> {
                rail.isVisible = false
                contentPane.isVisible = true
            }
        }
        rail.layoutParams = params
        onPaneChanged()
    }

    /** Narrow only: opens the current destination's page in place of the list. */
    fun showPage() {
        narrowPane = NarrowPane.PAGE
        applyPresentation(isWide)
    }

    /** Back on a narrow page returns to the list; returns false when there is nothing to go back to. */
    fun onBackFromPage(): Boolean {
        if (!isShowingNarrowPage) return false
        narrowPane = NarrowPane.LIST
        applyPresentation(isWide)
        rail.post { focusSelectedRow() }
        return true
    }

    fun saveState(outState: Bundle) {
        outState.putString(KEY_NARROW_PANE, narrowPane.name)
    }

    fun restoreState(savedState: Bundle?, hasNamedDestination: Boolean) {
        val saved = savedState?.getString(KEY_NARROW_PANE)
        narrowPane = when {
            saved != null -> NarrowPane.entries.firstOrNull { it.name == saved } ?: NarrowPane.LIST
            hasNamedDestination -> NarrowPane.PAGE
            else -> NarrowPane.LIST
        }
    }

    fun focusSelectedRow() {
        val index = destinations.indexOfFirst { it.id == selectedId }
        rail.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus()
    }

    private fun onRowClicked(destination: SettingsDestination) {
        Timber.d("S4621: rail row selected ${destination.id} wide=$isWide")
        markSelected(destination.id)
        onDestinationSelected(destination)
        if (!isWide) showPage()
    }

    private inner class RailAdapter : RecyclerView.Adapter<RailViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RailViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return RailViewHolder(ItemSettingsRailDestinationBinding.inflate(inflater, parent, false))
        }

        override fun onBindViewHolder(holder: RailViewHolder, position: Int) = holder.bind(destinations[position])

        override fun getItemCount(): Int = destinations.size
    }

    private inner class RailViewHolder(
        private val row: ItemSettingsRailDestinationBinding,
    ) : RecyclerView.ViewHolder(row.root) {

        fun bind(destination: SettingsDestination) {
            val selected = destination.id == selectedId
            val context = row.root.context
            row.railGlyph.setImageResource(destination.iconRes)
            row.railCaption.setText(destination.titleRes)
            row.railCaption.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            row.railSelectedBar.visibility = if (selected) View.VISIBLE else View.INVISIBLE
            row.root.isSelected = selected
            ViewCompat.setStateDescription(
                row.root,
                if (selected) context.getString(R.string.settings_rail_current_page) else null,
            )
            row.root.setOnClickListener { onRowClicked(destination) }
        }
    }

    private companion object {
        const val KEY_NARROW_PANE = "settings_nav_narrow_pane"
    }
}
