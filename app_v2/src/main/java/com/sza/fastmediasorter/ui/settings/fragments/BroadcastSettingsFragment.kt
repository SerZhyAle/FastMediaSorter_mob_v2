package com.sza.fastmediasorter.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsBroadcastBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.Locale

/**
 * S2817: Media-tab Broadcast section body. Five configurable preferences read by the capture
 * service at the next session start - stream title, bit rate, port, audio format (sample rate +
 * channels), and the auto-share-screen toggle. Mirrors [StreamsSettingsFragment]'s binding
 * pattern: shared [SettingsViewModel] via activityViewModels, BaseSettingsFragment helpers, and a
 * settings Flow collected on the view lifecycle.
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

        binding.rowBitRate.setEntries(
            BIT_RATES.map { formatBitRate(it) }
        )
        binding.rowAudioFormat.setEntries(
            AUDIO_FORMATS.map { formatAudioFormat(it) }
        )
        binding.rowMicGain.setEntries(
            MIC_GAINS.map { "$it%" }
        )

        binding.rowStreamTitle.setOnCommitListener { value ->
            val title = value.toString().trim()
            if (title.isNotEmpty()) {
                viewModel.updateSettings(viewModel.settings.value.copy(broadcastStreamTitle = title))
            }
        }

        binding.rowPort.setOnCommitListener { value ->
            val port = value.toString().trim().toIntOrNull()
            if (port != null && port in PORT_MIN..PORT_MAX) {
                Timber.d("S3034: broadcast port changed to %d", port)
                viewModel.updateSettings(viewModel.settings.value.copy(broadcastPort = port))
            } else {
                binding.rowPort.text = viewModel.settings.value.broadcastPort.toString()
            }
        }

        bindDropdown(binding.rowBitRate) { index ->
            val bitRate = BIT_RATES.getOrNull(index) ?: return@bindDropdown
            viewModel.updateSettings(viewModel.settings.value.copy(broadcastBitRateBps = bitRate))
        }

        bindDropdown(binding.rowAudioFormat) { index ->
            val format = AUDIO_FORMATS.getOrNull(index) ?: return@bindDropdown
            viewModel.updateSettings(
                viewModel.settings.value.copy(
                    broadcastSampleRateHz = format.first,
                    broadcastChannelCount = format.second,
                )
            )
        }

        bindDropdown(binding.rowMicGain) { index ->
            val gain = MIC_GAINS.getOrNull(index) ?: return@bindDropdown
            viewModel.updateSettings(viewModel.settings.value.copy(broadcastMicGainPercent = gain))
        }

        bindSwitch(binding.rowEnableBroadcasting) { isChecked ->
            Timber.d("S3032: broadcasting enable toggle changed to %b", isChecked)
            viewModel.updateSettings(viewModel.settings.value.copy(enableBroadcasting = isChecked))
            updateBroadcastOptionsVisibility(isChecked)
        }

        bindSwitch(binding.rowAutoOpenShare) { isChecked ->
            viewModel.updateSettings(viewModel.settings.value.copy(broadcastAutoOpenShare = isChecked))
        }

        collectOnLifecycle(viewModel.settings) { settings: AppSettings ->
            updateBroadcastOptionsVisibility(settings.enableBroadcasting)
            if (binding.rowStreamTitle.text.toString() != settings.broadcastStreamTitle) {
                binding.rowStreamTitle.text = settings.broadcastStreamTitle
            }
            if (binding.rowPort.text.toString() != settings.broadcastPort.toString()) {
                binding.rowPort.text = settings.broadcastPort.toString()
            }
            withSettingsUpdate {
                setSwitchChecked(binding.rowEnableBroadcasting, settings.enableBroadcasting)
                setDropdownSelection(
                    binding.rowBitRate,
                    BIT_RATES.indexOf(settings.broadcastBitRateBps).coerceAtLeast(0)
                )
                setDropdownSelection(binding.rowAudioFormat, audioFormatIndex(settings))
                setDropdownSelection(
                    binding.rowMicGain,
                    MIC_GAINS.indexOf(settings.broadcastMicGainPercent).coerceAtLeast(1)
                )
                setSwitchChecked(binding.rowAutoOpenShare, settings.broadcastAutoOpenShare)
            }
        }
    }

    private fun updateBroadcastOptionsVisibility(enabled: Boolean) {
        binding.rowStreamTitle.isVisible = enabled
        binding.broadcastConfigGroup.isVisible = enabled
        binding.rowAutoOpenShare.isVisible = enabled
    }

    private fun formatBitRate(bps: Int): String =
        getString(R.string.unit_bitrate_kbps, (bps / BPS_PER_KBPS).toString())

    private fun formatAudioFormat(format: Pair<Int, Int>): String {
        val khz = String.format(Locale.getDefault(), "%.1f", format.first / HZ_PER_KHZ)
        val channels = getString(
            if (format.second >= STEREO_CHANNEL_COUNT) {
                R.string.settings_broadcast_channels_stereo
            } else {
                R.string.settings_broadcast_channels_mono
            }
        )
        return getString(R.string.settings_broadcast_audio_format_value, khz, channels)
    }

    private fun audioFormatIndex(settings: AppSettings): Int =
        AUDIO_FORMATS.indexOfFirst { (sampleRate, channels) ->
            sampleRate == settings.broadcastSampleRateHz && channels == settings.broadcastChannelCount
        }.coerceAtLeast(0)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val PORT_MIN = 1
        private const val PORT_MAX = 65535
        private const val BPS_PER_KBPS = 1000
        private const val HZ_PER_KHZ = 1000.0
        private const val STEREO_CHANNEL_COUNT = 2

        private val BIT_RATES = intArrayOf(128_000, 192_000, 256_000, 320_000)

        // S3049: microphone digital gain percent options (50% .. 400%).
        private val MIC_GAINS = intArrayOf(50, 100, 150, 200, 300, 400)

        // (sampleRateHz, channelCount) - index maps to the dropdown position.
        private val AUDIO_FORMATS = listOf(
            44_100 to 1,
            44_100 to 2,
            48_000 to 1,
            48_000 to 2,
        )
    }
}
