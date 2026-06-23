package com.sza.fastmediasorter.ui.applaunchpanel.edit

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper
import com.sza.fastmediasorter.databinding.DialogAppPickerBinding
import com.sza.fastmediasorter.domain.usecase.panel.LaunchableApp
import com.sza.fastmediasorter.domain.usecase.panel.QueryLaunchableAppsUseCase
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Lists installed launcher apps and returns the chosen package to the host via a FragmentResult.
 * Used by the Edit-panel screen to fill or replace a slot. Sized/centred as a card in [onStart].
 */
@AndroidEntryPoint
class AppPickerDialogFragment : DialogFragment() {

    @Inject
    lateinit var queryLaunchableApps: QueryLaunchableAppsUseCase

    private var _binding: DialogAppPickerBinding? = null
    private val binding get() = _binding!!

    private var slotIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
        slotIndex = requireArguments().getInt(ARG_SLOT, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogAppPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvApps.layoutManager = LinearLayoutManager(requireContext())
        // One-shot package query wrapped as a flow so it is collected lifecycle-safely (the list does
        // not change while the picker is open).
        collectOnLifecycle(flow { emit(queryLaunchableApps()) }) { apps ->
            binding.rvApps.adapter = AppPickerAdapter(apps, ::onAppPicked)
        }
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

    private fun onAppPicked(app: LaunchableApp) {
        setFragmentResult(
            RESULT_KEY,
            bundleOf(RESULT_SLOT to slotIndex, RESULT_PACKAGE to app.packageName)
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

        fun newInstance(slotIndex: Int): AppPickerDialogFragment =
            AppPickerDialogFragment().apply { arguments = bundleOf(ARG_SLOT to slotIndex) }
    }
}
