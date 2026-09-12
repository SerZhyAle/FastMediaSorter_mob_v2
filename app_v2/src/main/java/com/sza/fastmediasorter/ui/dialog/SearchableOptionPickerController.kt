package com.sza.fastmediasorter.ui.dialog

import android.content.res.ColorStateList
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputEditText
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogSearchableOptionPickerBinding
import com.sza.fastmediasorter.databinding.ItemSearchableOptionBinding
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.LeadingVisual
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.Option
import com.sza.fastmediasorter.ui.player.helpers.LanguageFlagFormatter
import java.util.Locale

/**
 * S0947: the reusable wiring behind the canonical single-choice picker - shared by
 * [SearchableOptionPickerDialog] (the standalone dialog) and by the migrated quick-launch picker
 * fragments that reuse the same layout ([DialogSearchableOptionPickerBinding]) but deliver their
 * result via a FragmentResult. Keeping the behaviour here (conditional search-on-overflow, selected
 * highlight + autoscroll, passive IME, case-insensitive label filter) means every surface inherits it
 * identically (Rule 3/6: no picker logic duplicated in a Fragment).
 */
object SearchableOptionPickerController {

    /** Selected row is scrolled to the upper third of the viewport (height / 3) on open. */
    private const val SELECTED_SCROLL_DIVISOR = 3

    /**
     * Wire [binding] to show [options] as a single-choice list. When [resetRow] is non-null it is
     * prepended and reported as a `null` pick. [onPicked] receives the chosen option (or null for the
     * reset row); the caller is responsible for dismissing its host.
     */
    /** The three type-to-filter views, bound together because a caller wires all three or none. */
    data class SearchUi(
        val layout: View?,
        val input: TextInputEditText?,
        val emptyLabel: TextView?,
        /** S2178: boundary line under the field; shares the field's runtime visibility. */
        val divider: View? = null,
    )

    fun attach(
        binding: DialogSearchableOptionPickerBinding,
        options: List<Option>,
        selectedId: String?,
        resetRow: Option?,
        columns: Int = 1,
        onPicked: (Option?) -> Unit,
    ) {
        attachViews(
            recyclerOptions = binding.recyclerOptions,
            searchUi = SearchUi(
                binding.layoutOptionSearch,
                binding.editOptionSearch,
                binding.tvOptionsEmpty,
                binding.dividerOptionSearch,
            ),
            options = options,
            selectedId = selectedId,
            resetRow = resetRow,
            columns = columns,
            onPicked = onPicked,
        )
    }

    fun attachViews(
        recyclerOptions: RecyclerView,
        searchUi: SearchUi? = null,
        options: List<Option>,
        selectedId: String?,
        resetRow: Option?,
        columns: Int = 1,
        onPicked: (Option?) -> Unit,
    ) {
        val editOptionSearch = searchUi?.input
        val tvOptionsEmpty = searchUi?.emptyLabel
        val rows = if (resetRow != null) listOf(resetRow) + options else options
        val adapter = OptionAdapter(selectedId) { option ->
            onPicked(if (resetRow != null && option.id == resetRow.id) null else option)
        }
        recyclerOptions.apply {
            layoutManager =
                if (columns > 1) GridLayoutManager(context, columns) else LinearLayoutManager(context)
            this.adapter = adapter
            isFocusable = true
        }
        adapter.submit(rows)

        editOptionSearch?.doOnTextChanged { text, _, _, _ ->
            val visibleCount = adapter.filter(text?.toString().orEmpty())
            tvOptionsEmpty?.isVisible = visibleCount == 0
        }

        val initialQuery = editOptionSearch?.text?.toString().orEmpty()
        if (initialQuery.isNotEmpty()) {
            tvOptionsEmpty?.isVisible = adapter.filter(initialQuery) == 0
        }

        scrollToSelected(recyclerOptions, rows, selectedId)
        val searchLayout = searchUi?.layout
        if (searchLayout != null) {
            applySearchVisibilityOnFit(recyclerOptions, searchLayout, searchUi.divider)
        }
    }

    /**
     * S1413: re-flow an already-attached grid to [columns] - what a host calls when its window changed
     * width. Deliberately not a re-[attach]: rebuilding the list would discard the user's search text
     * and their scroll position. A single-column picker has no grid to re-flow and is left alone.
     */
    fun reflowColumns(binding: DialogSearchableOptionPickerBinding, columns: Int) {
        (binding.recyclerOptions.layoutManager as? GridLayoutManager)?.spanCount = columns
    }

