package com.sza.fastmediasorter.ui.launcher.picker

import android.app.Dialog
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
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import com.sza.fastmediasorter.domain.usecase.launcher.ResolveLauncherCommandLabelUseCase
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerController
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.LeadingVisual
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.Option
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerWindow
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * S1103: lists the user's saved scheduled operations and returns the chosen operation id to the host,
 * so it can be pinned to a desktop cell. Each row reuses the same live label the cell will show
 * ([ResolveLauncherCommandLabelUseCase]) rather than duplicating the type + source/target formatting.
 */
@AndroidEntryPoint
class LauncherScheduledOpPickerDialogFragment : DialogFragment() {

    @Inject
    lateinit var scheduledOperationRepository: ScheduledOperationRepository

    @Inject
    lateinit var resolveVisual: ResolveLauncherCommandLabelUseCase

    private var _binding: DialogSearchableOptionPickerBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
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
        binding.tvOptionPickerTitle.text = getString(R.string.launcher_scheduled_op_pick_title)
        binding.tvOptionPickerTitle.isVisible = true
        collectOnLifecycle(flow { emit(buildOptions()) }) { options ->
            if (options.isEmpty()) {
                binding.tvOptionsEmpty.text = getString(R.string.launcher_scheduled_op_none)
                binding.tvOptionsEmpty.isVisible = true
            }
            SearchableOptionPickerController.attach(binding, options, selectedId = null, resetRow = null) { picked ->
                picked?.let { onPicked(it.id) }
            }
        }
    }

    private suspend fun buildOptions(): List<Option> {
        val operations = scheduledOperationRepository.getAll().first()
        return operations.mapNotNull { operation ->
            val visual = resolveVisual(LauncherCellCommand.ScheduledOp(operation.id)) ?: return@mapNotNull null
            Option(
                id = operation.id.toString(),
                label = visual.label,
                leading = LeadingVisual.IconRes(visual.iconRes ?: R.drawable.ic_schedule),
            )
        }
    }

    private fun onPicked(operationIdText: String) {
        val operationId = operationIdText.toLongOrNull() ?: return
        setFragmentResult(RESULT_KEY, bundleOf(RESULT_OPERATION_ID to operationId))
        dismiss()
    }

    override fun onStart() {
        super.onStart()
        SearchableOptionPickerWindow.apply(dialog, binding)
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
        const val TAG = "LauncherScheduledOpPicker"
        const val RESULT_KEY = "launcher_scheduled_op_picker_result"
        const val RESULT_OPERATION_ID = "result_operation_id"

        fun newInstance(): LauncherScheduledOpPickerDialogFragment = LauncherScheduledOpPickerDialogFragment()
    }
}
