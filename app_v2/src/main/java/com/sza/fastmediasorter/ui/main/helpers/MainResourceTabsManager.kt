package com.sza.fastmediasorter.ui.main.helpers

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.tabs.TabLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate
import com.sza.fastmediasorter.core.capability.RemoteSourceId
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.main.ResourceTab
import kotlinx.coroutines.CoroutineScope

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
@Suppress("LongParameterList") // View-manager: views + per-tab callbacks + the collapse-chip notify (S1443).
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
    private val getPreviousTab: () -> ResourceTab?,
    // S1443: forwarded to the collapse manager, whose chip may be placed in the command bar.
    private val onChipVisibilityChanged: () -> Unit = {}
) {

    /** Drives the expanded(tabs) <-> collapsed(strip) state; mirrors the player copy/move panels. */
    private val collapseManager = MainResourceTabsCollapseManager(
        tabLayout = tabLayout,
        collapsedStrip = collapsedStrip,
        isPanelAvailable = { gate.anyRemoteEnabled() },
        settingsRepository = settingsRepository,
        scope = scope,
        onChipVisibilityChanged = onChipVisibilityChanged
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

        // S1049: the portrait-only fixed-grid bucket (main_resource_tabs_fixed_grid) always goes scrollable +
        // start-aligned so app:tabMinWidth/tabMaxWidth (main_panel_tab_min_width, 2x the shared item module)
        // are respected exactly instead of being overridden by fill-stretch. Landscape/w600dp leave this bool
        // false and keep the pre-existing width-aware fixed/fill split below, unchanged.
        val fixedGrid = tabLayout.resources.getBoolean(R.bool.main_resource_tabs_fixed_grid)
        val screenWidthDp = configuration.screenWidthDp
        if (fixedGrid || screenWidthDp < NARROW_TAB_LAYOUT_MAX_WIDTH_DP) {
            tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
            tabLayout.tabGravity = TabLayout.GRAVITY_START
        } else {
            tabLayout.tabMode = TabLayout.MODE_FIXED
            tabLayout.tabGravity = TabLayout.GRAVITY_FILL
        }
        applyLeadingCellAccent(fixedGrid)

        // S0781: (re)bind per-tab long-press to collapse + apply the persisted collapsed state.
        collapseManager.onTabsRebuilt()
    }

    /**
     * S1068: in the portrait fixed-grid bucket the first tab ("All") is the accented leading cell -
     * one base module (48dp) wider than the 2-module (96dp) tab, i.e. 108dp = 225% - so its right edge
     * lands on the same grid boundary as the command bar and panels while the strip starts flush at x=0.
     * The remaining tabs stay a uniform 96dp. Outside portrait (fixed/fill mode) the explicit widths are
     * cleared back to WRAP_CONTENT so the tabs stretch to full width as before (S1049); MainActivity uses
     * android:configChanges (no re-inflate on rotation) so this reset must run to undo a prior portrait pin.
     */
    private fun applyLeadingCellAccent(fixedGrid: Boolean) {
        val strip = tabLayout.getChildAt(0) as? ViewGroup ?: return
        val firstWidth = tabLayout.resources.getDimensionPixelSize(R.dimen.main_panel_first_tab_width)
        val restWidth = tabLayout.resources.getDimensionPixelSize(R.dimen.main_panel_tab_min_width)
        for (i in 0 until strip.childCount) {
            val tabView = strip.getChildAt(i)
            if (fixedGrid) {
                val target = if (i == 0) firstWidth else restWidth
                val lp = tabView.layoutParams
                if (lp.width != target) {
                    lp.width = target
                    tabView.layoutParams = lp
                }
                tabView.minimumWidth = target
            } else {
                // Land/wide: normally the mode switch to FIXED already reset each tab LP to width=0
                // (fill/stretch), so leave that untouched. Only undo a leftover portrait pin (width>0),
                // which survives the rare rotate-into-narrow-landscape path where the mode stays
                // SCROLLABLE (MainActivity reuses this view across rotations via configChanges).
                val lp = tabView.layoutParams
                if (lp.width > 0) {
                    lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    tabView.layoutParams = lp
                }
                tabView.minimumWidth = 0
            }
        }
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

    companion object {
        /** Below this width, resource tabs stay scrollable regardless of the fixed-grid bucket (S1049). */
        private const val NARROW_TAB_LAYOUT_MAX_WIDTH_DP = 480
    }
}
