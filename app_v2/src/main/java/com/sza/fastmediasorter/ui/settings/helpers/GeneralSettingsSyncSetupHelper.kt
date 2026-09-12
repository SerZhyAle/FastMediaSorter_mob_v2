package com.sza.fastmediasorter.ui.settings.helpers

import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import com.sza.fastmediasorter.R
import timber.log.Timber
import kotlin.reflect.KMutableProperty0

/**
 * S2601: the background-sync section - its three toggles, the interval field and the manual sync
 * button.
 *
 * Its own class rather than another `setupXxx` in [GeneralSettingsViewSetupHelper], which stood over
 * detekt's LargeClass threshold. [isUpdatingSpinner] is passed as the host's own property reference,
 * never copied: a copy would give this class a second flag, and a listener would then fire during a
 * programmatic value set that the host believes it has suppressed.
 */
class GeneralSettingsSyncSetupHelper(
    private val hostContext: GeneralSettingsHostContext,
    private val isUpdatingSpinner: KMutableProperty0<Boolean>,
) {
    private val binding get() = hostContext.binding
    private val viewModel get() = hostContext.viewModel
    private val fragment get() = hostContext.fragment

    fun setup() {
        binding.rowEnableBackgroundSync.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingSpinner.get()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(enableBackgroundSync = isChecked))
        }
        binding.rowEnableThumbnailPreload.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingSpinner.get()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(enableThumbnailPreload = isChecked))
            binding.layoutThumbnailPreloadWifiOnly.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        binding.rowThumbnailPreloadWifiOnly.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingSpinner.get()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(thumbnailPreloadWifiOnly = isChecked))
        }
        setupIntervalRow()
        binding.btnSyncNow.setOnClickListener {
            if (viewModel.manualNetworkSyncState.value.inProgress) {
                viewModel.cancelManualNetworkSync()
            } else {
                viewModel.startManualNetworkSync()
            }
        }
    }

    private fun setupIntervalRow() {
        val syncAdapter = ArrayAdapter(
            fragment.requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            INTERVAL_OPTIONS_MINUTES
        )
        binding.actvSyncInterval?.let { syncIntervalView ->
            syncIntervalView.setAdapter(syncAdapter)
            val currentMinutes = viewModel.settings.value.backgroundSyncIntervalHours * MINUTES_PER_HOUR
            syncIntervalView.setText(fragment.getString(R.string.number_format, currentMinutes), false)
            syncIntervalView.setOnItemClickListener { _, _, position, _ ->
                if (isUpdatingSpinner.get()) return@setOnItemClickListener
                val minutes = INTERVAL_OPTIONS_MINUTES[position].toInt()
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(backgroundSyncIntervalHours = toHours(minutes)))
            }
            syncIntervalView.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus && !isUpdatingSpinner.get()) {
                    val minutes = syncIntervalView.text.toString().toIntOrNull()
                    if (minutes != null && minutes >= MIN_INTERVAL_MINUTES) {
                        val current = viewModel.settings.value
                        viewModel.updateSettings(current.copy(backgroundSyncIntervalHours = toHours(minutes)))
                    } else {
                        val previous = viewModel.settings.value.backgroundSyncIntervalHours * MINUTES_PER_HOUR
                        syncIntervalView.setText(fragment.getString(R.string.number_format, previous), false)
                        Toast.makeText(
                            fragment.requireContext(),
                            R.string.slide_interval_error,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    // The stored unit is hours, so the two sub-hour options round up to one rather than to zero.
    private fun toHours(minutes: Int): Int =
        (minutes / MINUTES_PER_HOUR.toDouble()).toInt().coerceAtLeast(1)

    private companion object {
        val INTERVAL_OPTIONS_MINUTES = arrayOf("5", "15", "60", "120", "300")
        const val MINUTES_PER_HOUR = 60
        const val MIN_INTERVAL_MINUTES = 5
    }
}
