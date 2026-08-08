package com.sza.fastmediasorter.ui.main.helpers

import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.tabs.TabLayout
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
/**
 * Owns the two visual states of the main-window resource-type filter row: expanded (the full
 * [tabLayout]) and collapsed (the narrow labelled [collapsedStrip]). Long-press a tab to collapse,
 * tap the strip to expand - mirroring the player copy/move panels.
 *
 * Visibility always defers to the vanish rule via [isPanelAvailable]: when no remote source is
 * enabled neither the tabs nor the strip show. The collapsed flag is cached in-memory and updated
 * before the settings write, so an async settings re-emission cannot revert the UI to a stale state.
 *
 * Long-press is bound per tab view (not on the TabLayout itself): the internal tab views consume the
 * touch, so a listener on the container would never fire. [onTabsRebuilt] re-binds after every
 * [MainResourceTabsManager.createTabs].
 */
class MainResourceTabsCollapseManager(
    private val tabLayout: TabLayout,
    private val collapsedStrip: View,
    private val isPanelAvailable: () -> Boolean,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    // S1443: the strip may live in the command bar rather than the collapsed row, so whoever places
    // it has to learn that this chip appeared or vanished.
    private val onChipVisibilityChanged: () -> Unit = {}
) {

    private var collapsed = false

    /** Wire the strip tap and load the persisted collapsed state. Call once during initial setup. */
    fun install() {
        collapsedStrip.setOnClickListener { expand() }
        scope.launch {
            collapsed = settingsRepository.getSettings().first().resourceTypeTabCollapsed
            applyVisibility()
        }
    }

    /** Re-bind the per-tab long-press and re-apply visibility after the tab set is (re)built. */
    fun onTabsRebuilt() {
        bindTabLongPress()
        applyVisibility()
    }

    private fun bindTabLongPress() {
        val tabStrip = tabLayout.getChildAt(0) as? ViewGroup ?: return
        for (i in 0 until tabStrip.childCount) {
            tabStrip.getChildAt(i).apply {
                isLongClickable = true
                setOnLongClickListener {
                    collapse()
                    true
                }
            }
        }
    }

    private fun collapse() {
        setCollapsed(true)
    }

    private fun expand() {
        setCollapsed(false)
    }

    private fun setCollapsed(value: Boolean) {
        if (collapsed == value) return
        // Capture focus before hiding: a GONE view loses focus, so query it first to hand focus over.
        val tabsHadFocus = tabLayout.hasFocus()
        val stripHadFocus = collapsedStrip.hasFocus()
        collapsed = value
        applyVisibility()
        if (value && tabsHadFocus) {
            collapsedStrip.requestFocus()
        } else if (!value && stripHadFocus) {
            tabLayout.requestFocus()
        }
        persist(value)
    }

    private fun persist(value: Boolean) {
        scope.launch {
            val current = settingsRepository.getSettings().first()
            settingsRepository.updateSettings(current.copy(resourceTypeTabCollapsed = value))
        }
    }

    private fun applyVisibility() {
        val (tabsVisible, stripVisible) = resolveVisibility(isPanelAvailable(), collapsed)
        tabLayout.isVisible = tabsVisible
        collapsedStrip.isVisible = stripVisible
        onChipVisibilityChanged()
    }

    companion object {
        /**
         * (tabsVisible, stripVisible) for the given availability + collapsed state. The vanish rule
         * wins: when unavailable both are hidden. Tabs and strip are never both shown.
         */
        internal fun resolveVisibility(available: Boolean, collapsed: Boolean): Pair<Boolean, Boolean> =
            (available && !collapsed) to (available && collapsed)
    }
}
