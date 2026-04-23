package com.sza.fastmediasorter.ui.main.helpers

import android.content.res.Configuration
import com.google.android.material.tabs.TabLayout
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.main.ResourceTab

/**
 * Owns the resource-type TabLayout: tab construction (with flavor-aware Cloud tab),
 * width-aware mode/gravity, and bidirectional mapping between tab index and [ResourceTab].
 *
 * Extracted from MainActivity to keep the activity below the 1000-line cap.
 */
class MainResourceTabsManager(
    private val tabLayout: TabLayout,
    private val configuration: Configuration,
    private val onTabSelected: (ResourceTab) -> Unit,
    private val onFavoritesReselected: () -> Unit,
    private val getActiveTab: () -> ResourceTab,
    private val getPreviousTab: () -> ResourceTab?
) {

    /** Build base tabs (ALL, Local, SMB, S/FTP, optional Cloud) and apply width-aware mode. */
    fun createTabs() {
        // Cloud tab is hidden in lite flavor (SUPPORT_CLOUD = false)
        tabLayout.addTab(tabLayout.newTab().apply {
            setText(R.string.tab_all_resources)
            setIcon(R.drawable.ic_view_list)
        })
        tabLayout.addTab(tabLayout.newTab().apply {
            setText(R.string.tab_local_resources)
            setIcon(R.drawable.ic_resource_local)
        })
        tabLayout.addTab(tabLayout.newTab().apply {
            setText(R.string.tab_smb_resources)
            setIcon(R.drawable.ic_resource_smb)
        })
        tabLayout.addTab(tabLayout.newTab().apply {
            setText(R.string.tab_ftp_sftp_resources)
            setIcon(R.drawable.ic_resource_ftp)
        })
        if (BuildConfig.SUPPORT_CLOUD) {
            tabLayout.addTab(tabLayout.newTab().apply {
                setText(R.string.tab_cloud_resources)
                setIcon(R.drawable.ic_resource_cloud)
            })
        }

        // Restore active tab from state
        tabLayout.getTabAt(getTabIndexForResourceTab(getActiveTab()))?.select()

        // Width-aware mode: scrollable on narrow phones to avoid truncated labels
        val screenWidthDp = configuration.screenWidthDp
        if (screenWidthDp < 480) {
            tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
            tabLayout.tabGravity = TabLayout.GRAVITY_START
        } else {
            tabLayout.tabMode = TabLayout.MODE_FIXED
            tabLayout.tabGravity = TabLayout.GRAVITY_FILL
        }
    }

    /** Install the OnTabSelectedListener. Call once during initial setup. */
    fun setupListener() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                onTabSelected(getResourceTabForIndex(tab?.position ?: 0))
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit

            override fun onTabReselected(tab: TabLayout.Tab?) {
                // Reopen Browse when Favorites tab tapped again
                val favoritesTabIndex = if (BuildConfig.SUPPORT_CLOUD) 5 else 4
                if (tab?.position == favoritesTabIndex) {
                    onFavoritesReselected()
                    tabLayout.post {
                        // Restore previous tab
                        val previous = getPreviousTab() ?: ResourceTab.ALL
                        val target = tabLayout.getTabAt(getTabIndexForResourceTab(previous))
                        if (target != null && !target.isSelected) {
                            target.select()
                        }
                    }
                }
            }
        })
    }

    /** Account for hidden Cloud tab in lite flavor. */
    fun getTabIndexForResourceTab(tab: ResourceTab): Int = when (tab) {
        ResourceTab.ALL -> 0
        ResourceTab.LOCAL -> 1
        ResourceTab.SMB -> 2
        ResourceTab.FTP_SFTP -> 3
        ResourceTab.CLOUD -> if (BuildConfig.SUPPORT_CLOUD) 4 else 3
        ResourceTab.FAVORITES -> 0 // Should not happen — defaults to ALL
    }

    /** Account for hidden Cloud tab in lite flavor. */
    fun getResourceTabForIndex(index: Int): ResourceTab = if (BuildConfig.SUPPORT_CLOUD) {
        when (index) {
            0 -> ResourceTab.ALL
            1 -> ResourceTab.LOCAL
            2 -> ResourceTab.SMB
            3 -> ResourceTab.FTP_SFTP
            4 -> ResourceTab.CLOUD
            else -> ResourceTab.ALL
        }
    } else {
        when (index) {
            0 -> ResourceTab.ALL
            1 -> ResourceTab.LOCAL
            2 -> ResourceTab.SMB
            3 -> ResourceTab.FTP_SFTP
            else -> ResourceTab.ALL
        }
    }
}
