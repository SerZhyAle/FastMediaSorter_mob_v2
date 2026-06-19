package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import com.sza.fastmediasorter.databinding.FragmentSettingsDestinationsBinding
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader
import timber.log.Timber

/**
 * Owns the expandable-section state machine of the Operations tab: maps each collapsible header to
 * its container, persists per-section expanded state in SharedPreferences, and restores it on setup.
 * The scheduled section is only registered when the flavor enables scheduled operations.
 */
class OperationsSectionsManager(
    private val binding: FragmentSettingsDestinationsBinding,
    private val fragment: Fragment,
) {

    private data class ExpandableSection(
        val header: CollapsibleSectionHeader,
        val container: View,
        val prefKey: String,
        val defaultExpanded: Boolean,
    )

    fun setup() {
        val sections = mutableListOf(
            ExpandableSection(binding.headerSafety, binding.containerSafety, KEY_SAFETY_EXPANDED, false),
            ExpandableSection(binding.headerCopyMove, binding.containerFileOperations, KEY_FILE_OPS_EXPANDED, false),
            ExpandableSection(binding.headerDestinations, binding.containerDestinations, KEY_DESTINATIONS_EXPANDED, false),
        )
        if (BuildConfig.ENABLE_SCHEDULED_OPERATIONS) {
            sections += ExpandableSection(binding.headerScheduled, binding.containerScheduled, KEY_SCHEDULED_EXPANDED, false)
        } else {
            binding.headerScheduled.isVisible = false
            binding.containerScheduled.isVisible = false
        }
        sections += ExpandableSection(binding.headerBehaviour, binding.containerBehaviour, KEY_BEHAVIOUR_EXPANDED, false)
        sections += ExpandableSection(binding.headerOtherFeatures, binding.containerOtherFeatures, KEY_OTHER_FEATURES_EXPANDED, false)
        sections += ExpandableSection(binding.headerSystemApps, binding.containerSystemApps, KEY_SYSTEM_APPS_EXPANDED, false)
        sections += ExpandableSection(binding.headerScreenGestures, binding.containerScreenGestures, KEY_SCREEN_GESTURES_EXPANDED, false)

        val savedStates = StrictModeHelper.allowDiskReads {
            val prefs = fragment.requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sections.associate { it.prefKey to prefs.getBoolean(it.prefKey, it.defaultExpanded) }
        }

        sections.forEach { section ->
            val expanded = savedStates[section.prefKey] ?: section.defaultExpanded
            section.header.setExpanded(expanded, notify = false)
            section.container.isVisible = expanded
            section.header.setOnExpandedChangeListener { isExpanded ->
                section.container.isVisible = isExpanded
                StrictModeHelper.allowDiskWrites {
                    fragment.requireContext()
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(section.prefKey, isExpanded)
                        .apply()
                }
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "settings_section_states"
        private const val KEY_SAFETY_EXPANDED = "operations_safety_expanded"
        private const val KEY_FILE_OPS_EXPANDED = "destinations_file_ops_expanded"
        private const val KEY_DESTINATIONS_EXPANDED = "destinations_list_expanded"
        private const val KEY_SCHEDULED_EXPANDED = "scheduled_ops_expanded"
        // "mgmt_" prefix avoids collisions with PlaybackSettingsFragment's "section_*" keys.
        private const val KEY_BEHAVIOUR_EXPANDED = "mgmt_behaviour_expanded"
        private const val KEY_OTHER_FEATURES_EXPANDED = "mgmt_other_features_expanded"
        private const val KEY_SYSTEM_APPS_EXPANDED = "mgmt_system_apps_expanded"
        private const val KEY_SCREEN_GESTURES_EXPANDED = "mgmt_screen_gestures_expanded"
    }
}
