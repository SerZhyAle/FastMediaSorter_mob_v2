package com.sza.fastmediasorter.ui.main.helpers

import android.content.res.Configuration
import android.view.View
import androidx.core.view.isVisible
import com.google.android.material.tabs.TabLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate
import com.sza.fastmediasorter.core.capability.RemoteSourceId
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.main.ResourceTab
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber

/**
 * Owns the resource-type TabLayout: gate-aware tab construction, width-aware mode/gravity, and
 * bidirectional mapping between tab index and [ResourceTab] derived from the actually-built set.
 *
 * S0391: only the remote sources the availability gate currently enables get a tab. When no remote
 * source is enabled the whole strip is hidden, since only ALL + Local would remain and Local is a
 * subset of ALL.
 *
 * Extracted from MainActivity to keep the activity below the 1000-line cap.
 */
class MainResourceTabsManager(
    private val tabLayout: TabLayout,
    private val collapsedStrip: View,
    private val configuration: Configuration,
    private val gate: RemoteSourceAvailabilityGate,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    private val onTabSelected: (ResourceTab) -> Unit,
    private val onFavoritesReselected: () -> Unit,
    private val getActiveTab: () -> ResourceTab,
    private val getPreviousTab: () -> ResourceTab?
) {

    /** Drives the expanded(tabs) <-> collapsed(strip) state; mirrors the player copy/move panels. */
    private val collapseManager = MainResourceTabsCollapseManager(
        tabLayout = tabLayout,
        collapsedStrip = collapsedStrip,
        isPanelAvailable = { gate.anyRemoteEnabled() },
        settingsRepository = settingsRepository,
        scope = scope
    )

    /** Tabs in display order, rebuilt by [createTabs]; the single source of truth for index<->tab. */
    private val builtTabs = mutableListOf<ResourceTab>()

    /** Build tabs for the currently-enabled sources and apply width-aware mode. Idempotent. */
    fun createTabs() {
        tabLayout.removeAllTabs()
        builtTabs.clear()

        addTab(ResourceTab.ALL, R.string.tab_all_resources, R.drawable.ic_view_list)
        addTab(ResourceTab.LOCAL, R.string.tab_local_resources, R.drawable.ic_resource_local)
        if (gate.isEnabled(RemoteSourceId.SMB)) {
            addTab(ResourceTab.SMB, R.string.tab_smb_resources, R.drawable.ic_resource_smb)
        }
        if (gate.isEnabled(RemoteSourceId.SFTP) || gate.isEnabled(RemoteSourceId.FTP)) {
            addTab(ResourceTab.FTP_SFTP, R.string.tab_ftp_sftp_resources, R.drawable.ic_resource_ftp)
        }
        if (gate.anyCloudEnabled()) {
            addTab(ResourceTab.CLOUD, R.string.tab_cloud_resources, R.drawable.ic_resource_cloud)
        }

        // Vanish rule: with no remote source enabled only ALL + Local remain, so hide the strip.
        tabLayout.isVisible = gate.anyRemoteEnabled()

        // Restore active tab (falls back to ALL when the active source is no longer built).
        tabLayout.getTabAt(getTabIndexForResourceTab(getActiveTab()))?.select()

        // Width-aware mode: scrollable on narrow phones to avoid truncated labels.
        val screenWidthDp = configuration.screenWidthDp
        if (screenWidthDp < 480) {
            tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
            tabLayout.tabGravity = TabLayout.GRAVITY_START
        } else {
            tabLayout.tabMode = TabLayout.MODE_FIXED
            tabLayout.tabGravity = TabLayout.GRAVITY_FILL
        }

        // S0781: (re)bind per-tab long-press to collapse + apply the persisted collapsed state.
        collapseManager.onTabsRebuilt()
    }

    private fun addTab(tab: ResourceTab, textRes: Int, iconRes: Int) {
        tabLayout.addTab(
            tabLayout.newTab().apply {
                setText(textRes)
                setIcon(iconRes)
            }
        )
        builtTabs.add(tab)
    }

    /** Install the OnTabSelectedListener. Call once during initial setup. */
    fun setupListener() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                onTabSelected(getResourceTabForIndex(tab?.position ?: 0))
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit

            override fun onTabReselected(tab: TabLayout.Tab?) {
                // Favorites is an action-only button, never a built tab, so indexOf returns -1 and
                // this branch does not fire today; kept for parity should Favorites become a tab.
                if (tab?.position == builtTabs.indexOf(ResourceTab.FAVORITES)) {
                    onFavoritesReselected()
                    tabLayout.post {
                        val previous = getPreviousTab() ?: ResourceTab.ALL
                        val target = tabLayout.getTabAt(getTabIndexForResourceTab(previous))
                        if (target != null && !target.isSelected) {
                            target.select()
                        }
                    }
                }
            }
        })
        // S0781: wire the collapsed-strip tap + load persisted state. Once, like the tab listener.
        collapseManager.install()
    }

    /** Index of [tab] in the built set; ALL (0) when the tab is not currently built. */
    fun getTabIndexForResourceTab(tab: ResourceTab): Int =
        builtTabs.indexOf(tab).takeIf { it >= 0 } ?: 0

    /** Tab at [index] in the built set; ALL when out of range. */
    fun getResourceTabForIndex(index: Int): ResourceTab =
        builtTabs.getOrElse(index) { ResourceTab.ALL }
}
