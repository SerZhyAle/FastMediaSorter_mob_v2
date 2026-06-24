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
import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper
import com.sza.fastmediasorter.databinding.DialogPanelRoutePickerBinding
import com.sza.fastmediasorter.databinding.ItemAppPickerRowBinding
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Lists the curated OS targets from [OsShortcutCatalog] that actually resolve on this device
 * (strategic S0663 §6.3), returning the chosen target key to the host via a FragmentResult. Shares
 * the route-picker layout; sized/centred like [AppPickerDialogFragment].
 */
@AndroidEntryPoint
class OsShortcutPickerDialogFragment : DialogFragment() {

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
        binding.tvRoutePickerTitle.text = getString(R.string.app_launch_panel_picker_os_title)
        binding.rvRouteItems.layoutManager = LinearLayoutManager(requireContext())
        collectOnLifecycle(flow { emit(buildItems()) }) { items ->
            binding.rvRouteItems.adapter = OsItemAdapter(items, ::onTargetPicked)
        }
    }

    // PackageManager resolution runs off the main thread (strategic §3.2 performance constraint).
    private suspend fun buildItems(): List<OsItem> = withContext(Dispatchers.IO) {
        OsShortcutCatalog.available(requireContext()).map { target ->
            OsItem(target.key, target.iconRes, getString(target.labelRes))
        }
    }

    private fun onTargetPicked(item: OsItem) {
        setFragmentResult(RESULT_KEY, bundleOf(RESULT_SLOT to slotIndex, RESULT_TARGET_KEY to item.targetKey))
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

    private data class OsItem(val targetKey: String, val iconRes: Int, val label: String)

    private class OsItemAdapter(
        private val items: List<OsItem>,
        private val onPicked: (OsItem) -> Unit,
    ) : RecyclerView.Adapter<OsItemAdapter.OsViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OsViewHolder {
            val itemBinding = ItemAppPickerRowBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return OsViewHolder(itemBinding)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: OsViewHolder, position: Int) = holder.bind(items[position])

        inner class OsViewHolder(
            private val itemBinding: ItemAppPickerRowBinding,
        ) : RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(item: OsItem) {
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
        const val TAG = "OsShortcutPickerDialog"
        const val RESULT_KEY = "os_shortcut_picker_result"
        const val RESULT_SLOT = "result_slot"
        const val RESULT_TARGET_KEY = "result_target_key"

        private const val ARG_SLOT = "arg_slot"

        fun newInstance(slotIndex: Int): OsShortcutPickerDialogFragment =
            OsShortcutPickerDialogFragment().apply { arguments = bundleOf(ARG_SLOT to slotIndex) }
    }
}
