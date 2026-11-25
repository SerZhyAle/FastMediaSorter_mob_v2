package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogSearchableLanguagePickerBinding
import com.sza.fastmediasorter.databinding.ItemSearchableLanguageBinding
import com.sza.fastmediasorter.ui.common.showSoftInputImplicitly
import com.sza.fastmediasorter.ui.player.helpers.LanguageCapability
import com.sza.fastmediasorter.ui.player.helpers.LanguageItem
import com.sza.fastmediasorter.ui.player.helpers.TranslationLanguageCatalog
import com.sza.fastmediasorter.ui.player.helpers.withAutoDetectLabel
import timber.log.Timber
import java.util.Locale

/**
 * The lower-cased strings a query is matched against: the name in the interface language, the
 * endonym, the code and the English name. The English name is what a user who cannot read the current
 * interface language still knows ("Russian"), so it must match whichever language the app shows.
 */
internal fun languageSearchKeys(item: LanguageItem, locale: Locale = Locale.getDefault()): List<String> {
    val englishName = Locale.forLanguageTag(item.code).getDisplayLanguage(Locale.ENGLISH)
    return listOf(item.localizedName, item.nativeName, item.code, englishName)
        .filter { it.isNotBlank() }
        .map { it.lowercase(locale) }
        .distinct()
}

private const val MAX_DIALOG_CARD_AND_INSETS_DP = 580f

/**
 * Computes dialog window width in pixels, keeping narrow portrait screens full-width while
 * capping wide landscape/tablet screens so the two-column layout stays balanced.
 */
internal fun computeLanguagePickerDialogWidthPx(screenWidthPx: Int, density: Float): Int {
    if (screenWidthPx <= 0 || density <= 0f) return screenWidthPx.coerceAtLeast(0)
    val maxWidthPx = (density * MAX_DIALOG_CARD_AND_INSETS_DP).toInt()
    return minOf(screenWidthPx, maxWidthPx)
}

/**
 * Computes the root card height cap in pixels from the visible area above any soft keyboard,
 * subtracting Material dialog vertical window insets and bounding by [maxScrollCapPx].
 */
internal fun computeLanguagePickerMaxHeightPx(
    visibleHeightPx: Int,
    verticalWindowInsetsPx: Int,
    maxScrollCapPx: Int,
): Int {
    if (visibleHeightPx <= 0) return maxScrollCapPx.coerceAtLeast(0)
    val availableCardPx = (visibleHeightPx - verticalWindowInsetsPx.coerceAtLeast(0))
        .coerceAtLeast(1)
    return if (maxScrollCapPx > 0) minOf(availableCardPx, maxScrollCapPx) else availableCardPx
}

/**
 * Searchable language picker. Returns the chosen language code to the host through a FragmentResult
 * ([RESULT_LANGUAGE_CODE] in the bundle) rather than a constructor lambda: S1214 - FragmentManager
 * rebuilds a restored dialog with the no-arg constructor, so a field-held handler would be null and
 * the pick would be dropped without a trace.
 */
