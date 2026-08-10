package com.sza.fastmediasorter.ui.networkmonitor.helpers

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.sza.fastmediasorter.ui.networkmonitor.NetworkMonitorSection
import com.sza.fastmediasorter.ui.networkmonitor.history.NetworkHistoryFragment
import com.sza.fastmediasorter.ui.networkmonitor.sections.BluetoothSectionFragment
import com.sza.fastmediasorter.ui.networkmonitor.sections.GnssSectionFragment
import com.sza.fastmediasorter.ui.networkmonitor.sections.InternetSectionFragment
import com.sza.fastmediasorter.ui.networkmonitor.sections.MobileSectionFragment
import com.sza.fastmediasorter.ui.networkmonitor.sections.WifiSectionFragment
import com.sza.fastmediasorter.ui.networkmonitor.summary.NetworkMonitorSummaryFragment

/**
 * S1433: what a screen asks when it wants another section opened.
 *
 * The Activity implements it and the Fragments call it, so a tile never reaches for its host's concrete
 * type - which is what lets Phase 08 add six section Fragments without any of them knowing the Activity.
 */
interface NetworkMonitorSectionHost {

    /** Opens [section], replacing whatever the container currently shows. */
    fun openSection(section: NetworkMonitorSection)
}

/**
 * S1433: the single place a Monitor fragment transaction is built.
 *
 * CLAUDE.md Rule 3 keeps navigation out of the screen classes, and the practical reason is Phase 08: six
 * more sections arrive through this one seam instead of six more transaction sites that each have to get
 * the back stack and the restore-after-recreation behaviour right on their own.
 */
class NetworkMonitorSectionNavigator(
    private val fragmentManager: FragmentManager,
    @IdRes private val containerId: Int,
) {

    /**
     * The section on screen, read back from the committed Fragment rather than from a field of our own.
     *
     * After a configuration change the navigator is rebuilt while the FragmentManager restored its own
     * state, so the FragmentManager is the only party that still knows where the user was.
     */
    val currentSection: NetworkMonitorSection
        get() = sectionOfTag(fragmentManager.findFragmentById(containerId)?.tag)

    /** Shows [section], keeping [NetworkMonitorSection.Summary] as the single back-stack root. */
    fun openSection(section: NetworkMonitorSection) {
        if (currentSection == section && fragmentManager.findFragmentById(containerId) != null) {
            return
        }
        commit(
            section = section,
            fragment = createFragment(section),
            addToBackStack = section != NetworkMonitorSection.Summary,
        )
    }

    /** Returns to Summary, dropping any section the user opened on top of it. */
    fun returnToSummary() {
        if (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStack(SUMMARY_STACK_ROOT, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            return
        }
        openSection(NetworkMonitorSection.Summary)
    }

    /** True when a section sits above Summary, so the host can consume Back instead of finishing. */
    fun canGoBack(): Boolean = fragmentManager.backStackEntryCount > 0

    /** Clears the container - used when the screen turns out to be disabled after it was already populated. */
    fun clear() {
        val current = fragmentManager.findFragmentById(containerId) ?: return
        fragmentManager.popBackStackImmediate(SUMMARY_STACK_ROOT, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        fragmentManager.beginTransaction()
            .remove(current)
            .commitAllowingStateLoss()
    }

    private fun commit(section: NetworkMonitorSection, fragment: Fragment, addToBackStack: Boolean) {
        fragmentManager.beginTransaction()
            .replace(containerId, fragment, section.key)
            .apply {
                if (addToBackStack) {
                    addToBackStack(SUMMARY_STACK_ROOT)
                }
            }
            .commit()
    }

    /**
     * Every section has a screen, so this returns a Fragment rather than a nullable one.
     *
     * The `null` branches Phase 07 left behind - and the "stay on Summary" fallback that read them - are
     * gone with them: an exhaustive `when` over the enum now makes a future section fail to compile here
     * instead of silently doing nothing at run time.
     */
    private fun createFragment(section: NetworkMonitorSection): Fragment = when (section) {
        NetworkMonitorSection.Summary -> NetworkMonitorSummaryFragment()
        NetworkMonitorSection.Wifi -> WifiSectionFragment()
        NetworkMonitorSection.Mobile -> MobileSectionFragment()
        NetworkMonitorSection.Bluetooth -> BluetoothSectionFragment()
        NetworkMonitorSection.Gnss -> GnssSectionFragment()
        NetworkMonitorSection.Internet -> InternetSectionFragment()
        NetworkMonitorSection.History -> NetworkHistoryFragment()
    }

    private fun sectionOfTag(tag: String?): NetworkMonitorSection = NetworkMonitorSection.fromKey(tag)

    private companion object {

        /** Back-stack name of everything stacked above Summary, so one pop returns to the root. */
        const val SUMMARY_STACK_ROOT = "network_monitor_section"
    }
}
