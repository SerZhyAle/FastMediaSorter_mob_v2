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
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogSearchableLanguagePickerBinding
import com.sza.fastmediasorter.databinding.ItemSearchableLanguageBinding
import com.sza.fastmediasorter.ui.player.helpers.LanguageCapability
import com.sza.fastmediasorter.ui.player.helpers.LanguageFlagFormatter
import com.sza.fastmediasorter.ui.player.helpers.LanguageItem
import com.sza.fastmediasorter.ui.player.helpers.TranslationLanguageCatalog
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

/**
 * Searchable language picker. Returns the chosen language code to the host through a FragmentResult
 * ([RESULT_LANGUAGE_CODE] in the bundle) rather than a constructor lambda: S1214 - FragmentManager
 * rebuilds a restored dialog with the no-arg constructor, so a field-held handler would be null and
 * the pick would be dropped without a trace.
 */
@AndroidEntryPoint
class SearchableLanguagePickerDialog : DialogFragment() {

    private var _binding: DialogSearchableLanguagePickerBinding? = null
    private val binding get() = _binding!!

    private var selectedCode: String = "en"
    private var mode: Mode = Mode.TARGET
    private var interfaceLanguage: String = "en"

    // S1214: hosts that open the picker twice (source + target) pass their own key, so one
    // FragmentManager can carry both without either host seeing the other's pick.
    private var requestKey: String = RESULT_KEY
    private lateinit var languageAdapter: LanguageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        selectedCode = args.getString(ARG_SELECTED_CODE, "en")
        mode = Mode.valueOf(args.getString(ARG_MODE, Mode.TARGET.name))
        interfaceLanguage = args.getString(ARG_INTERFACE_LANGUAGE, "en")
        requestKey = args.getString(ARG_REQUEST_KEY) ?: RESULT_KEY
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSearchableLanguagePickerBinding.inflate(layoutInflater)
        setupViews()
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes())
            .setView(binding.root)
            .create()
    }

    private fun titleRes(): Int = when (mode) {
        Mode.SOURCE -> R.string.translation_source_language_and_ocr
        Mode.TARGET -> R.string.translation_target_language
        Mode.UI_LANGUAGE -> R.string.language
    }

    private fun setupViews() {
        val languages = when (mode) {
            Mode.SOURCE -> TranslationLanguageCatalog.buildSourceLanguageList(interfaceLanguage)
            Mode.TARGET -> TranslationLanguageCatalog.buildTargetLanguageList(interfaceLanguage)
            Mode.UI_LANGUAGE -> UiLanguagePickerItems.build(requireContext())
        }

        languageAdapter = LanguageAdapter(
            selectedCode = selectedCode,
            mode = mode,
            onClick = { language ->
                setFragmentResult(requestKey, bundleOf(RESULT_LANGUAGE_CODE to language.code))
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

    override fun onStart() {
        super.onStart()
        // Pure picker: a list row is the action (rows keep their own Enter/Space handler), so no-op
        // confirm only adds Esc-dismiss and focus traversal. Initial focus stays on the search field.
        DialogKeyboardDelegate.applyToDialogFragment(dialog, onConfirm = {})
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    enum class Mode {
        SOURCE,
        TARGET,

        /** S1190: the app's own interface language, sourced from `locales_config.xml`. */
        UI_LANGUAGE
    }

    companion object {
        const val TAG = "SearchableLanguagePickerDialog"
        const val RESULT_KEY = "language_picker_result"
        const val RESULT_LANGUAGE_CODE = "result_language_code"

        private const val ARG_SELECTED_CODE = "arg_selected_code"
        private const val ARG_MODE = "arg_mode"
        private const val ARG_INTERFACE_LANGUAGE = "arg_interface_language"
        private const val ARG_REQUEST_KEY = "arg_request_key"

        /**
         * Interface-language picker. [selectedCode] is a declared language tag or
         * [com.sza.fastmediasorter.core.util.LocaleHelper.FOLLOW_SYSTEM_LANGUAGE].
         */
        fun newInstanceForUiLanguage(
            selectedCode: String,
            requestKey: String = RESULT_KEY
        ): SearchableLanguagePickerDialog = newInstance(
            selectedCode = selectedCode,
            mode = Mode.UI_LANGUAGE,
            interfaceLanguage = selectedCode,
            requestKey = requestKey
        )

        fun newInstance(
            selectedCode: String,
            mode: Mode,
            interfaceLanguage: String,
            requestKey: String = RESULT_KEY
        ): SearchableLanguagePickerDialog {
            return SearchableLanguagePickerDialog().apply {
                arguments = bundleOf(
                    ARG_SELECTED_CODE to selectedCode,
                    ARG_MODE to mode.name,
                    ARG_INTERFACE_LANGUAGE to interfaceLanguage,
                    ARG_REQUEST_KEY to requestKey
                )
            }
        }
    }

    private class LanguageAdapter(
        private val selectedCode: String,
        private val mode: Mode,
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
            return LanguageViewHolder(binding, mode, onClick)
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
            private val onClick: (LanguageItem) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: LanguageItem, selected: Boolean) {
                val name = item.displayName()
                val label = if (item.flagEmoji.isBlank()) name else "${item.flagEmoji} $name"
                val capabilityLabel = item.capabilityLabel(binding.root.context, mode)
                LanguageFlagFormatter.applyFlagGlyph(binding.tvLanguageFlag, item)
                binding.tvLanguageName.text = name
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

            // "German (Deutsch)", but a single name when both spellings coincide - the interface-language
            // list contains rows ("Default", English while the app is English) where the doubled form
            // would read as a defect.
            private fun LanguageItem.displayName(): String =
                if (localizedName.equals(nativeName, ignoreCase = true)) {
                    localizedName
                } else {
                    "$localizedName ($nativeName)"
                }

            private fun LanguageItem.capabilityLabel(context: Context, mode: Mode): String {
                return capabilities
                    .filter { capability ->
                        capability == LanguageCapability.TRANSLATION || mode == Mode.SOURCE
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
