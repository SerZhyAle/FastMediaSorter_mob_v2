package com.sza.fastmediasorter.ui.networkmonitor.sections

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentNetworkMonitorResourceSpeedBinding
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.Locale

@AndroidEntryPoint
class ResourceSpeedSectionFragment : Fragment() {

    private var _binding: FragmentNetworkMonitorResourceSpeedBinding? = null
    private val binding
        get() = requireNotNull(_binding) { "Binding is valid only between onCreateView and onDestroyView" }

    private val viewModel: ResourceSpeedSectionViewModel by viewModels()

    private var meteredDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNetworkMonitorResourceSpeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindActions()
        collectOnLifecycle(viewModel.uiState) { render(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // showBoundTo already dismisses the dialog with the view lifecycle, but the reference lives on the
        // fragment, which outlives the view: a recreated view would see a stale non-null dialog and never
        // rebuild one while the question is still open.
        meteredDialog = null
        _binding = null
    }

    private fun bindActions() {
        binding.btnSpeedStartTest.setOnClickListener {
            viewModel.startSpeedTest(getNetworkLabel())
        }

        binding.btnSpeedCancel.setOnClickListener {
            viewModel.cancelTest()
        }
    }

    private fun render(state: ResourceSpeedUiState) {
        setupTargetPicker(state)

        binding.speedProgressGroup.isVisible = state.isRunning
        val status = state.status
        if (state.isRunning && status != null) {
            binding.speedProgressLabel.text = getString(status.toLabelRes())
            val percent = (state.progressFraction * PERCENT_SCALE).toInt()
            binding.speedProgress.progress = percent
        }

        binding.speedDownValue.text = state.downMbps?.let { formatMbps(it) } ?: VALUE_ABSENT

        binding.speedUpValue.text = when {
            state.upMbps != null -> formatMbps(state.upMbps)
            !state.isWritable && state.selectedResourceId != null ->
                getString(R.string.network_monitor_read_only_resource)
            else -> VALUE_ABSENT
        }

        val resultRes = state.error?.toMessageRes() ?: status?.toLabelRes()
        binding.speedResultStatus.text = resultRes?.let { getString(it) }.orEmpty()
        binding.speedResultStatus.isVisible = resultRes != null

        renderMeteredWarning(state.isMeteredWarning)
    }

    /**
     * A builder dialog rather than an inflated layout, as in the neighbouring Internet section: it inherits
     * the named confirm and cancel button styles from `materialAlertDialogTheme`, which is what keeps this
     * pair from drifting into a one-off.
     */
    private fun renderMeteredWarning(isWarning: Boolean) {
        if (!isWarning) {
            meteredDialog?.dismiss()
            meteredDialog = null
            return
        }
        // Rebuilding on every emission would tear the open dialog down mid-answer, so an existing one stands.
        if (meteredDialog != null) return

        Timber.d("S2348: raising the metered confirmation dialog")
        meteredDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.network_monitor_warn_metered_title)
            .setMessage(R.string.network_monitor_warn_metered_message)
            .setPositiveButton(R.string.network_monitor_action_continue) { _, _ ->
                Timber.d("S2348: user accepted the metered cost, restarting with allowMetered = true")
                viewModel.startSpeedTest(getNetworkLabel(), allowMetered = true)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> viewModel.dismissMeteredWarning() }
            .setOnCancelListener { viewModel.dismissMeteredWarning() }
            .showBoundTo(this)
    }

    private fun formatMbps(value: Double): String = getString(
        R.string.network_monitor_value_mbps_decimal,
        String.format(Locale.getDefault(), "%.2f", value)
    )

    private fun setupTargetPicker(state: ResourceSpeedUiState) {
        val options = mutableListOf(getString(R.string.network_monitor_target_internet))
        options.addAll(state.resources.map { it.name })

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, options)
        binding.speedTargetPicker.setAdapter(adapter)

        binding.speedTargetPicker.setOnItemClickListener { _, _, position, _ ->
            if (position == 0) {
                viewModel.selectResource(null)
            } else {
                val res = state.resources.getOrNull(position - 1)
                viewModel.selectResource(res?.id)
            }
        }
    }

    private fun getNetworkLabel(): String = getString(R.string.network_monitor_section_resource_speed)

    private companion object {
        // A typographic placeholder, not a phrase - it reads the same in every locale.
        const val VALUE_ABSENT = "-"

        // The bar counts to 100; the state carries the same progress as a 0..1 fraction.
        const val PERCENT_SCALE = 100
    }
}

private fun ResourceSpeedStatus.toLabelRes(): Int = when (this) {
    ResourceSpeedStatus.CHECKING -> R.string.network_monitor_speed_status_checking
    ResourceSpeedStatus.MEASURING -> R.string.network_monitor_running_speed_test
    ResourceSpeedStatus.COMPLETE -> R.string.network_monitor_speed_status_complete
    ResourceSpeedStatus.STOPPED -> R.string.network_monitor_stopped
}

private fun ResourceSpeedError.toMessageRes(): Int = when (this) {
    ResourceSpeedError.UNREACHABLE -> R.string.network_monitor_speed_unreachable
    ResourceSpeedError.RESOURCE_MISSING -> R.string.network_monitor_result_resource_missing
    ResourceSpeedError.MEASUREMENT_FAILED -> R.string.network_monitor_result_speed_test_failed
    ResourceSpeedError.METERED_NETWORK -> R.string.network_monitor_result_speed_test_metered
}
