package com.sza.fastmediasorter.ui.launcher.picker

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper
import com.sza.fastmediasorter.databinding.DialogSearchableOptionPickerBinding
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerController
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.LeadingVisual
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.Option
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadgetRegistry
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * S0404: the first step of "put something on the desktop". Level one lists the kinds of thing a cell can
 * hold; choosing "Gadget" re-opens this same dialog on its gadget list - one class, two modes, so the
 * whole flow is FragmentResult-based and survives a config change (a lambda callback would not). Every
 * other kind hands off to the shared panel picker for that kind: the host reads the category out of the
 * result and opens the matching picker.
 *
 * The dialog carries only [row]/[col] and never touches a repository. Terminal placement - and the
 * ADR-10 rememberFileList write for a resource gadget - is the host + ViewModel's job.
 */
@AndroidEntryPoint
class LauncherCellContentPickerDialogFragment : DialogFragment() {

    @Inject
    lateinit var gadgetRegistry: LauncherGadgetRegistry

    private var _binding: DialogSearchableOptionPickerBinding? = null
    private val binding get() = _binding!!

    private var row: Int = 0
    private var col: Int = 0
    private var gadgetMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
        row = requireArguments().getInt(ARG_ROW)
        col = requireArguments().getInt(ARG_COL)
        gadgetMode = requireArguments().getBoolean(ARG_GADGET_MODE, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogSearchableOptionPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val titleRes = if (gadgetMode) {
            R.string.launcher_edit_pick_gadget_title
        } else {
            R.string.launcher_edit_add_cell_title
        }
        binding.tvOptionPickerTitle.text = getString(titleRes)
        binding.tvOptionPickerTitle.isVisible = true
        val options = if (gadgetMode) gadgetOptions() else categoryOptions()
        SearchableOptionPickerController.attach(binding, options, selectedId = null, resetRow = null) { picked ->
            picked?.let { onPicked(it.id) }
        }
    }

    private fun categoryOptions(): List<Option> = listOf(
        category(CATEGORY_APP, R.string.launcher_edit_kind_app, R.drawable.ic_apps),
        category(CATEGORY_FEATURE, R.string.launcher_edit_kind_feature, R.drawable.ic_launcher_mode),
        category(CATEGORY_RESOURCE, R.string.launcher_edit_kind_resource, R.drawable.ic_folder),
        category(CATEGORY_STREAM, R.string.launcher_edit_kind_stream, R.drawable.ic_cast),
        category(CATEGORY_OS, R.string.launcher_edit_kind_os, R.drawable.ic_settings),
        category(CATEGORY_SCHEDULED_OP, R.string.launcher_edit_kind_scheduled_op, R.drawable.ic_schedule),
        category(CATEGORY_GADGET, R.string.launcher_edit_kind_gadget, R.drawable.ic_view_grid),
    )

    private fun gadgetOptions(): List<Option> = gadgetRegistry.all().map { gadget ->
        Option(
            id = GADGET_PREFIX + gadget.key,
            label = getString(gadget.labelRes),
            leading = LeadingVisual.IconRes(gadget.iconRes),
        )
    }

    private fun category(
        id: String,
        @StringRes labelRes: Int,
        @DrawableRes iconRes: Int,
    ): Option = Option(id = id, label = getString(labelRes), leading = LeadingVisual.IconRes(iconRes))

    private fun onPicked(id: String) {
        val gadgetKey = id.takeIf { it.startsWith(GADGET_PREFIX) }?.removePrefix(GADGET_PREFIX)
        val category = if (gadgetKey != null) CATEGORY_GADGET else id
        setFragmentResult(
            RESULT_KEY,
            bundleOf(
                RESULT_ROW to row,
                RESULT_COL to col,
                RESULT_CATEGORY to category,
                RESULT_GADGET_KEY to gadgetKey,
            ),
        )
        dismiss()
    }

    override fun onStart() {
        super.onStart()
        val metrics = resources.displayMetrics
        val width = minOf(
            (metrics.widthPixels * DIALOG_WIDTH_FRACTION).toInt(),
            (metrics.density * DIALOG_MAX_WIDTH_DP).toInt(),
        )
        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
        }
        dialog?.let { DialogAccessibilityHelper.applyInitialFocus(it) }
        DialogKeyboardDelegate.applyToDialogFragment(dialog, onConfirm = {})
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).also { it.setCanceledOnTouchOutside(true) }

    companion object {
        const val TAG = "LauncherCellContentPicker"

        // A distinct tag for the gadget-list re-open, so findFragmentByTag does not see the just-dismissed
        // category dialog (its transaction commits asynchronously) and skip showing the second level.
        const val TAG_GADGET = "LauncherCellContentPickerGadget"

        const val RESULT_KEY = "launcher_content_picker_result"
        const val RESULT_ROW = "result_row"
        const val RESULT_COL = "result_col"
        const val RESULT_CATEGORY = "result_category"
        const val RESULT_GADGET_KEY = "result_gadget_key"

        const val CATEGORY_APP = "app"
        const val CATEGORY_FEATURE = "feature"
        const val CATEGORY_RESOURCE = "resource"
        const val CATEGORY_STREAM = "stream"
        const val CATEGORY_OS = "os"
        const val CATEGORY_SCHEDULED_OP = "scheduled_op"
        const val CATEGORY_GADGET = "gadget"

        private const val GADGET_PREFIX = "gadget:"
        private const val ARG_ROW = "arg_row"
        private const val ARG_COL = "arg_col"
        private const val ARG_GADGET_MODE = "arg_gadget_mode"
        private const val DIALOG_WIDTH_FRACTION = 0.92f
        private const val DIALOG_MAX_WIDTH_DP = 560f

        fun newInstance(row: Int, col: Int): LauncherCellContentPickerDialogFragment =
            LauncherCellContentPickerDialogFragment().apply {
                arguments = bundleOf(ARG_ROW to row, ARG_COL to col)
            }

        fun newGadgetInstance(row: Int, col: Int): LauncherCellContentPickerDialogFragment =
            LauncherCellContentPickerDialogFragment().apply {
                arguments = bundleOf(ARG_ROW to row, ARG_COL to col, ARG_GADGET_MODE to true)
            }
    }
}
