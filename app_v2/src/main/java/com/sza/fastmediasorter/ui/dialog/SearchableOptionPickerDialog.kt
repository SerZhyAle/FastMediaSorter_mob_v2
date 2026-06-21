package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogSearchableOptionPickerBinding
import com.sza.fastmediasorter.databinding.ItemSearchableOptionBinding
import com.sza.fastmediasorter.ui.player.helpers.LanguageFlagFormatter
import com.sza.fastmediasorter.ui.player.helpers.LanguageItem
import java.util.Locale

/**
 * Generic single-choice picker with a type-to-filter search field over a (possibly long) option list
 * (S0580 ADR-1). Each [Option] may carry a [LanguageItem] flag rendered via [LanguageFlagFormatter];
 * options without one show plain text. The option list and result callback are retained instance
 * fields rather than Bundle arguments because [Option] holds a non-Parcelable [LanguageItem] - the same
 * transient pattern the language picker uses for its callback; only title/selection survive in args.
 */
class SearchableOptionPickerDialog : DialogFragment() {

    data class Option(val id: String, val label: String, val flag: LanguageItem? = null)

    private var _binding: DialogSearchableOptionPickerBinding? = null
    private val binding get() = _binding!!

    private var title: String = ""
    private var selectedId: String? = null
    private var options: List<Option> = emptyList()
    private var onPicked: ((Option?) -> Unit)? = null
    private lateinit var optionAdapter: OptionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        title = args.getString(ARG_TITLE).orEmpty()
        selectedId = args.getString(ARG_SELECTED_ID)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSearchableOptionPickerBinding.inflate(layoutInflater)
        setupViews()
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(binding.root)
            .create()
    }

    private fun setupViews() {
        // Leading "All / reset" row maps to a null pick (clears the filter for this facet).
        val resetRow = Option(id = RESET_ID, label = getString(R.string.streams_filter_all))
        optionAdapter = OptionAdapter(selectedId) { option ->
            onPicked?.invoke(if (option.id == RESET_ID) null else option)
            dismiss()
        }
        binding.recyclerOptions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = optionAdapter
            isFocusable = true
        }
        optionAdapter.submit(listOf(resetRow) + options)

        binding.editOptionSearch.doOnTextChanged { text, _, _, _ ->
            val visibleCount = optionAdapter.filter(text?.toString().orEmpty())
            binding.tvOptionsEmpty.isVisible = visibleCount == 0
        }
        binding.editOptionSearch.post {
            binding.editOptionSearch.requestFocus()
            requireContext().getSystemService<InputMethodManager>()
                ?.showSoftInput(binding.editOptionSearch, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onStart() {
        super.onStart()
        // Pure picker: a row tap is the action, so confirm is a no-op; this only adds Esc-dismiss + focus
        // traversal. Initial focus stays on the search field.
        DialogKeyboardDelegate.applyToDialogFragment(dialog, onConfirm = {})
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SearchableOptionPickerDialog"

        private const val RESET_ID = "__reset__"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_SELECTED_ID = "arg_selected_id"

        fun newInstance(
            title: String,
            options: List<Option>,
            selectedId: String?,
            onPicked: (Option?) -> Unit,
        ): SearchableOptionPickerDialog = SearchableOptionPickerDialog().apply {
            arguments = bundleOf(
                ARG_TITLE to title,
                ARG_SELECTED_ID to selectedId,
            )
            this.options = options
            this.onPicked = onPicked
        }
    }

    private class OptionAdapter(
        private val selectedId: String?,
        private val onClick: (Option) -> Unit,
    ) : RecyclerView.Adapter<OptionAdapter.OptionViewHolder>() {

        private var allItems: List<Option> = emptyList()
        private var visibleItems: List<Option> = emptyList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionViewHolder {
            val binding = ItemSearchableOptionBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
            return OptionViewHolder(binding, onClick)
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
            visibleItems = if (normalized.isEmpty()) allItems else allItems.filter { it.matches(normalized) }
            notifyDataSetChanged()
            return visibleItems.size
        }

        private fun Option.matches(query: String): Boolean {
            val values = listOf(label, id).map { it.lowercase(Locale.getDefault()) }
            return values.any { it.startsWith(query) } || values.any { it.contains(query) }
        }

        private class OptionViewHolder(
            private val binding: ItemSearchableOptionBinding,
            private val onClick: (Option) -> Unit,
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: Option, selected: Boolean) {
                val flag = item.flag
                if (flag != null) {
                    LanguageFlagFormatter.applyFlagGlyph(binding.tvOptionFlag, flag)
                    binding.tvOptionFlag.isVisible = true
                } else {
                    binding.tvOptionFlag.text = ""
                    binding.tvOptionFlag.isVisible = false
                }
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
