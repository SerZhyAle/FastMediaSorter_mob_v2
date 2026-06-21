package com.sza.fastmediasorter.ui.settings.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.sza.fastmediasorter.databinding.FragmentSettingsStreamsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.streams.StreamsActivity
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * S0575: Media-tab "Streams" section hosting the single feature master toggle. Mirrors
 * [OtherMediaSettingsFragment]'s toggle binding - shared [SettingsViewModel] via activityViewModels,
 * the BaseSettingsFragment bindSwitch helper, and a settings Flow collected on the view lifecycle.
 */
@AndroidEntryPoint
class StreamsSettingsFragment : BaseSettingsFragment() {

    private var _binding: FragmentSettingsStreamsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsStreamsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindSwitch(binding.rowEnableStreams) { isChecked ->
            Timber.d("S0575: settings Streams toggle -> %b", isChecked)
            viewModel.updateSettings(viewModel.settings.value.copy(enableStreams = isChecked))
        }
        collectOnLifecycle(viewModel.settings) { settings: AppSettings ->
            setSwitchChecked(binding.rowEnableStreams, settings.enableStreams)
            // S0578: the shortcut follows the master toggle - hidden when Streams is off so the
            // feature is absent everywhere while disabled, present only once the user opts in.
            binding.btnStreams.visibility = if (settings.enableStreams) View.VISIBLE else View.GONE
        }
        // Opens the Streams screen to manage sources when the feature is enabled.
        binding.btnStreams.setOnClickListener {
            Timber.d("S0578: open Streams screen from Media > Streams section")
            startActivity(Intent(requireContext(), StreamsActivity::class.java))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
