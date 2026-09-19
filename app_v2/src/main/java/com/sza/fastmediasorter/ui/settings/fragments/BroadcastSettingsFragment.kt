package com.sza.fastmediasorter.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.sza.fastmediasorter.databinding.FragmentSettingsBroadcastBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.BroadcastSettings
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint

/**
 * S2817: Media-tab Broadcast section body. Five configurable preferences read by the capture
 * service at the next session start - stream title, bit rate, port, audio format (sample rate +
 * channels), and the auto-share-screen toggle. Mirrors [StreamsSettingsFragment]'s binding
 * pattern: shared [SettingsViewModel] via activityViewModels, BaseSettingsFragment helpers, and a
 * settings Flow collected on the view lifecycle. The option lists and captions live in
 * [BroadcastSettingsOptions], shared with the panel the broadcast screen hosts (S3060).
 */
@AndroidEntryPoint
class BroadcastSettingsFragment : BaseSettingsFragment() {

    private var _binding: FragmentSettingsBroadcastBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBroadcastBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rowBitRate.setEntries(BroadcastSettingsOptions.bitRateLabels(requireContext()))
        binding.rowAudioFormat.setEntries(BroadcastSettingsOptions.audioFormatLabels(requireContext()))
        binding.rowMicGain.setEntries(BroadcastSettingsOptions.micGainLabels())

        binding.rowStreamTitle.setOnCommitListener { value ->
            val title = value.toString().trim()
            if (title.isNotEmpty()) {
                updateBroadcast { copy(streamTitle = title) }
            }
        }

        binding.rowPort.setOnCommitListener { value ->
            val port = value.toString().trim().toIntOrNull()
            if (port != null && BroadcastSettingsOptions.isValidPort(port)) {
                updateBroadcast { copy(port = port) }
            } else {
                binding.rowPort.text = viewModel.settings.value.broadcast.port.toString()
            }
        }

        bindDropdown(binding.rowBitRate) { index ->
            val bitRate = BroadcastSettingsOptions.bitRatesBps.getOrNull(index) ?: return@bindDropdown
            updateBroadcast { copy(bitRateBps = bitRate) }
        }

        bindDropdown(binding.rowAudioFormat) { index ->
            val format = BroadcastSettingsOptions.audioFormats.getOrNull(index) ?: return@bindDropdown
            updateBroadcast { copy(sampleRateHz = format.first, channelCount = format.second) }
        }

        bindDropdown(binding.rowMicGain) { index ->
            val gain = BroadcastSettingsOptions.micGainPercents.getOrNull(index) ?: return@bindDropdown
            updateBroadcast { copy(micGainPercent = gain) }
        }

        bindSwitch(binding.rowEnableBroadcasting) { isChecked ->
            viewModel.updateSettings(viewModel.settings.value.copy(enableBroadcasting = isChecked))
            updateBroadcastOptionsVisibility(isChecked)
        }

        bindSwitch(binding.rowAutoOpenShare) { isChecked ->
            updateBroadcast { copy(autoOpenShare = isChecked) }
        }

        collectOnLifecycle(viewModel.settings) { settings: AppSettings ->
            updateBroadcastOptionsVisibility(settings.enableBroadcasting)
            if (binding.rowStreamTitle.text.toString() != settings.broadcast.streamTitle) {
                binding.rowStreamTitle.text = settings.broadcast.streamTitle
            }
            if (binding.rowPort.text.toString() != settings.broadcast.port.toString()) {
                binding.rowPort.text = settings.broadcast.port.toString()
            }
            withSettingsUpdate {
                setSwitchChecked(binding.rowEnableBroadcasting, settings.enableBroadcasting)
                setDropdownSelection(binding.rowBitRate, BroadcastSettingsOptions.bitRateIndex(settings))
                setDropdownSelection(
                    binding.rowAudioFormat,
                    BroadcastSettingsOptions.audioFormatIndex(settings)
                )
                setDropdownSelection(binding.rowMicGain, BroadcastSettingsOptions.micGainIndex(settings))
                setSwitchChecked(binding.rowAutoOpenShare, settings.broadcast.autoOpenShare)
            }
        }
    }

    private fun updateBroadcast(transform: BroadcastSettings.() -> BroadcastSettings) {
        val current = viewModel.settings.value
        val updated = current.broadcast.transform()
        viewModel.updateSettings(current.copy(broadcast = updated))
    }

    private fun updateBroadcastOptionsVisibility(enabled: Boolean) {
        binding.rowStreamTitle.isVisible = enabled
        binding.broadcastConfigGroup.isVisible = enabled
        binding.rowAutoOpenShare.isVisible = enabled
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
