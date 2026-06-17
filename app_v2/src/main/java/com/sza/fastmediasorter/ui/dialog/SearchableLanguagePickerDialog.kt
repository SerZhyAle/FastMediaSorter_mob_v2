package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.content.Context
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
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.databinding.DialogSearchableLanguagePickerBinding
import com.sza.fastmediasorter.databinding.ItemSearchableLanguageBinding
import com.sza.fastmediasorter.ui.player.helpers.LanguageCapability
import com.sza.fastmediasorter.ui.player.helpers.LanguageFlagFormatter
import com.sza.fastmediasorter.ui.player.helpers.LanguageItem
import com.sza.fastmediasorter.ui.player.helpers.TranslationLanguageCatalog
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class SearchableLanguagePickerDialog : DialogFragment() {

    @Inject
    lateinit var capabilityAvailability: CapabilityAvailability

    private var _binding: DialogSearchableLanguagePickerBinding? = null
    private val binding get() = _binding!!

    private var selectedCode: String = "en"
    private var mode: Mode = Mode.TARGET
    private var interfaceLanguage: String = "en"
    private var onLanguageSelected: ((LanguageItem) -> Unit)? = null
    private lateinit var languageAdapter: LanguageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        selectedCode = args.getString(ARG_SELECTED_CODE, "en")
        mode = Mode.valueOf(args.getString(ARG_MODE, Mode.TARGET.name))
        interfaceLanguage = args.getString(ARG_INTERFACE_LANGUAGE, "en")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSearchableLanguagePickerBinding.inflate(layoutInflater)
        setupViews()
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (mode == Mode.SOURCE) R.string.translation_source_language_and_ocr else R.string.translation_target_language)
            .setView(binding.root)
            .create()
    }

    private fun setupViews() {
        val languages = when (mode) {
            Mode.SOURCE -> TranslationLanguageCatalog.buildSourceLanguageList(interfaceLanguage)
            Mode.TARGET -> TranslationLanguageCatalog.buildTargetLanguageList(interfaceLanguage)
        }

        languageAdapter = LanguageAdapter(
            selectedCode = selectedCode,
            mode = mode,
            noLegalOcrLabelsEnabled = capabilityAvailability.isOcrEngineSelectionAvailable(),
            onClick = { language ->
                onLanguageSelected?.invoke(language)
                dismiss()
            }
        )

        binding.recyclerLanguages.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = languageAdapter
            isFocusable = true
        }
        languageAdapter.submit(languages)

        binding.editLanguageSearch.doOnTextChanged { text, _, _, _ ->
            languageAdapter.filter(text?.toString().orEmpty())
        }
        binding.editLanguageSearch.post {
            binding.editLanguageSearch.requestFocus()
            requireContext().getSystemService<InputMethodManager>()
                ?.showSoftInput(binding.editLanguageSearch, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    enum class Mode {
        SOURCE,
        TARGET
    }

    companion object {
        const val TAG = "SearchableLanguagePickerDialog"

        private const val ARG_SELECTED_CODE = "arg_selected_code"
        private const val ARG_MODE = "arg_mode"
        private const val ARG_INTERFACE_LANGUAGE = "arg_interface_language"

        fun newInstance(
            selectedCode: String,
            mode: Mode,
            interfaceLanguage: String,
            onLanguageSelected: (LanguageItem) -> Unit
        ): SearchableLanguagePickerDialog {
            return SearchableLanguagePickerDialog().apply {
                arguments = bundleOf(
                    ARG_SELECTED_CODE to selectedCode,
                    ARG_MODE to mode.name,
                    ARG_INTERFACE_LANGUAGE to interfaceLanguage
                )
                this.onLanguageSelected = onLanguageSelected
            }
        }
    }

    private class LanguageAdapter(
        private val selectedCode: String,
        private val mode: Mode,
        private val noLegalOcrLabelsEnabled: Boolean,
        private val onClick: (LanguageItem) -> Unit
    ) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {

        private var allItems: List<LanguageItem> = emptyList()
        private var visibleItems: List<LanguageItem> = emptyList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
            val binding = ItemSearchableLanguageBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return LanguageViewHolder(binding, mode, noLegalOcrLabelsEnabled, onClick)
        }

        override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
            holder.bind(visibleItems[position], visibleItems[position].code == selectedCode)
        }

        override fun getItemCount(): Int = visibleItems.size

        fun submit(items: List<LanguageItem>) {
            allItems = items
            visibleItems = items
            notifyDataSetChanged()
        }

        fun filter(query: String) {
            val normalizedQuery = query.trim().lowercase(Locale.getDefault())
            visibleItems = if (normalizedQuery.isEmpty()) {
                allItems
            } else {
                allItems.filter { item -> item.matches(normalizedQuery) }
            }
            notifyDataSetChanged()
        }

        private fun LanguageItem.matches(query: String): Boolean {
            val searchableValues = listOf(localizedName, nativeName, code)
                .map { it.lowercase(Locale.getDefault()) }
            return searchableValues.any { it.startsWith(query) } || searchableValues.any { it.contains(query) }
        }

        private class LanguageViewHolder(
            private val binding: ItemSearchableLanguageBinding,
            private val mode: Mode,
            private val noLegalOcrLabelsEnabled: Boolean,
            private val onClick: (LanguageItem) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: LanguageItem, selected: Boolean) {
                val label = TranslationLanguageCatalog.formatLanguage(item)
                val capabilityLabel = item.capabilityLabel(binding.root.context, mode)
                LanguageFlagFormatter.applyFlagGlyph(binding.tvLanguageFlag, item)
                binding.tvLanguageName.text = "${item.localizedName} (${item.nativeName})"
                binding.tvLanguageCapabilities.text = capabilityLabel
                binding.tvLanguageCapabilities.isVisible = capabilityLabel.isNotBlank()
                binding.root.isSelected = selected
                binding.root.isActivated = selected
                val accessibleLabel = listOf(label, capabilityLabel)
                    .filter { it.isNotBlank() }
                    .joinToString(separator = ", ")
                binding.root.contentDescription = if (selected) {
                    "$accessibleLabel, ${binding.root.context.getString(R.string.selected_label)}"
                } else {
                    accessibleLabel
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

            private fun LanguageItem.capabilityLabel(context: Context, mode: Mode): String {
                return capabilities
                    .filter { capability ->
                        when {
                            capability == LanguageCapability.TRANSLATION -> true
                            mode != Mode.SOURCE -> false
                            capability == LanguageCapability.NO_LEGAL_OCR -> noLegalOcrLabelsEnabled
                            else -> true
                        }
                    }
                    .distinct()
                    .joinToString(separator = " · ") { capability ->
                        context.getString(capability.labelResId)
                    }
            }

            private val LanguageCapability.labelResId: Int
                get() = when (this) {
                    LanguageCapability.TRANSLATION -> R.string.language_capability_translation
                    LanguageCapability.BASIC_OCR -> R.string.language_capability_basic_ocr
                    LanguageCapability.QUALITY_OCR -> R.string.language_capability_quality_ocr
                    LanguageCapability.NO_LEGAL_OCR -> R.string.language_capability_nolegal_ocr
                }

            companion object {
                private val confirmKeyCodes = setOf(
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_SPACE
                )
            }
        }
    }
}
