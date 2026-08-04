package com.sza.fastmediasorter.ui.main.helpers

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.compat.MultiWindowCapabilityDetector
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.streams.StreamTitleFormatter
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * S0831/S0770: per-item context-menu actions for the main-window programs (S0755) and streams (S0756)
 * panels, plus the shared "open in a separate window" primitives. Extracted verbatim from MainActivity
 * (which exceeded the 1500-LOC limit) so the panel item menus have one home. Behaviour-preserving.
 *
 * [currentSettings] reads MainActivity's freshest settings snapshot (mutated by its settings collector),
 * so a "Remove"/"Disable" write bases on the current state without this manager holding its own copy.
 */
class MainPanelItemActionsManager(
    private val activity: AppCompatActivity,
    private val settingsRepository: SettingsRepository,
    // S1195: an operation rather than the use case, so the host Activity routes it through its ViewModel.
    private val unpinStreamSource: (id: String) -> Unit,
    private val currentSettings: () -> AppSettings?,
    private val resourceVrCinema: ResourceVrCinemaLaunchManager,
) {

    // S0963 (Pillar 2): open a resource in the immersive VR browser. Delegates to the XR-gated
    // launch manager; the item is only visible when [isVrCinemaAvailable] is true.
    fun openResourceInVrCinema(resource: MediaResource) = resourceVrCinema.launch(resource)

    /** S0963: XR availability mirror gating the resource "Open in VR Cinema" entry. */
    fun isVrCinemaAvailable(): Boolean = resourceVrCinema.isAvailable

    // S0293 Phase 08: launch BrowseActivity for the given resource as a new task so the
    // platform places it in a separate window (Quest 3 panel / DeX desktop / ChromeOS).
    fun openResourceInNewWindow(resourceId: Long) {
        val windowId = UUID.randomUUID().toString()
        val intent = Intent(activity, BrowseActivity::class.java).apply {
            putExtra(BrowseActivity.EXTRA_RESOURCE_ID, resourceId)
            putExtra(BrowseActivity.EXTRA_WINDOW_ID, windowId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        activity.startActivity(intent)
    }

    /** Multi-window available (persisted setting OR runtime capability) - gates "Open in new window". */
    fun isNewWindowAvailable(): Boolean =
        currentSettings()?.allowSeparateWindow == true ||
            MultiWindowCapabilityDetector.isMultiWindowActiveNow(activity)

    /** Launch an activity intent in a separate window (same flags as [openResourceInNewWindow]). */
    fun launchInNewWindow(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        activity.startActivity(intent)
    }

    /** S0770: confirm, then disable the program's toggle; the settings collector rebuilds the panel. */
    fun confirmRemoveProgram(titleRes: Int, apply: (AppSettings) -> AppSettings) {
        if (activity.isFinishing || activity.isDestroyed) return
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.panel_remove_title)
            .setMessage(activity.getString(R.string.panel_remove_program_message, activity.getString(titleRes)))
            .setPositiveButton(R.string.remove_action) { _, _ ->
                val current = currentSettings() ?: return@setPositiveButton
                activity.lifecycleScope.launch { settingsRepository.updateSettings(apply(current)) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** S0779: turn off the Streams master toggle from the panel's entry menu; the settings collector then
     *  rebuilds the panels (streamsEnabledChanged) and hides the streams panel. */
    fun disableStreamsFromPanel() {
        val current = currentSettings() ?: return
        activity.lifecycleScope.launch { settingsRepository.updateSettings(current.copy(enableStreams = false)) }
    }

    /** S0782: hide only the main-window streams panel from its entry menu. Streams stays enabled, so the
     *  settings collector rebuilds the panels (streamsPanelChanged) and the Streams entry returns to the
     *  programs panel/menu (gate.streams ignores this flag). Re-enable via Settings -> Streams. */
    fun hideStreamsPanelFromPanel() {
        val current = currentSettings() ?: return
        activity.lifecycleScope.launch {
            settingsRepository.updateSettings(current.copy(showStreamsPanelInMainWindow = false))
        }
    }

    /** S0807: hide the main-window programs panel from its header menu. The settings collector rebuilds
     *  the panels (programsPanelChanged), which drops the panel and restores the top command-bar
     *  three-dots button (refreshMainWindowDropdownMenuVisibility). Re-enable there or via Settings. */
    fun hideProgramsPanelFromPanel() {
        val current = currentSettings() ?: return
        activity.lifecycleScope.launch {
            settingsRepository.updateSettings(current.copy(showProgramsPanelInMainWindow = false))
        }
    }

    /** S0770: confirm, then unpin the channel; the pinned-sources flow drops it from the streams panel. */
    fun confirmRemoveChannel(channel: StreamSourceEntity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val name = StreamTitleFormatter.display(channel.title)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.panel_remove_title)
            .setMessage(activity.getString(R.string.panel_remove_channel_message, name))
            .setPositiveButton(R.string.remove_action) { _, _ ->
                unpinStreamSource(channel.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
