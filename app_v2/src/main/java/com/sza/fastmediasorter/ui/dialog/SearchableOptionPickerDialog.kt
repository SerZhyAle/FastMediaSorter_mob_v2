package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogSearchableOptionPickerBinding
import com.sza.fastmediasorter.ui.player.helpers.LanguageItem

/**
 * Generic single-choice picker with a conditional type-to-filter search field over a (possibly long)
 * option list (S0580 ADR-1). The shared wiring lives in [SearchableOptionPickerController] so the
 * migrated quick-launch picker fragments (S0947) inherit identical behaviour. Each [Option] may carry
 * a language-flag glyph or a general [LeadingVisual] image; options without one show plain text. The
 * option list and result callback are retained instance fields rather than Bundle arguments because
 * [Option] can hold a non-Parcelable [LanguageItem] / Drawable - the same transient pattern the
 * language picker uses; only title/selection survive in args.
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

        /** A drawable resource id (e.g. an internal-feature / OS-shortcut icon). */
        data class IconRes(@androidx.annotation.DrawableRes val resId: Int) : LeadingVisual

        /** A Glide-loadable model (e.g. a resource thumbnail Uri/path). */
        data class Thumbnail(val model: Any) : LeadingVisual
    }

    private var _binding: DialogSearchableOptionPickerBinding? = null
    private val binding get() = _binding!!

    private var title: String = ""
    private var selectedId: String? = null
    private var options: List<Option> = emptyList()
    private var onPicked: ((Option?) -> Unit)? = null

    // S0947: streams uses a leading "All" reset row that maps to a null pick; general single-choice
    // pickers migrated onto this component opt out so there is no spurious clear row.
    private var includeResetRow: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        title = args.getString(ARG_TITLE).orEmpty()
        selectedId = args.getString(ARG_SELECTED_ID)
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
            onPicked?.invoke(picked)
            dismiss()
        }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(binding.root)
            .create()
    }

    override fun onStart() {
        super.onStart()
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

        private const val RESET_ID = "__reset__"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_SELECTED_ID = "arg_selected_id"

        fun newInstance(
            title: String,
            options: List<Option>,
            selectedId: String?,
            includeResetRow: Boolean = true,
            onPicked: (Option?) -> Unit,
        ): SearchableOptionPickerDialog = SearchableOptionPickerDialog().apply {
            arguments = bundleOf(
                ARG_TITLE to title,
                ARG_SELECTED_ID to selectedId,
            )
            this.options = options
            this.includeResetRow = includeResetRow
            this.onPicked = onPicked
        }
    }
}
