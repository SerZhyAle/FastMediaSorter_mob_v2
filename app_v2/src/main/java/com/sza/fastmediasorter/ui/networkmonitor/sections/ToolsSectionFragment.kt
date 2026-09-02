package com.sza.fastmediasorter.ui.networkmonitor.sections

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.chip.Chip
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.clipboard.copyTextToClipboard
import com.sza.fastmediasorter.databinding.FragmentNetworkMonitorToolsBinding
import com.sza.fastmediasorter.ui.networkmonitor.helpers.renderToolsConsole
import com.sza.fastmediasorter.ui.networkmonitor.helpers.renderToolsProgressLabel
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ToolsSectionFragment : Fragment() {

    private var _binding: FragmentNetworkMonitorToolsBinding? = null
    private val binding
        get() = requireNotNull(_binding) { "Binding is valid only between onCreateView and onDestroyView" }

    private val viewModel: ToolsSectionViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNetworkMonitorToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindActions()
        collectOnLifecycle(viewModel.uiState) { render(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun bindActions() {
        binding.btnToolsPing.setOnClickListener {
            val host = binding.toolsHostInput.text?.toString().orEmpty()
            viewModel.startPing(host, getNetworkLabel())
        }

        binding.btnToolsTraceroute.setOnClickListener {
            val host = binding.toolsHostInput.text?.toString().orEmpty()
            viewModel.startTraceRoute(host, getNetworkLabel())
        }

        binding.btnToolsScanSubnet.setOnClickListener {
            val first = binding.toolsRangeFirst.text?.toString().orEmpty()
            val last = binding.toolsRangeLast.text?.toString().orEmpty()
            viewModel.startSubnetScan(first, last, getNetworkLabel())
        }

        binding.btnToolsCancel.setOnClickListener {
            viewModel.cancelActiveOperation()
        }

        binding.btnToolsClearConsole.setOnClickListener {
            viewModel.clearConsole()
        }

        binding.btnToolsCopyConsole.setOnClickListener {
            copyConsoleToClipboard()
        }
    }

    private fun render(state: ToolsSectionUiState) {
        if (binding.toolsRangeFirst.text.isNullOrBlank() && state.rangeFirst.isNotEmpty()) {
            binding.toolsRangeFirst.setText(state.rangeFirst)
        }
        if (binding.toolsRangeLast.text.isNullOrBlank() && state.rangeLast.isNotEmpty()) {
            binding.toolsRangeLast.setText(state.rangeLast)
        }

        binding.toolsProgressGroup.isVisible = state.isRunning
        val runningOperation = state.runningOperation
        if (state.isRunning && runningOperation != null) {
            binding.toolsProgressLabel.text = requireContext().renderToolsProgressLabel(runningOperation)
        }

        binding.toolsConsoleOutput.text = if (state.consoleLines.isEmpty()) {
            getString(R.string.network_monitor_console_empty)
        } else {
            requireContext().renderToolsConsole(state.consoleLines)
        }

        renderTargetChips(state.targets)
    }

    private fun renderTargetChips(targets: List<String>) {
        binding.toolsTargetChipGroup.removeAllViews()
        for (target in targets) {
            val chip = Chip(requireContext()).apply {
                text = target
                isCloseIconVisible = true
                setOnClickListener {
                    binding.toolsHostInput.setText(target)
                }
                setOnCloseIconClickListener {
                    viewModel.removeTarget(target)
                }
            }
            binding.toolsTargetChipGroup.addView(chip)
        }
    }

    private fun copyConsoleToClipboard() {
        val text = binding.toolsConsoleOutput.text.toString()
        if (text.isNotBlank()) {
            // Android 13+ shows this label in the system copy preview, so it is user-visible text.
            requireContext().copyTextToClipboard(getString(R.string.network_monitor_console_heading), text)
        }
    }

    private fun getNetworkLabel(): String = getString(R.string.network_monitor_section_tools)
}