    /** Bring the selected option into view (upper third) on open; no-op when nothing is selected. */
    private fun scrollToSelected(recycler: RecyclerView, rows: List<Option>, selectedId: String?) {
        val target = selectedId ?: return
        val index = rows.indexOfFirst { it.id == target }
        if (index < 0) return
        recycler.post {
            val offset = recycler.height / SELECTED_SCROLL_DIVISOR
            (recycler.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(index, offset)
        }
    }

    /**
     * Show the search field only when the option list does not fit its viewport. Measured once the
     * list is laid out; re-runs on recreate (rotation), so it stays orientation-correct without a
     * separate landscape layout.
     */
    private fun applySearchVisibilityOnFit(
        recycler: RecyclerView,
        searchLayout: View,
        divider: View?,
    ) {
        recycler.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (recycler.height == 0) return
                    recycler.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    val overflows = recycler.computeVerticalScrollRange() > recycler.height
                    searchLayout.isVisible = overflows
                    divider?.isVisible = overflows
                }
            },
        )
    }

    private class OptionAdapter(
        private val selectedId: String?,
        private val onClick: (Option) -> Unit,
    ) : RecyclerView.Adapter<OptionAdapter.OptionViewHolder>() {

        private var allItems: List<Option> = emptyList()
        private var visibleItems: List<Option> = emptyList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionViewHolder {
            val itemBinding = ItemSearchableOptionBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
            return OptionViewHolder(itemBinding, onClick)
        }

        override fun onBindViewHolder(holder: OptionViewHolder, position: Int) {
            val item = visibleItems[position]
            holder.bind(item, item.id == selectedId)
        }

        override fun getItemCount(): Int = visibleItems.size

        fun submit(items: List<Option>) {
            allItems = items
            visibleItems = items
            notifyDataSetChanged()
        }

        /** Filters by the query and returns the visible row count (for the empty-state toggle). */
        fun filter(query: String): Int {
            val normalized = query.trim().lowercase(Locale.getDefault())
            visibleItems =
                if (normalized.isEmpty()) allItems else allItems.filter { it.matches(normalized) }
            notifyDataSetChanged()
            return visibleItems.size
        }

        // S0947: filter on the primary visible label, case-insensitive contains (owner contract §4.1).
        private fun Option.matches(query: String): Boolean =
            label.lowercase(Locale.getDefault()).contains(query)

        private class OptionViewHolder(
            private val binding: ItemSearchableOptionBinding,
            private val onClick: (Option) -> Unit,
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: Option, selected: Boolean) {
                bindLeadingVisual(item)
                binding.tvOptionLabel.text = item.label
                binding.root.isSelected = selected
                binding.root.isActivated = selected
                binding.root.contentDescription = if (selected) {
                    "${item.label}, ${binding.root.context.getString(R.string.selected_label)}"
                } else {
                    item.label
                }
                binding.root.setOnClickListener { onClick(item) }
                binding.root.setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_UP && keyCode in confirmKeyCodes) {
                        onClick(item)
                        true
                    } else {
                        false
                    }
                }
            }

            /** Renders at most one leading visual: a general [LeadingVisual] wins over a flag glyph. */
            private fun bindLeadingVisual(item: Option) {
                val icon = binding.ivOptionIcon
                val flagView = binding.tvOptionFlag
                val leading = item.leading
                if (leading != null) {
                    flagView.isVisible = false
                    icon.isVisible = true
                    // S2062: the row is recycled, so a tint applied for a previous white glyph must be
                    // cleared here - left set, it would repaint the next row's app icon/thumbnail/brand
                    // logo one flat colour.
                    val applyTint = leading is LeadingVisual.IconRes && leading.tintIcon
                    icon.imageTintList = if (applyTint) {
                        ColorStateList.valueOf(
                            MaterialColors.getColor(icon, com.google.android.material.R.attr.colorOnSurfaceVariant)
                        )
                    } else {
                        null
                    }
                    when (leading) {
                        is LeadingVisual.IconDrawable -> {
                            // Cancel any pending async load from a recycled thumbnail row first.
                            Glide.with(icon).clear(icon)
                            icon.setImageDrawable(leading.drawable)
                        }
                        is LeadingVisual.IconRes -> {
                            Glide.with(icon).clear(icon)
                            icon.setImageResource(leading.resId)
                        }
                        is LeadingVisual.Thumbnail ->
                            Glide.with(icon).load(leading.model).into(icon)
                    }
                    return
                }
                icon.isVisible = false
                val flag = item.flag
                if (flag != null) {
                    LanguageFlagFormatter.applyFlagGlyph(flagView, flag)
                    flagView.isVisible = true
                } else {
                    flagView.text = ""
                    flagView.isVisible = false
                }
            }

            companion object {
                private val confirmKeyCodes = setOf(
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_SPACE,
                )
            }
        }
    }
}
