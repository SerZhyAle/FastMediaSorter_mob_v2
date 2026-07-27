package com.sza.fastmediasorter.ui.settings.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsStreamsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.StreamDefaultSort
import com.sza.fastmediasorter.domain.model.StreamMediaTypeFilter
import com.sza.fastmediasorter.domain.model.StreamTrackLanguage
import com.sza.fastmediasorter.domain.model.StreamsCatalogRefreshPolicy
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.streams.StreamsActivity
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
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
            viewModel.updateSettings(viewModel.settings.value.copy(enableStreams = isChecked))
        }
        // S1148: opt-in resilient radio buffering profile (start-up cushion + silent reconnects).
        bindSwitch(binding.rowSmartBuffering) { isChecked ->
            viewModel.updateSettings(viewModel.settings.value.copy(streamsSmartBuffering = isChecked))
        }

        // S0659: dropdown entries follow each enum's declaration order so the chosen index maps back to
        // the enum via `entries[index]`. Sort/filter reuse the existing list-screen labels; only the
        // catalog-refresh policy gets settings-specific entries.
        binding.rowDefaultSort.setEntries(
            listOf(
                getString(R.string.streams_sort_name),
                getString(R.string.streams_sort_topic),
                getString(R.string.streams_sort_language),
                getString(R.string.streams_sort_country),
                getString(R.string.streams_sort_recent),
            )
        )
        binding.rowDefaultMediaFilter.setEntries(
            listOf(
                getString(R.string.streams_filter_all),
                getString(R.string.streams_filter_media_audio),
                getString(R.string.streams_filter_media_video),
            )
        )
        binding.rowCatalogRefresh.setEntries(
            listOf(
                getString(R.string.settings_streams_catalog_refresh_manual),
                getString(R.string.settings_streams_catalog_refresh_on_open),
                getString(R.string.settings_streams_catalog_refresh_wifi),
            )
        )
        // S1144: same option list for both language rows; order must match StreamTrackLanguage.entries.
        val languageEntries = listOf(
            getString(R.string.language_default),
            getString(R.string.language_english),
            getString(R.string.language_russian),
            getString(R.string.language_ukrainian),
        )
        binding.rowDefaultAudioLanguage.setEntries(languageEntries)
        binding.rowDefaultSubtitleLanguage.setEntries(languageEntries)

        bindDropdown(binding.rowDefaultSort) {
            viewModel.updateSettings(
                viewModel.settings.value.copy(streamsDefaultSort = StreamDefaultSort.entries[it])
            )
        }
        bindDropdown(binding.rowDefaultMediaFilter) {
            viewModel.updateSettings(
                viewModel.settings.value.copy(streamsDefaultMediaFilter = StreamMediaTypeFilter.entries[it])
            )
        }
        bindDropdown(binding.rowCatalogRefresh) {
            viewModel.updateSettings(
                viewModel.settings.value.copy(streamsCatalogRefreshPolicy = StreamsCatalogRefreshPolicy.entries[it])
            )
        }
        bindDropdown(binding.rowDefaultAudioLanguage) {
            viewModel.updateSettings(
                viewModel.settings.value.copy(streamsDefaultAudioLanguage = StreamTrackLanguage.entries[it])
            )
        }
        bindDropdown(binding.rowDefaultSubtitleLanguage) {
            viewModel.updateSettings(
                viewModel.settings.value.copy(streamsDefaultSubtitleLanguage = StreamTrackLanguage.entries[it])
            )
        }

        binding.btnClearPlayStatuses.setOnClickListener { confirmClearPlayStatuses() }

        collectOnLifecycle(viewModel.settings) { settings: AppSettings ->
            setSwitchChecked(binding.rowEnableStreams, settings.enableStreams)
            setSwitchChecked(binding.rowSmartBuffering, settings.streamsSmartBuffering)
            withSettingsUpdate {
                setDropdownSelection(binding.rowDefaultSort, settings.streamsDefaultSort.ordinal)
                setDropdownSelection(binding.rowDefaultMediaFilter, settings.streamsDefaultMediaFilter.ordinal)
                setDropdownSelection(binding.rowCatalogRefresh, settings.streamsCatalogRefreshPolicy.ordinal)
                setDropdownSelection(binding.rowDefaultAudioLanguage, settings.streamsDefaultAudioLanguage.ordinal)
                setDropdownSelection(
                    binding.rowDefaultSubtitleLanguage,
                    settings.streamsDefaultSubtitleLanguage.ordinal
                )
            }
            // S0578/S0659: the defaults group, clear action and shortcut all follow the master toggle -
            // hidden when Streams is off so the section stays compact while the feature is disabled.
            val sectionVisibility = if (settings.enableStreams) View.VISIBLE else View.GONE
            binding.streamsDefaultsGroup.visibility = sectionVisibility
            binding.btnClearPlayStatuses.visibility = sectionVisibility
            binding.btnStreams.visibility = sectionVisibility
        }
        // Opens the Streams screen to manage sources when the feature is enabled.
        binding.btnStreams.setOnClickListener {
            startActivity(Intent(requireContext(), StreamsActivity::class.java))
        }
    }

    /**
     * S0659: confirm before wiping every channel's OK/FAIL play-status bullet (the channels stay). The
     * ViewModel owns the DB clear; the fragment only confirms and reports the result.
     */
    private fun confirmClearPlayStatuses() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.settings_streams_clear_statuses_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.clearStreamPlayStatuses()
                Toast.makeText(
                    requireContext(),
                    R.string.settings_streams_clear_statuses_done,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
