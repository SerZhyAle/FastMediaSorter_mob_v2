package com.sza.fastmediasorter.ui.settings.helpers

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.databinding.FragmentSettingsDestinationsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.PairedWatchStatus
import com.sza.fastmediasorter.ui.common.support.SupportIntentFactory
import com.sza.fastmediasorter.ui.dialog.TooltipDialog
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.wear.WearCompanionActivity
import timber.log.Timber

/**
 * S1883: owns the Wear OS group on the Operations tab - the master checkbox, the explanation, the link
 * to the install guide, and the button that opens the companion window.
 *
 * The checkbox means "the companion is on", not merely "show its entry in the programs list". Before
 * this ticket the settings button read only the build capability while the programs entry read the
 * capability and the switch together, so the two disagreed whenever the switch was off. Both now read
 * the same pair, which is why [render] hides the button rather than greying it: a build without the
 * watch bridge hides the whole card, and a switched-off companion has no window to open.
 */
class OperationsWearGroupManager(
    private val binding: FragmentSettingsDestinationsBinding,
    private val fragment: Fragment,
    private val viewModel: SettingsViewModel,
    private val mediaCapabilities: MediaCapabilities,
    private val isUpdatingFromSettings: () -> Boolean,
    // S1885: the paired-watch state lives on WearSyncViewModel, not on the settings view model,
    // so this manager asks for a refresh through a callback rather than holding a second view model.
    private val requestWatchStatusRefresh: () -> Unit,
) {

    /** True when this build carries the watch bridge at all. */
    val isAvailableInBuild: Boolean get() = mediaCapabilities.supportsWearCompanion

    /**
     * S1885: last companion state this manager rendered. Null until the first render, so the first
     * pass with the companion already on still asks the bridge once.
     */
    private var lastCompanionEnabled: Boolean? = null

    fun setup() {
        if (!isAvailableInBuild) {
            // The whole card goes, not just its header and container: an empty elevated card would
            // still be drawn, and on lite/photos/vr the group must be absent rather than empty.
            binding.cardWear.isVisible = false
            return
        }
        binding.rowEnableWearCompanion.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(enableWearCompanion = isChecked))
        }
        binding.btnWearCompanion.setOnClickListener {
            fragment.startActivity(WearCompanionActivity.createIntent(fragment.requireContext()))
        }
        binding.iconHelpWearCompanion.setOnClickListener {
            TooltipDialog.show(
                fragment.requireContext(),
                R.string.tooltip_wear_companion_title,
                R.string.tooltip_wear_companion_message,
            )
        }
        binding.btnWearInstallGuide.setOnClickListener {
            openInstallGuide()
        }
    }

    fun render(settings: AppSettings) {
        if (!isAvailableInBuild) {
            return
        }
        Timber.d("S1883: wear group render enabled=%s", settings.enableWearCompanion)
        if (binding.rowEnableWearCompanion.isChecked != settings.enableWearCompanion) {
            binding.rowEnableWearCompanion.setCheckedSilently(settings.enableWearCompanion)
        }
        // The explanation and the link stay visible in both states - they are what tells the reader
        // what the checkbox above them switches on.
        binding.containerWearCompanion.isVisible = settings.enableWearCompanion
        // S1885: the status row follows the same switch. A row reporting "no watch on the link"
        // under a switched-off companion would read as a fault rather than as a disabled feature.
        binding.textWearPairedWatch.isVisible = settings.enableWearCompanion
        // render() runs on every settings emission, so an unguarded call here would hit the bridge
        // each time any unrelated switch on this tab moved. Ask only when the companion has just
        // become enabled, which is what strategic 5.3 specifies.
        val companionJustEnabled = settings.enableWearCompanion && lastCompanionEnabled != true
        lastCompanionEnabled = settings.enableWearCompanion
        if (companionJustEnabled) {
            requestWatchStatusRefresh()
        }
    }

    /** S1885: one row, three states - see [PairedWatchStatus] for why absence is a single state. */
    fun renderPairedWatch(status: PairedWatchStatus) {
        if (!isAvailableInBuild) {
            return
        }
        Timber.d("S1885: paired watch row -> %s", status)
        binding.textWearPairedWatch.text = when (status) {
            is PairedWatchStatus.Unknown ->
                fragment.getString(R.string.settings_wear_paired_watch_checking)
            is PairedWatchStatus.Connected ->
                fragment.getString(R.string.settings_wear_paired_watch_connected, status.name)
            is PairedWatchStatus.NotConnected ->
                fragment.getString(R.string.settings_wear_paired_watch_not_connected)
        }
    }

    private fun openInstallGuide() {
        val url = SupportIntentFactory.wearInstallGuideUrl(fragment.requireContext())
        Timber.d("S1883: install guide opened")
        try {
            fragment.startActivity(SupportIntentFactory.openUrl(url))
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No browser to open the Wear install guide")
            Toast.makeText(
                fragment.requireContext(),
                fragment.getString(R.string.settings_no_browser_for_docs),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
