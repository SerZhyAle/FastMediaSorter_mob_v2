package com.sza.fastmediasorter.ui.networkmonitor.sections

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentNetworkMonitorResourceSpeedBinding
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class ResourceSpeedSectionFragment : Fragment() {

    private var _binding: FragmentNetworkMonitorResourceSpeedBinding? = null
    private val binding
        get() = requireNotNull(_binding) { "Binding is valid only between onCreateView and onDestroyView" }

    private val viewModel: ResourceSpeedSectionViewModel by viewModels()

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
        if (state.isRunning && state.statusText != null) {
            binding.speedProgressLabel.text = state.statusText
            val percent = (state.progressFraction * 100).toInt()
            binding.speedProgress.progress = percent
        }

        binding.speedDownValue.text = if (state.downMbps != null) {
            String.format(Locale.getDefault(), "%.2f Mbps", state.downMbps)
        } else {
            "-"
        }

        binding.speedUpValue.text = when {
            state.upMbps != null -> String.format(Locale.getDefault(), "%.2f Mbps", state.upMbps)
            !state.isWritable && state.selectedResourceId != null -> getString(R.string.network_monitor_read_only_resource)
            else -> "-"
        }

        binding.speedResultStatus.text = when {
            state.error != null -> state.error
            state.statusText != null -> state.statusText
            else -> ""
        }
        binding.speedResultStatus.isVisible = !state.error.isNullOrBlank() || !state.statusText.isNullOrBlank()
    }

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
}
