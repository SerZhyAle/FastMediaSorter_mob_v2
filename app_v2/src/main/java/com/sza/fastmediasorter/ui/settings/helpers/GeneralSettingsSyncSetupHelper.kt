package com.sza.fastmediasorter.ui.settings.helpers

import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import com.sza.fastmediasorter.R
import timber.log.Timber
import kotlin.math.roundToInt
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
            syncIntervalView.setOnItemClickListener { _, _, _, _ -> commitPendingInterval() }
            syncIntervalView.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) commitPendingInterval()
            }
            syncIntervalView.setOnEditorActionListener { view, actionId, event ->
                // A hardware ENTER still arrives as IME_NULL carrying the key event, which TextView
                // delivers on ACTION_UP - so the key event is accepted beside the actionDone the
                // layout's imeOptions asks the keyboard for (measured on the S21+, 2026-09-18).
                val isCommit = actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_NEXT ||
                    actionId == EditorInfo.IME_ACTION_GO ||
                    event?.keyCode == KeyEvent.KEYCODE_ENTER
                if (isCommit) {
                    commitPendingInterval()
                    view.clearFocus()
                }
                isCommit
            }
        }
    }

    /**
     * Commits whatever stands in the interval field. The field is freely typeable but used to persist
     * only on focus loss, so leaving the settings screen threw the typed number away (S3295, the
     * defect S3292 fixed for the icon-size field). The store holds hours, so a typed value is snapped
     * to the nearest hour inside 1..24 and the snapped minute count is written back - the field then
     * always shows what the store holds, including for the sub-hour presets 5 and 15.
     */
    fun commitPendingInterval() {
        if (isUpdatingSpinner.get()) return
        val syncIntervalView = binding.actvSyncInterval ?: return
        val typed = syncIntervalView.text.toString().toIntOrNull()
        if (typed == null || typed < MIN_INTERVAL_MINUTES) {
            rejectTypedInterval(syncIntervalView, typed)
        } else {
            applyTypedInterval(syncIntervalView, typed)
        }
    }

    private fun rejectTypedInterval(syncIntervalView: AutoCompleteTextView, typed: Int?) {
        Timber.d("Sync interval rejected, typed=$typed")
        val storedMinutes = viewModel.settings.value.backgroundSyncIntervalHours * MINUTES_PER_HOUR
        syncIntervalView.setText(fragment.getString(R.string.number_format, storedMinutes), false)
        Toast.makeText(fragment.requireContext(), R.string.slide_interval_error, Toast.LENGTH_SHORT).show()
    }

    private fun applyTypedInterval(syncIntervalView: AutoCompleteTextView, typed: Int) {
        val current = viewModel.settings.value
        val hours = snapToHours(typed)
        if (hours != current.backgroundSyncIntervalHours) {
            viewModel.updateSettings(current.copy(backgroundSyncIntervalHours = hours))
        }
        val storedMinutes = hours * MINUTES_PER_HOUR
        if (typed != storedMinutes) {
            syncIntervalView.setText(fragment.getString(R.string.number_format, storedMinutes), false)
        }
    }

    // The stored unit is hours, so a sub-hour value rounds up to one rather than down to zero.
    private fun snapToHours(minutes: Int): Int =
        (minutes / MINUTES_PER_HOUR.toDouble()).roundToInt().coerceIn(MIN_INTERVAL_HOURS, MAX_INTERVAL_HOURS)

    private companion object {
        val INTERVAL_OPTIONS_MINUTES = arrayOf("5", "15", "60", "120", "300")
        const val MINUTES_PER_HOUR = 60
        const val MIN_INTERVAL_MINUTES = 5
        const val MIN_INTERVAL_HOURS = 1
        const val MAX_INTERVAL_HOURS = 24
    }
}
