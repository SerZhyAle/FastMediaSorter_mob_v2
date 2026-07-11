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
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper
import com.sza.fastmediasorter.databinding.DialogSearchableOptionPickerBinding
import com.sza.fastmediasorter.domain.usecase.panel.ResolvePanelRouteAvailabilityUseCase
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerController
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.LeadingVisual
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.Option
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Lists our own feature routes from [InternalRouteCatalog], hiding any not compiled into this build
 * and flagging available-but-disabled ones (strategic S0663 §6.1). Returns the chosen route key to
 * the host via a FragmentResult. S0947: renders through the canonical [SearchableOptionPickerController].
 */
@AndroidEntryPoint
class InternalRoutePickerDialogFragment : DialogFragment() {

    @Inject
    lateinit var resolveRouteAvailability: ResolvePanelRouteAvailabilityUseCase

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
        binding.tvOptionPickerTitle.text = getString(R.string.app_launch_panel_picker_feature_title)
        binding.tvOptionPickerTitle.isVisible = true
        collectOnLifecycle(flow { emit(buildOptions()) }) { options ->
            SearchableOptionPickerController.attach(binding, options, selectedId = null, resetRow = null) { picked ->
                picked?.let { onRoutePicked(it.id) }
            }
        }
    }

    private suspend fun buildOptions(): List<Option> {
        val availability = resolveRouteAvailability.all()
        return InternalRouteCatalog.all().mapNotNull { route ->
            val state = availability[route.key] ?: return@mapNotNull null
            if (!state.availableInBuild) return@mapNotNull null
            val label = getString(route.labelRes)
            // Disabled routes show a textual hint (not colour-only) so the off-state is accessible.
            val display = if (!state.enabledAtRuntime) {
                "$label - ${getString(R.string.app_launch_panel_route_disabled_hint)}"
            } else {
                label
            }
            Option(id = route.key, label = display, leading = LeadingVisual.IconRes(route.iconRes))
        }
    }

    private fun onRoutePicked(routeKey: String) {
        setFragmentResult(RESULT_KEY, bundleOf(RESULT_SLOT to slotIndex, RESULT_ROUTE_KEY to routeKey))
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
        const val TAG = "InternalRoutePickerDialog"
        const val RESULT_KEY = "internal_route_picker_result"
        const val RESULT_SLOT = "result_slot"
        const val RESULT_ROUTE_KEY = "result_route_key"

        private const val ARG_SLOT = "arg_slot"

        fun newInstance(slotIndex: Int): InternalRoutePickerDialogFragment =
            InternalRoutePickerDialogFragment().apply { arguments = bundleOf(ARG_SLOT to slotIndex) }
    }
}
