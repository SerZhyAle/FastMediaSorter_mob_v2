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
import com.sza.fastmediasorter.core.panel.ResourceTypeIconMap
import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper
import com.sza.fastmediasorter.databinding.DialogSearchableOptionPickerBinding
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerController
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.LeadingVisual
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.Option
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Lists the configured resources (one tile pins one specific resource, strategic S0663 §5.1.C) and
 * returns the chosen resource id to the host via a FragmentResult. Lists through the domain
 * [ResourceRepository], keeping UI off the data layer (CLAUDE.md Architecture). S0947: renders through
 * the canonical [SearchableOptionPickerController].
 */
@AndroidEntryPoint
class ResourcePickerDialogFragment : DialogFragment() {

    @Inject
    lateinit var resourceRepository: ResourceRepository

    private var _binding: DialogSearchableOptionPickerBinding? = null
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
        _binding = DialogSearchableOptionPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvOptionPickerTitle.text = getString(R.string.app_launch_panel_picker_resource_title)
        binding.tvOptionPickerTitle.isVisible = true
        collectOnLifecycle(flow { emit(buildOptions()) }) { options ->
            SearchableOptionPickerController.attach(binding, options, selectedId = null, resetRow = null) { picked ->
                picked?.let { onResourcePicked(it.id) }
            }
        }
    }

    private suspend fun buildOptions(): List<Option> =
        resourceRepository.getAllResourcesSync().map { resource ->
            Option(
                id = resource.id.toString(),
                label = resource.name,
                leading = LeadingVisual.IconRes(resourceIconRes(resource.type)),
            )
        }

    // S0890: single source of truth for the type -> icon table.
    private fun resourceIconRes(type: ResourceType): Int = ResourceTypeIconMap.iconFor(type)

    private fun onResourcePicked(resourceId: String) {
        setFragmentResult(
            RESULT_KEY,
            bundleOf(RESULT_SLOT to slotIndex, RESULT_RESOURCE_ID to resourceId.toLong()),
        )
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
        const val TAG = "ResourcePickerDialog"
        const val RESULT_KEY = "resource_picker_result"
        const val RESULT_SLOT = "result_slot"
        const val RESULT_RESOURCE_ID = "result_resource_id"

        private const val ARG_SLOT = "arg_slot"

        fun newInstance(slotIndex: Int): ResourcePickerDialogFragment =
            ResourcePickerDialogFragment().apply { arguments = bundleOf(ARG_SLOT to slotIndex) }
    }
}