// S1703: @AndroidEntryPoint dropped - the dialog lost its only @Inject member with PaddleOCR's
// withdrawal and injects nothing now (Rule 20).
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
    private var boundsObserverTarget: View? = null
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

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
            .setView(binding.root)
            .create()
            .also { created ->
                val stateMode = if (opensKeyboardOnShow()) {
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
                } else {
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                }
                // Resize the floating dialog window above the soft keyboard while preserving S4708's
                // initial keyboard visibility rule per mode.
                @Suppress("DEPRECATION")
                created.window?.setSoftInputMode(
                    stateMode or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                )
            }
    }

    private fun titleRes(): Int = when (mode) {
        Mode.SOURCE -> R.string.translation_source_language_and_ocr
        Mode.TARGET -> R.string.translation_target_language
        Mode.UI_LANGUAGE -> R.string.language
    }

    private fun setupViews() {
        binding.tvLanguagePickerTitle.setText(titleRes())
        ViewCompat.setAccessibilityHeading(binding.tvLanguagePickerTitle, true)
        val languages = when (mode) {
            Mode.SOURCE -> {
                val autoDetectLabel = getString(R.string.language_auto_detect)
                Timber.d("S4836: auto-detect label=$autoDetectLabel")
                TranslationLanguageCatalog.buildSourceLanguageList(interfaceLanguage)
                    .map { it.withAutoDetectLabel(autoDetectLabel) }
            }
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
        Timber.d("S4708: language picker opened mode=$mode rows=${languages.size} keyboard=${opensKeyboardOnShow()}")
        Timber.d("S4820: language picker bounds mode=$mode orientation=${resources.configuration.orientation}")
        Timber.d("S4836: language picker rows mode=$mode selected=$selectedCode")

        binding.editLanguageSearch.doOnTextChanged { text, _, _, _ ->
            languageAdapter.filter(text?.toString().orEmpty()) { isEmpty ->
                // The commit callback can outlive the view when the dialog closes mid-diff.
                _binding?.tvLanguageEmpty?.isVisible = isEmpty
                Timber.d("S4708: language picker filter emptyState=$isEmpty")
            }
        }
        binding.editLanguageSearch.post {
            val search = _binding?.editLanguageSearch ?: return@post
            search.requestFocus()
            if (opensKeyboardOnShow()) {
                requireContext().getSystemService<InputMethodManager>()?.showSoftInputImplicitly(search)
            }
        }
    }

    // The interface-language list is thirteen rows: a keyboard forced up on a short screen leaves two
    // of them visible, and scrolling beats typing for that few. Source/Target lists are long, so
    // search stays the primary way in and the keyboard still opens by itself.
    private fun opensKeyboardOnShow(): Boolean = mode != Mode.UI_LANGUAGE

    override fun onStart() {
        super.onStart()
        applyDialogWidthAndBounds()
        attachBoundsListeners()
        // Pure picker: a list row is the action (rows keep their own Enter/Space handler), so no-op
        // confirm only adds Esc-dismiss and focus traversal. Initial focus stays on the search field.
        DialogKeyboardDelegate.applyToDialogFragment(dialog, onConfirm = {})
    }

    override fun onStop() {
        detachBoundsListeners()
        super.onStop()
    }

    override fun onDestroyView() {
        detachBoundsListeners()
        super.onDestroyView()
        _binding = null
    }

    private fun applyDialogWidthAndBounds() {
        val dialogWindow = dialog?.window ?: return
        val metrics = resources.displayMetrics
        val targetWidth = computeLanguagePickerDialogWidthPx(metrics.widthPixels, metrics.density)
        dialogWindow.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        updateDialogHeightBounds()
    }

    private fun attachBoundsListeners() {
        detachBoundsListeners()
        val dialogDecor = dialog?.window?.decorView
        val hostDecor = activity?.window?.decorView ?: dialogDecor ?: return
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            updateDialogHeightBounds()
        }
        hostDecor.viewTreeObserver.addOnGlobalLayoutListener(listener)
        boundsObserverTarget = hostDecor
        globalLayoutListener = listener
        if (dialogDecor != null) {
            ViewCompat.setOnApplyWindowInsetsListener(dialogDecor) { _, insets ->
                updateDialogHeightBounds()
                insets
            }
        }
    }

    private fun detachBoundsListeners() {
        val listener = globalLayoutListener
        val target = boundsObserverTarget
        if (listener != null && target != null && target.viewTreeObserver.isAlive) {
            target.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
        globalLayoutListener = null
        boundsObserverTarget = null
        dialog?.window?.decorView?.let { ViewCompat.setOnApplyWindowInsetsListener(it, null) }
    }

    private fun updateDialogHeightBounds() {
        val currentBinding = _binding ?: return
        val dialogDecor = dialog?.window?.decorView
        val hostDecor = activity?.window?.decorView ?: dialogDecor ?: return
        val visibleRect = Rect()
        hostDecor.getWindowVisibleDisplayFrame(visibleRect)
        val rectHeight = visibleRect.height()
        val hostInsets = ViewCompat.getRootWindowInsets(hostDecor)
        val dialogInsets = dialogDecor?.let { ViewCompat.getRootWindowInsets(it) }
        val insetHeight = computeInsetHeightPx(hostDecor.height, hostInsets, dialogInsets)
        val availableHeightPx = when {
            rectHeight > 0 && insetHeight > 0 -> minOf(rectHeight, insetHeight)
            rectHeight > 0 -> rectHeight
            else -> insetHeight
        }
        val bgPadding = Rect()
        dialogDecor?.background?.getPadding(bgPadding)
        val verticalInsetsPx = bgPadding.top + bgPadding.bottom
        val maxScrollCapPx = resources.getDimensionPixelSize(R.dimen.dialog_scroll_max_height)
        currentBinding.languagePickerRoot.maxHeightPx = computeLanguagePickerMaxHeightPx(
            visibleHeightPx = availableHeightPx,
            verticalWindowInsetsPx = verticalInsetsPx,
            maxScrollCapPx = maxScrollCapPx,
        )
    }

    private fun computeInsetHeightPx(
        hostHeightPx: Int,
        hostInsets: WindowInsetsCompat?,
        dialogInsets: WindowInsetsCompat?,
    ): Int {
        if (hostHeightPx <= 0 || hostInsets == null) return 0
        val sysBars = hostInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val hostImeBottom = hostInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val dialogImeBottom = dialogInsets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        val effectiveBottom = maxOf(sysBars.bottom, hostImeBottom, dialogImeBottom)
        return (hostHeightPx - sysBars.top - effectiveBottom).coerceAtLeast(0)
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

    private object LanguageDiffCallback : DiffUtil.ItemCallback<LanguageItem>() {
        override fun areItemsTheSame(oldItem: LanguageItem, newItem: LanguageItem): Boolean =
            oldItem.code == newItem.code

        override fun areContentsTheSame(oldItem: LanguageItem, newItem: LanguageItem): Boolean =
            oldItem == newItem
    }

    private class LanguageAdapter(
        private val selectedCode: String,
        private val mode: Mode,
        private val onClick: (LanguageItem) -> Unit
    ) : ListAdapter<LanguageItem, LanguageAdapter.LanguageViewHolder>(LanguageDiffCallback) {

        private var allItems: List<LanguageItem> = emptyList()
        private var searchKeys: Map<String, List<String>> = emptyMap()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
            val binding = ItemSearchableLanguageBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return LanguageViewHolder(binding, mode, onClick)
        }

        override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
            val item = getItem(position)
            holder.bind(item, item.code == selectedCode)
        }

        fun submit(items: List<LanguageItem>) {
            allItems = items
            searchKeys = items.associate { item -> item.code to languageSearchKeys(item) }
            submitList(items)
        }

        /** [onFiltered] reports, once the list has settled, whether the query left no row. */
        fun filter(query: String, onFiltered: (isEmpty: Boolean) -> Unit) {
            val normalizedQuery = query.trim().lowercase(Locale.getDefault())
            val filtered = if (normalizedQuery.isEmpty()) {
                allItems
            } else {
                allItems.filter { item ->
                    searchKeys[item.code].orEmpty().any { key -> key.contains(normalizedQuery) }
                }
            }
            submitList(filtered) { onFiltered(filtered.isEmpty()) }
        }

        private class LanguageViewHolder(
            private val binding: ItemSearchableLanguageBinding,
            private val mode: Mode,
            private val onClick: (LanguageItem) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: LanguageItem, selected: Boolean) {
                val name = item.displayName()
                val capabilityLabel = item.capabilityLabel(binding.root.context, mode)
                // The glyph (the globe of "Default" / "Auto-detect") leads the name rather than owning a
                // column: every real language has none, so a reserved column was blank on all other rows.
                binding.tvLanguageName.text = if (item.glyph.isBlank()) name else "${item.glyph} $name"
                binding.tvLanguageCapabilities.text = capabilityLabel
                binding.tvLanguageCapabilities.isVisible = capabilityLabel.isNotBlank()
                binding.root.isSelected = selected
                binding.root.isActivated = selected
                val accessibleLabel = listOf(name, capabilityLabel)
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
