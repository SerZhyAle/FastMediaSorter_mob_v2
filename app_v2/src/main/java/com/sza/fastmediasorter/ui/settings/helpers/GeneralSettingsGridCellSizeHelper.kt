package com.sza.fastmediasorter.ui.settings.helpers

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.ResourceGridCellSize
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Handles the "Resource grid cell size" dropdown in
 * [com.sza.fastmediasorter.ui.settings.fragments.GeneralSettingsFragment] (S1285).
 *
 * Pattern mirrors [GeneralSettingsPrefetchHelper]: entry order follows the enum, selection is by
 * ordinal, and the observer sets the row behind a guard.
 */
class GeneralSettingsGridCellSizeHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val viewModel: SettingsViewModel,
    private val fragment: Fragment
) {
    // Without this the observer's setSelection re-enters the selection listener and persists a value
    // the user never picked, which then re-emits settings and loops.
    private var isUpdatingSpinner = false

    fun setup() {
        val labels = ResourceGridCellSize.values().map { cellSizeLabel(it) as CharSequence }
        binding.rowResourceGridCellSize.setEntries(labels)
        binding.rowResourceGridCellSize.setOnItemSelectedListener { position ->
            if (isUpdatingSpinner) return@setOnItemSelectedListener
            val selected = ResourceGridCellSize.values()[position]
            Timber.d("GeneralSettingsGridCellSizeHelper: resourceGridCellSize -> %s", selected)
            Timber.d("S1285: cell size chosen in settings -> %s", selected)
            saveSettings { it.copy(resourceGridCellSize = selected) }
        }
    }

    fun updateFromSettings(settings: AppSettings) {
        if (isUpdatingSpinner) return
        isUpdatingSpinner = true
        binding.rowResourceGridCellSize.setSelection(settings.resourceGridCellSize.ordinal)
        isUpdatingSpinner = false
    }

    private fun cellSizeLabel(size: ResourceGridCellSize): String = when (size) {
        ResourceGridCellSize.SMALL -> fragment.getString(R.string.pref_resource_grid_cell_size_small)
        ResourceGridCellSize.MEDIUM -> fragment.getString(R.string.pref_resource_grid_cell_size_medium)
        ResourceGridCellSize.LARGE -> fragment.getString(R.string.pref_resource_grid_cell_size_large)
    }

    private fun saveSettings(transform: (AppSettings) -> AppSettings) {
        fragment.lifecycleScope.launch {
            val current = viewModel.settings.first()
            viewModel.updateSettings(transform(current))
        }
    }
}
