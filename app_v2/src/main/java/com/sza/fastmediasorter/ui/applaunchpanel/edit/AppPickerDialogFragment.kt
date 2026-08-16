package com.sza.fastmediasorter.ui.applaunchpanel.edit

import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper
import com.sza.fastmediasorter.databinding.DialogSearchableOptionPickerBinding
import com.sza.fastmediasorter.domain.usecase.panel.QueryLaunchableAppsUseCase
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerController
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.LeadingVisual
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.Option
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerWindow
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Lists installed launcher apps and returns the chosen package to the host via a FragmentResult.
 * Used by the Edit-panel screen to fill or replace a slot. Sized/centred as a card in [onStart].
 * S0947: renders through the canonical [SearchableOptionPickerController] (conditional search filter,
 * selected highlight/autoscroll) instead of a bespoke adapter.
 */
@AndroidEntryPoint
class AppPickerDialogFragment : DialogFragment() {

    @Inject
    lateinit var queryLaunchableApps: QueryLaunchableAppsUseCase

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
        binding.tvOptionPickerTitle.text = getString(R.string.app_picker_title)
        binding.tvOptionPickerTitle.isVisible = true
        val columns = currentColumnCount()
        // One-shot package query wrapped as a flow so it is collected lifecycle-safely (the list does
        // not change while the picker is open).
        collectOnLifecycle(flow { emit(queryLaunchableApps()) }) { apps ->
            val options = apps.map { app ->
                Option(
                    id = app.packageName,
                    label = app.label,
                    leading = LeadingVisual.IconDrawable(app.icon),
                )
            }
            SearchableOptionPickerController.attach(
                binding = binding,
                options = options,
                selectedId = null,
                resetRow = null,
                columns = columns,
            ) { picked ->
                picked?.let { onAppPicked(it.id) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        SearchableOptionPickerWindow.apply(dialog, binding)
        dialog?.let { DialogAccessibilityHelper.applyInitialFocus(it) }
        DialogKeyboardDelegate.applyToDialogFragment(dialog, onConfirm = {})
    }

    /**
     * S1095: the desktop host absorbs rotation via android:configChanges, so this dialog is never
     * recreated and [onViewCreated] - where the column count is derived from dialog width - does not run
     * again. Without this the picker keeps the portrait column count on a landscape screen twice as wide.
     *
     * Only the span count and the measurements change; the controller is deliberately not re-attached,
     * because rebuilding the list would discard whatever the user has typed into the search field.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (_binding == null) return
        SearchableOptionPickerWindow.apply(dialog, binding)
        val columns = currentColumnCount()
        SearchableOptionPickerController.reflowColumns(binding, columns)
    }

    // S1413: the rule itself moved to the shared window object - every grid host needs the same one.
    private fun currentColumnCount(): Int =
        SearchableOptionPickerWindow.columnsFor(resources.displayMetrics)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun onAppPicked(packageName: String) {
        setFragmentResult(
            requestKey,
            bundleOf(RESULT_SLOT to slotIndex, RESULT_PACKAGE to packageName),
        )
        dismiss()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).also { it.setCanceledOnTouchOutside(true) }

    companion object {
        const val TAG = "AppPickerDialog"
        const val RESULT_KEY = "app_picker_result"
        const val RESULT_SLOT = "result_slot"
        const val RESULT_PACKAGE = "result_package"

        private const val ARG_SLOT = "arg_slot"
        private const val ARG_REQUEST_KEY = "arg_request_key"

        fun newInstance(slotIndex: Int): AppPickerDialogFragment =
            AppPickerDialogFragment().apply { arguments = bundleOf(ARG_SLOT to slotIndex) }

        /**
         * S0404: host factory with no panel-slot semantics - the result arrives on [requestKey], so a
         * second host on the same FragmentManager never sees the panel editor's picks. The bundle still
         * carries RESULT_SLOT (0); a non-panel host ignores it.
         */
        fun newInstance(requestKey: String): AppPickerDialogFragment =
            AppPickerDialogFragment().apply { arguments = bundleOf(ARG_REQUEST_KEY to requestKey) }
    }
}
