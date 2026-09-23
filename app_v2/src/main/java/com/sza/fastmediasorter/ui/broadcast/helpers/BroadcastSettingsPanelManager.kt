package com.sza.fastmediasorter.ui.broadcast.helpers

import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.databinding.FragmentSettingsBroadcastBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.common.widget.SettingsDropdownRow
import com.sza.fastmediasorter.ui.settings.fragments.BroadcastSettingsOptions
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the broadcast preferences hosted on the unified broadcast screen.
 *
 * The screen inflates the very layout the settings screen uses, so the two editors offer the same
 * rows in the same order; this class is the settings-screen fragment's binding half, without the
 * fragment (S3060).
 */
@Singleton
class BroadcastSettingsPanelManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {

    // A write lands back through the settings Flow, and re-rendering a row fires its listener again;
    // without this the panel would write the value it has just read, once per emission.
    private var renderingFromSettings = false

    // The last committed port, so a rejected edit can be put back without reading the Flow again.
    private var currentPort: Int = BroadcastSettingsOptions.PORT_MIN

    // The most recent settings write, joined by awaitPendingWrites before an action reads the store.
    private var pendingWrite: Job? = null

    fun bind(activity: AppCompatActivity, panel: FragmentSettingsBroadcastBinding) {
        panel.rowBitRate.setEntries(BroadcastSettingsOptions.bitRateLabels(activity))
        panel.rowAudioFormat.setEntries(BroadcastSettingsOptions.audioFormatLabels(activity))
        panel.rowMicGain.setEntries(BroadcastSettingsOptions.micGainLabels())

        panel.rowStreamTitle.setOnCommitListener { value ->
            val title = value.toString().trim()
            if (title.isNotEmpty()) {
                update(activity) { it.copy(broadcast = it.broadcast.copy(streamTitle = title)) }
            }
        }

        panel.rowPort.setOnCommitListener { value ->
            val port = value.toString().trim().toIntOrNull()
            if (port != null && BroadcastSettingsOptions.isValidPort(port)) {
                update(activity) { it.copy(broadcast = it.broadcast.copy(port = port)) }
            } else {
                renderPort(panel, currentPort)
            }
        }

        bindDropdown(panel.rowBitRate) { index ->
            val bitRate = BroadcastSettingsOptions.bitRatesBps.getOrNull(index) ?: return@bindDropdown
            update(activity) { it.copy(broadcast = it.broadcast.copy(bitRateBps = bitRate)) }
        }

        bindDropdown(panel.rowAudioFormat) { index ->
            val format = BroadcastSettingsOptions.audioFormats.getOrNull(index) ?: return@bindDropdown
            update(activity) {
                it.copy(
                    broadcast = it.broadcast.copy(
                        sampleRateHz = format.first,
                        channelCount = format.second
                    )
                )
            }
        }

        bindDropdown(panel.rowMicGain) { index ->
            val gain = BroadcastSettingsOptions.micGainPercents.getOrNull(index) ?: return@bindDropdown
            update(activity) { it.copy(broadcast = it.broadcast.copy(micGainPercent = gain)) }
        }

        panel.rowEnableBroadcasting.setOnCheckedChangeListener { checked ->
            if (!renderingFromSettings) update(activity) { it.copy(enableBroadcasting = checked) }
        }

        panel.rowFeedbackGuard.setOnCheckedChangeListener { checked ->
            if (!renderingFromSettings) {
                update(activity) { it.copy(broadcast = it.broadcast.copy(feedbackGuardEnabled = checked)) }
            }
        }

        panel.rowAutoOpenShare.setOnCheckedChangeListener { checked ->
            if (!renderingFromSettings) {
                update(activity) { it.copy(broadcast = it.broadcast.copy(autoOpenShare = checked)) }
            }
        }

        activity.collectOnLifecycle(settingsRepository.getSettings()) { settings ->
            render(panel, settings)
        }
    }

    /**
     * Commits the text rows the user may still be editing. A tap on a screen action leaves the field
     * focused, so without this the action reads the stored value while the panel shows the typed one
     * (S3234). The dropdown and switch rows commit on selection and need nothing here.
     */
    fun flushPending(panel: FragmentSettingsBroadcastBinding) {
        panel.rowPort.commitPending()
        panel.rowStreamTitle.commitPending()
    }

    /**
     * Waits for the write a commit started. Committing only hands the value to a coroutine, so a caller
     * that reads the stored settings right after [flushPending] still reads the previous port - measured
     * on a device, where the process died between the two and the typed port was lost (S3234).
     */
    suspend fun awaitPendingWrites() {
        pendingWrite?.join()
    }

    private fun render(panel: FragmentSettingsBroadcastBinding, settings: AppSettings) {
        currentPort = settings.broadcast.port
        renderingFromSettings = true
        panel.rowStreamTitle.isVisible = settings.enableBroadcasting
        panel.broadcastConfigGroup.isVisible = settings.enableBroadcasting
        panel.rowFeedbackGuard.isVisible = settings.enableBroadcasting
        panel.rowAutoOpenShare.isVisible = settings.enableBroadcasting
        if (panel.rowStreamTitle.text.toString() != settings.broadcast.streamTitle) {
            panel.rowStreamTitle.text = settings.broadcast.streamTitle
        }
        renderPort(panel, settings.broadcast.port)
        if (panel.rowEnableBroadcasting.isChecked != settings.enableBroadcasting) {
            panel.rowEnableBroadcasting.setCheckedSilently(settings.enableBroadcasting)
        }
        if (panel.rowFeedbackGuard.isChecked != settings.broadcast.feedbackGuardEnabled) {
            panel.rowFeedbackGuard.setCheckedSilently(settings.broadcast.feedbackGuardEnabled)
        }
        if (panel.rowAutoOpenShare.isChecked != settings.broadcast.autoOpenShare) {
            panel.rowAutoOpenShare.setCheckedSilently(settings.broadcast.autoOpenShare)
        }
        setSelection(panel.rowBitRate, BroadcastSettingsOptions.bitRateIndex(settings))
        setSelection(panel.rowAudioFormat, BroadcastSettingsOptions.audioFormatIndex(settings))
        setSelection(panel.rowMicGain, BroadcastSettingsOptions.micGainIndex(settings))
        renderingFromSettings = false
    }

    private fun renderPort(panel: FragmentSettingsBroadcastBinding, port: Int) {
        if (panel.rowPort.text.toString() != port.toString()) {
            panel.rowPort.text = port.toString()
        }
    }

    private fun setSelection(row: SettingsDropdownRow, index: Int) {
        if (row.getSelectedIndex() != index) {
            row.setSelection(index)
        }
    }

    private fun bindDropdown(row: SettingsDropdownRow, onUserSelected: (Int) -> Unit) {
        row.setOnItemSelectedListener { position ->
            if (!renderingFromSettings) {
                onUserSelected(position)
            }
        }
    }

    private fun update(activity: AppCompatActivity, transform: (AppSettings) -> AppSettings) {
        pendingWrite = activity.lifecycleScope.launch {
            settingsRepository.updateSettings { current -> transform(current) }
        }
    }
}
