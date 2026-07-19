package com.sza.fastmediasorter.ui.applaunchpanel.edit

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper
import com.sza.fastmediasorter.databinding.DialogSearchableOptionPickerBinding
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerController
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.LeadingVisual
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.Option
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Lists the curated OS targets from [OsShortcutCatalog] that actually resolve on this device
 * (strategic S0663 §6.3), returning the chosen target key to the host via a FragmentResult.
 * S0947: renders through the canonical [SearchableOptionPickerController].
 */
@AndroidEntryPoint
class OsShortcutPickerDialogFragment : DialogFragment() {

    private var _binding: DialogSearchableOptionPickerBinding? = null
    private val binding get() = _binding!!

    private var slotIndex: Int = 0

    // S0404: hosts other than the panel editor pass their own key, so several hosts can share one
    // FragmentManager without receiving each other's results.
    private var requestKey: String = RESULT_KEY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
        slotIndex = requireArguments().getInt(ARG_SLOT, 0)
        requestKey = requireArguments().getString(ARG_REQUEST_KEY) ?: RESULT_KEY
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
        binding.tvOptionPickerTitle.text = getString(R.string.app_launch_panel_picker_os_title)
        binding.tvOptionPickerTitle.isVisible = true
        collectOnLifecycle(flow { emit(buildOptions()) }) { options ->
            SearchableOptionPickerController.attach(binding, options, selectedId = null, resetRow = null) { picked ->
                picked?.let { onTargetPicked(it.id) }
            }
        }
    }

    // PackageManager resolution runs off the main thread (strategic §3.2 performance constraint).
    private suspend fun buildOptions(): List<Option> = withContext(Dispatchers.IO) {
        OsShortcutCatalog.available(requireContext()).map { target ->
            Option(
                id = target.key,
                label = getString(target.labelRes),
                leading = LeadingVisual.IconRes(target.iconRes),
            )
        }
    }

    private fun onTargetPicked(targetKey: String) {
        setFragmentResult(requestKey, bundleOf(RESULT_SLOT to slotIndex, RESULT_TARGET_KEY to targetKey))
        dismiss()
    }

    override fun onStart() {
        super.onStart()
        val metrics = resources.displayMetrics
        val width = minOf((metrics.widthPixels * 0.92f).toInt(), (metrics.density * 560f).toInt())
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
        const val TAG = "OsShortcutPickerDialog"
        const val RESULT_KEY = "os_shortcut_picker_result"
        const val RESULT_SLOT = "result_slot"
        const val RESULT_TARGET_KEY = "result_target_key"

        private const val ARG_SLOT = "arg_slot"
        private const val ARG_REQUEST_KEY = "arg_request_key"

        fun newInstance(slotIndex: Int): OsShortcutPickerDialogFragment =
            OsShortcutPickerDialogFragment().apply { arguments = bundleOf(ARG_SLOT to slotIndex) }

        /**
         * S0404: host factory with no panel-slot semantics - the result arrives on [requestKey], so a
         * second host on the same FragmentManager never sees the panel editor's picks. The bundle still
         * carries RESULT_SLOT (0); a non-panel host ignores it.
         */
        fun newInstance(requestKey: String): OsShortcutPickerDialogFragment =
            OsShortcutPickerDialogFragment().apply { arguments = bundleOf(ARG_REQUEST_KEY to requestKey) }
    }
}
