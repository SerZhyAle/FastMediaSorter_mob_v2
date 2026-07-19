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
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerController
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.LeadingVisual
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.Option

/**
 * S0404: the second step of the resource add-flow - how the chosen resource opens when its cell is
 * tapped (Phase 04 modes: browse the file list, run a slideshow, or play/read). The resource id rides
 * in the arguments and comes back in the result, so the terminal result carries both halves of a
 * [LauncherResourceMode] shortcut and the host keeps no state between the two pickers.
 *
 * No Hilt: the three options are static, so nothing is injected.
 */
class LauncherResourceModePickerDialogFragment : DialogFragment() {

    private var _binding: DialogSearchableOptionPickerBinding? = null
    private val binding get() = _binding!!

    private var resourceId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
        resourceId = requireArguments().getLong(ARG_RESOURCE_ID)
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
        binding.tvOptionPickerTitle.text = getString(R.string.launcher_edit_pick_mode_title)
        binding.tvOptionPickerTitle.isVisible = true
        SearchableOptionPickerController.attach(binding, buildOptions(), selectedId = null, resetRow = null) { picked ->
            picked?.let { onModePicked(it.id) }
        }
    }

    private fun buildOptions(): List<Option> = listOf(
        modeOption(LauncherResourceMode.BROWSE, R.string.launcher_edit_mode_browse, R.drawable.ic_open_in_browse),
        modeOption(LauncherResourceMode.SLIDESHOW, R.string.launcher_edit_mode_slideshow, R.drawable.ic_slideshow),
        modeOption(LauncherResourceMode.PLAY, R.string.launcher_edit_mode_play, R.drawable.ic_play),
    )

    private fun modeOption(
        mode: LauncherResourceMode,
        @StringRes labelRes: Int,
        @DrawableRes iconRes: Int,
    ): Option = Option(id = mode.name, label = getString(labelRes), leading = LeadingVisual.IconRes(iconRes))

    private fun onModePicked(modeName: String) {
        setFragmentResult(
            RESULT_KEY,
            bundleOf(RESULT_RESOURCE_ID to resourceId, RESULT_MODE to modeName),
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
        const val TAG = "LauncherResourceModePicker"
        const val RESULT_KEY = "launcher_resource_mode_result"
        const val RESULT_RESOURCE_ID = "result_resource_id"
        const val RESULT_MODE = "result_mode"

        private const val ARG_RESOURCE_ID = "arg_resource_id"
        private const val DIALOG_WIDTH_FRACTION = 0.92f
        private const val DIALOG_MAX_WIDTH_DP = 560f

        fun newInstance(resourceId: Long): LauncherResourceModePickerDialogFragment =
            LauncherResourceModePickerDialogFragment().apply {
                arguments = bundleOf(ARG_RESOURCE_ID to resourceId)
            }
    }
}
