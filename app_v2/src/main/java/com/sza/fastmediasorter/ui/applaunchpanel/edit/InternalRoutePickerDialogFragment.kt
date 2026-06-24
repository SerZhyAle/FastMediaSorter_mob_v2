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
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper
import com.sza.fastmediasorter.databinding.DialogPanelRoutePickerBinding
import com.sza.fastmediasorter.databinding.ItemAppPickerRowBinding
import com.sza.fastmediasorter.domain.usecase.panel.ResolvePanelRouteAvailabilityUseCase
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Lists our own feature routes from [InternalRouteCatalog], hiding any not compiled into this build
 * and flagging available-but-disabled ones (strategic S0663 §6.1). Returns the chosen route key to
 * the host via a FragmentResult. Sized/centred like [AppPickerDialogFragment].
 */
@AndroidEntryPoint
class InternalRoutePickerDialogFragment : DialogFragment() {

    @Inject
    lateinit var resolveRouteAvailability: ResolvePanelRouteAvailabilityUseCase

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
        binding.tvRoutePickerTitle.text =
            getString(com.sza.fastmediasorter.R.string.app_launch_panel_picker_feature_title)
        binding.rvRouteItems.layoutManager = LinearLayoutManager(requireContext())
        // One-shot availability resolve wrapped as a flow so it is collected lifecycle-safely.
        collectOnLifecycle(flow { emit(buildItems()) }) { items ->
            binding.rvRouteItems.adapter = RouteItemAdapter(items, ::onRoutePicked)
        }
    }

    private suspend fun buildItems(): List<RouteItem> {
        val availability = resolveRouteAvailability.all()
        return InternalRouteCatalog.all().mapNotNull { route ->
            val state = availability[route.key] ?: return@mapNotNull null
            if (!state.availableInBuild) return@mapNotNull null
            val label = getString(route.labelRes)
            val disabledHint = if (!state.enabledAtRuntime) {
                getString(com.sza.fastmediasorter.R.string.app_launch_panel_route_disabled_hint)
            } else {
                null
            }
            RouteItem(route.key, route.iconRes, label, disabledHint)
        }
    }

    private fun onRoutePicked(item: RouteItem) {
        setFragmentResult(RESULT_KEY, bundleOf(RESULT_SLOT to slotIndex, RESULT_ROUTE_KEY to item.routeKey))
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

    /** One selectable feature route. [disabledHint] non-null when compiled-in but turned off (§6.1). */
    private data class RouteItem(
        val routeKey: String,
        val iconRes: Int,
        val label: String,
        val disabledHint: String?,
    )

    private class RouteItemAdapter(
        private val items: List<RouteItem>,
        private val onPicked: (RouteItem) -> Unit,
    ) : RecyclerView.Adapter<RouteItemAdapter.RouteViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
            val itemBinding = ItemAppPickerRowBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return RouteViewHolder(itemBinding)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: RouteViewHolder, position: Int) = holder.bind(items[position])

        inner class RouteViewHolder(
            private val itemBinding: ItemAppPickerRowBinding,
        ) : RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(item: RouteItem) {
                itemBinding.ivAppIcon.setImageDrawable(
                    ContextCompat.getDrawable(itemBinding.root.context, item.iconRes)
                )
                // Disabled routes show a textual hint (not colour-only) so the off-state is accessible.
                itemBinding.tvAppLabel.text =
                    item.disabledHint?.let { "${item.label} - $it" } ?: item.label
                itemBinding.rowApp.contentDescription = itemBinding.tvAppLabel.text
                itemBinding.rowApp.setOnClickListener { onPicked(item) }
            }
        }
    }

    companion object {
        const val TAG = "InternalRoutePickerDialog"
        const val RESULT_KEY = "internal_route_picker_result"
        const val RESULT_SLOT = "result_slot"
        const val RESULT_ROUTE_KEY = "result_route_key"

        private const val ARG_SLOT = "arg_slot"

        fun newInstance(slotIndex: Int): InternalRoutePickerDialogFragment =
            InternalRoutePickerDialogFragment().apply { arguments = bundleOf(ARG_SLOT to slotIndex) }
    }
}
