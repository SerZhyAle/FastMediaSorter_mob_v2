package com.sza.fastmediasorter.ui.applaunchpanel.edit

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper
import com.sza.fastmediasorter.databinding.DialogPanelRoutePickerBinding
import com.sza.fastmediasorter.databinding.ItemAppPickerRowBinding
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Lists the configured resources (one tile pins one specific resource, strategic S0663 §5.1.C) and
 * returns the chosen resource id to the host via a FragmentResult. Lists through the domain
 * [ResourceRepository] (mirroring how [AppPickerDialogFragment] injects a use case) rather than the
 * DAO/entity, keeping UI off the data layer (CLAUDE.md Architecture). Shares the route-picker layout.
 */
@AndroidEntryPoint
class ResourcePickerDialogFragment : DialogFragment() {

    @Inject
    lateinit var resourceRepository: ResourceRepository

    private var _binding: DialogPanelRoutePickerBinding? = null
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
        _binding = DialogPanelRoutePickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvRoutePickerTitle.text = getString(R.string.app_launch_panel_picker_resource_title)
        binding.rvRouteItems.layoutManager = LinearLayoutManager(requireContext())
        collectOnLifecycle(flow { emit(buildItems()) }) { items ->
            binding.rvRouteItems.adapter = ResourceItemAdapter(items, ::onResourcePicked)
        }
    }

    private suspend fun buildItems(): List<ResourceItem> =
        resourceRepository.getAllResourcesSync().map { resource ->
            ResourceItem(resource.id, resourceIconRes(resource.type), resource.name)
        }

    private fun resourceIconRes(type: ResourceType): Int = when (type) {
        ResourceType.LOCAL -> R.drawable.ic_resource_local
        ResourceType.SMB -> R.drawable.ic_resource_smb
        ResourceType.SFTP -> R.drawable.ic_resource_sftp
        ResourceType.FTP -> R.drawable.ic_resource_ftp
        ResourceType.CLOUD -> R.drawable.ic_resource_cloud
        ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM -> R.drawable.ic_cast
    }

    private fun onResourcePicked(item: ResourceItem) {
        setFragmentResult(RESULT_KEY, bundleOf(RESULT_SLOT to slotIndex, RESULT_RESOURCE_ID to item.resourceId))
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

    private data class ResourceItem(val resourceId: Long, val iconRes: Int, val label: String)

    private class ResourceItemAdapter(
        private val items: List<ResourceItem>,
        private val onPicked: (ResourceItem) -> Unit,
    ) : RecyclerView.Adapter<ResourceItemAdapter.ResourceViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResourceViewHolder {
            val itemBinding = ItemAppPickerRowBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ResourceViewHolder(itemBinding)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ResourceViewHolder, position: Int) = holder.bind(items[position])

        inner class ResourceViewHolder(
            private val itemBinding: ItemAppPickerRowBinding,
        ) : RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(item: ResourceItem) {
                itemBinding.ivAppIcon.setImageDrawable(
                    ContextCompat.getDrawable(itemBinding.root.context, item.iconRes)
                )
                itemBinding.tvAppLabel.text = item.label
                itemBinding.rowApp.contentDescription = item.label
                itemBinding.rowApp.setOnClickListener { onPicked(item) }
            }
        }
    }

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
