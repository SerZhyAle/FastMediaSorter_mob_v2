package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogSearchableOptionPickerBinding
import com.sza.fastmediasorter.ui.player.helpers.LanguageItem

/**
 * Generic single-choice picker with a conditional type-to-filter search field over a (possibly long)
 * option list (S0580 ADR-1). The shared wiring lives in [SearchableOptionPickerController] so the
 * migrated quick-launch picker fragments (S0947) inherit identical behaviour. Each [Option] may carry
 * a language-flag glyph or a general [LeadingVisual] image; options without one show plain text.
 *
 * The pick is reported through a FragmentResult ([RESULT_OPTION_ID] in the bundle, null for the reset
 * row) under a per-host request key, not through a constructor lambda: the FragmentManager rebuilds a
 * restored dialog with the no-arg constructor, so a field-held handler would be null and the pick would
 * be dropped without a trace. The option list stays a retained instance field because [Option] can hold
 * a non-Parcelable [LanguageItem] / Drawable and cannot go into a Bundle, so a restored instance has
 * nothing to offer and closes itself instead of presenting an empty list; only title, selection, reset
 * row and request key survive in args.
 */
class SearchableOptionPickerDialog : DialogFragment() {

    /**
     * A single-choice option: [label] with an optional leading visual. [flag] renders a language-flag
     * glyph (streams facets); [leading] carries a general image (app icon / resource thumbnail / icon
     * res) for migrated pickers (S0947). At most one leading visual is shown - [leading] wins.
     */
    data class Option(
        val id: String,
        val label: String,
        val flag: LanguageItem? = null,
        val leading: LeadingVisual? = null,
    )

    /** General leading image for an [Option] (S0947); complements the language-flag [Option.flag]. */
    sealed interface LeadingVisual {
        /** A ready Drawable (e.g. an installed app icon). */
        data class IconDrawable(val drawable: android.graphics.drawable.Drawable) : LeadingVisual

        /**
         * A drawable resource id (e.g. an internal-feature / OS-shortcut icon). [tintIcon] opts a flat
         * single-colour glyph into a themed tint (S2062) - some icon resources in this app are
         * deliberately filled white/constant, meant to be tinted at their point of use; a branded or
         * multi-colour icon (a YouTube logo, an `_accent` widget glyph) must keep `false`, the default,
         * or the tint would flatten it to one colour.
         */
        data class IconRes(
            @androidx.annotation.DrawableRes val resId: Int,
            val tintIcon: Boolean = false,
        ) : LeadingVisual

        /** A Glide-loadable model (e.g. a resource thumbnail Uri/path). */
        data class Thumbnail(val model: Any) : LeadingVisual
    }

    private var _binding: DialogSearchableOptionPickerBinding? = null
    private val binding get() = _binding!!

    private var title: String = ""
    private var selectedId: String? = null
    private var options: List<Option> = emptyList()

    // Hosts that open several pickers from one screen pass their own key, so a single FragmentManager
    // carries them all without one host seeing another's pick.
    private var requestKey: String = RESULT_KEY

    // S0947: streams uses a leading "All" reset row that maps to a null pick; general single-choice
    // pickers migrated onto this component opt out so there is no spurious clear row.
    private var includeResetRow: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        title = args.getString(ARG_TITLE).orEmpty()
        selectedId = args.getString(ARG_SELECTED_ID)
        requestKey = args.getString(ARG_REQUEST_KEY) ?: RESULT_KEY
        includeResetRow = args.getBoolean(ARG_INCLUDE_RESET_ROW, true)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSearchableOptionPickerBinding.inflate(layoutInflater)
        val resetRow = if (includeResetRow) {
            Option(id = RESET_ID, label = getString(R.string.streams_filter_all))
        } else {
            null
        }
        // S1286: cap only - the MaterialAlertDialog frame already supplies the surface, so this host must
        // not gain the picker card on top of it.
        SearchableOptionPickerWindow.applyHeightCap(binding)
        SearchableOptionPickerController.attach(binding, options, selectedId, resetRow) { picked ->
            setFragmentResult(requestKey, bundleOf(RESULT_OPTION_ID to picked?.id))
            dismiss()
        }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(binding.root)
            .create()
    }

    override fun onStart() {
        super.onStart()
        if (options.isEmpty()) {
            // The list cannot be bundled (see the class KDoc), so a restored instance has nothing to
            // offer - close rather than present rows the user cannot act on.
            dismiss()
            return
        }
        // Pure picker: a row tap is the action, so confirm is a no-op; this only adds Esc-dismiss + focus
        // traversal (S0947: the field is passive, so initial focus falls on the list, not the search).
        DialogKeyboardDelegate.applyToDialogFragment(dialog, onConfirm = {})
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SearchableOptionPickerDialog"
        const val RESULT_KEY = "option_picker_result"
        const val RESULT_OPTION_ID = "result_option_id"

        private const val RESET_ID = "__reset__"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_SELECTED_ID = "arg_selected_id"
        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val ARG_INCLUDE_RESET_ROW = "arg_include_reset_row"

        fun newInstance(
            title: String,
            options: List<Option>,
            selectedId: String?,
            includeResetRow: Boolean = true,
            requestKey: String = RESULT_KEY,
        ): SearchableOptionPickerDialog = SearchableOptionPickerDialog().apply {
            arguments = bundleOf(
                ARG_TITLE to title,
                ARG_SELECTED_ID to selectedId,
                ARG_INCLUDE_RESET_ROW to includeResetRow,
                ARG_REQUEST_KEY to requestKey,
            )
            this.options = options
        }
    }
}
