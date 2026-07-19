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
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
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

    // S0404: hosts other than the panel editor pass their own key, so several hosts can share one
    // FragmentManager without receiving each other's results.
    private var requestKey: String = RESULT_KEY

    // S0404: an optional gate so a resource gadget (playlist/preview) lists only resources that can hold
    // its media; null keeps the panel editor's list unfiltered.
    private var mediaTypeFilter: MediaType? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
        slotIndex = requireArguments().getInt(ARG_SLOT, 0)
        requestKey = requireArguments().getString(ARG_REQUEST_KEY) ?: RESULT_KEY
        mediaTypeFilter = requireArguments().getString(ARG_MEDIA_TYPE_FILTER)
            ?.let { name -> MediaType.entries.firstOrNull { it.name == name } }
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
        resourceRepository.getAllResourcesSync()
            .filter { matchesMediaFilter(it) }
            .map { resource ->
                Option(
                    id = resource.id.toString(),
                    label = resource.name,
                    leading = LeadingVisual.IconRes(resourceIconRes(resource.type)),
                )
            }

    /**
     * S0404: permissive by design. The test is `AUDIO in supportedMediaTypes`, not `isAudioOnly()`, so a
     * mixed video+audio library still qualifies for a playlist gadget; an all-files resource carries
     * every type and always passes.
     */
    private fun matchesMediaFilter(resource: MediaResource): Boolean {
        val filter = mediaTypeFilter ?: return true
        return resource.allFiles || filter in resource.supportedMediaTypes
    }

    // S0890: single source of truth for the type -> icon table.
    private fun resourceIconRes(type: ResourceType): Int = ResourceTypeIconMap.iconFor(type)

    private fun onResourcePicked(resourceId: String) {
        setFragmentResult(
            requestKey,
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
        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val ARG_MEDIA_TYPE_FILTER = "arg_media_type_filter"

        fun newInstance(slotIndex: Int): ResourcePickerDialogFragment =
            ResourcePickerDialogFragment().apply { arguments = bundleOf(ARG_SLOT to slotIndex) }

        /**
         * S0404: host factory with no panel-slot semantics - the result arrives on [requestKey], so
         * a second host on the same FragmentManager never sees the panel editor's picks. The bundle
         * still carries RESULT_SLOT (0); a non-panel host ignores it.
         */
        fun newInstance(requestKey: String): ResourcePickerDialogFragment =
            ResourcePickerDialogFragment().apply { arguments = bundleOf(ARG_REQUEST_KEY to requestKey) }

        /**
         * S0404: host factory that also filters the list to resources holding [mediaTypeFilter] (null =
         * no filter). Used by the launcher's resource-gadget flow so a playlist gadget offers only
         * audio-capable resources.
         */
        fun newInstance(requestKey: String, mediaTypeFilter: MediaType?): ResourcePickerDialogFragment =
            ResourcePickerDialogFragment().apply {
                arguments = bundleOf(
                    ARG_REQUEST_KEY to requestKey,
                    ARG_MEDIA_TYPE_FILTER to mediaTypeFilter?.name,
                )
            }
    }
}
