package com.sza.fastmediasorter.ui.main.helpers

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
        // start-aligned so the explicit per-tab widths below are respected instead of being overridden by
        // fill-stretch. S1659: those widths are now computed from the strip width rather than taken from
        // app:tabMinWidth/tabMaxWidth, which stay as the upper bound. Landscape/w600dp leave this bool
        // false and keep the pre-existing width-aware fixed/fill split below, unchanged.
        val fixedGrid = tabLayout.resources.getBoolean(R.bool.main_resource_tabs_fixed_grid)
        // S1659: read the width at use time, never from a Configuration captured at construction. Measured
        // on device: after a landscape-to-portrait turn a captured instance still reported the landscape
        // width, so the division sized the tabs for 853dp and the row overflowed the 384dp portrait again.
        val screenWidthDp = tabLayout.resources.configuration.screenWidthDp
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
     * S1659: in the portrait fixed-grid bucket the tab widths are a quotient of the strip's own width,
     * not constants. Five tabs at the S1068 defaults (108dp + 4x96dp = 492dp) overflow every phone
     * portrait (360-411dp), so the last tab - Cloud - was pushed off-screen and read as a removed
     * feature. The row now divides the available width by the tab count and keeps the S1068 leading-cell
     * accent as a ratio, so a sixth tab needs no new constant. Outside portrait (fixed/fill mode) the
     * explicit widths are cleared back to WRAP_CONTENT so the tabs stretch to full width as before
     * (S1049); MainActivity uses android:configChanges (no re-inflate on rotation) so this reset must run
     * to undo a prior portrait pin.
     */
    private fun applyLeadingCellAccent(fixedGrid: Boolean) {
        val strip = tabLayout.getChildAt(0) as? ViewGroup ?: return
        if (!fixedGrid) {
            clearPinnedTabWidths(strip)
            return
        }
        val restWidth = resolveRestTabWidth(strip.childCount)
        val maxFirstWidth = tabLayout.resources.getDimensionPixelSize(R.dimen.main_panel_first_tab_width)
        val firstWidth = (restWidth * firstTabAccentRatio()).toInt().coerceAtMost(maxFirstWidth)
        for (i in 0 until strip.childCount) {
            val tabView = strip.getChildAt(i)
            val target = if (i == 0) firstWidth else restWidth
            val lp = tabView.layoutParams
            if (lp.width != target) {
                lp.width = target
                tabView.layoutParams = lp
            }
            tabView.minimumWidth = target
        }
    }

    /**
     * S1659: the available width divided by the tab count, clamped between the shared 48dp touch module
     * and the S1068 two-module default. The upper clamp keeps a wide screen on the shared grid instead of
     * stretching a tab past its module. The lower clamp is unreachable on any supported screen - five tabs
     * stay above it down to 246dp and the narrowest supported portrait is 320dp (strategic 6.1) - and
     * exists so an unforeseen width degrades to the pre-existing scrollable strip rather than to a tab too
     * narrow to hit.
     */
    private fun resolveRestTabWidth(tabCount: Int): Int {
        val resources = tabLayout.resources
        val maxWidth = resources.getDimensionPixelSize(R.dimen.main_panel_tab_min_width)
        if (tabCount <= 0) {
            return maxWidth
        }
        val available = resources.configuration.screenWidthDp * resources.displayMetrics.density -
            tabLayout.paddingStart - tabLayout.paddingEnd
        val share = available / (tabCount - 1 + firstTabAccentRatio())
        val minWidth = resources.getDimensionPixelSize(R.dimen.main_panel_item_min_width)
        return share.toInt().coerceIn(minWidth, maxWidth)
    }

    /**
     * S1068's leading-cell accent expressed as a ratio (108/96) rather than a fixed +12dp, so it survives
     * the S1659 division: the first tab stays that multiple of the others at any width, and on a screen
     * wide enough for the defaults both widths land back on the dimens the other top panels align to.
     */
    private fun firstTabAccentRatio(): Float {
        val resources = tabLayout.resources
        return resources.getDimensionPixelSize(R.dimen.main_panel_first_tab_width).toFloat() /
            resources.getDimensionPixelSize(R.dimen.main_panel_tab_min_width)
    }

    /**
     * Land/wide: normally the mode switch to FIXED already reset each tab LP to width=0 (fill/stretch), so
     * leave that untouched. Only undo a leftover portrait pin (width>0), which survives the rare
     * rotate-into-narrow-landscape path where the mode stays SCROLLABLE (MainActivity reuses this view
     * across rotations via configChanges).
     */
    private fun clearPinnedTabWidths(strip: ViewGroup) {
        for (i in 0 until strip.childCount) {
            val tabView = strip.getChildAt(i)
            val lp = tabView.layoutParams
            if (lp.width > 0) {
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                tabView.layoutParams = lp
            }
            tabView.minimumWidth = 0
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
