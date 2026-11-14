package com.sza.fastmediasorter.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.databinding.FragmentSettingsAboutBinding
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.settings.helpers.AboutResourceAvailability
import com.sza.fastmediasorter.ui.settings.helpers.AboutResourceLaunchManager
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/** S4621: the About page, the last top-level settings destination (SETTINGS-NAVIGATION 2.1 section 3). */
@AndroidEntryPoint
class AboutSettingsFragment : BaseSettingsFragment() {

    private var _binding: FragmentSettingsAboutBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var capabilityAvailability: CapabilityAvailability

    private val viewModel: SettingsViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.d("S4621: About page view created")
        val availability = AboutResourceAvailability(
            isCompanionAvailable = viewModel.isCompanionImportAvailable,
            isExtensionsAvailable = capabilityAvailability.isExtensionsScreenAvailable(requireContext()),
        )
        AboutResourceLaunchManager(this, availability).bind(binding.root, binding.tvVersionInfo)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
