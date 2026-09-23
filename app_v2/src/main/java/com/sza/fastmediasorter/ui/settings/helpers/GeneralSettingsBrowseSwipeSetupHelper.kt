package com.sza.fastmediasorter.ui.settings.helpers

import com.sza.fastmediasorter.domain.model.BrowseSwipeDirection
import com.sza.fastmediasorter.ui.browse.helpers.BrowseSwipeActionCatalog

/**
 * S2533: wires both Browse row swipe-action dropdown rows.
 *
 * Its own class rather than another `setupXxx` in [GeneralSettingsViewSetupHelper]: that class sits
 * against detekt's LargeClass threshold, so a feature adding rows to the file-browser section grows
 * it past the limit. Entries and the index-to-action mapping come from the one catalogue, so the
 * picker's order and the gesture's labels cannot drift apart, and the write goes through
 * [BrowseSwipeDirection] rather than touching the settings field directly.
 */
class GeneralSettingsBrowseSwipeSetupHelper(
    private val hostContext: GeneralSettingsHostContext,
) {
    private val binding get() = hostContext.binding
    private val viewModel get() = hostContext.viewModel
    private val fragment get() = hostContext.fragment

    fun setup() {
        val actions = BrowseSwipeActionCatalog.orderedForPicker()
        val labels = actions.map { fragment.getString(BrowseSwipeActionCatalog.labelResFor(it)) }
        val rows = listOf(
            binding.rowBrowseSwipeLeftAction to BrowseSwipeDirection.LEFT,
            binding.rowBrowseSwipeRightAction to BrowseSwipeDirection.RIGHT,
        )
        for ((row, direction) in rows) {
            row.setEntries(labels)
            row.setOnItemSelectedListener { index ->
                val picked = actions.getOrNull(index) ?: return@setOnItemSelectedListener
                val current = viewModel.settings.value
                if (direction.actionOf(current) == picked) return@setOnItemSelectedListener
                viewModel.updateSettings(direction.withAction(current, picked))
            }
        }
    }
}
