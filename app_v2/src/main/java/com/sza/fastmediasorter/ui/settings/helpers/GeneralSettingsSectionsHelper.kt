package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader

class GeneralSettingsSectionsHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val fragment: Fragment,
) {
    companion object {
        const val PREFS_NAME = "general_sections_state"
        const val KEY_INTERFACE_EXPANDED = "section_interface_expanded"
        const val KEY_FILE_BROWSER_EXPANDED = "section_file_browser_expanded"
        const val KEY_AUTHORIZATION_EXPANDED = "section_authorization_expanded"
        const val KEY_APP_DATA_EXPANDED = "section_app_data_expanded"
        const val KEY_SYSTEM_EXPANDED = "section_system_expanded"
        const val KEY_DEBUG_EXPANDED = "section_debug_expanded"
    }

    private data class ExpandableSection(
        val header: CollapsibleSectionHeader,
        val container: View,
        val prefKey: String,
        val defaultExpanded: Boolean,
    )

    fun setup() {
        val savedStates = getSavedSectionStates()
        val sections = mutableListOf(
            ExpandableSection(binding.headerInterface, binding.containerInterface, KEY_INTERFACE_EXPANDED, false),
            ExpandableSection(binding.headerFileBrowser, binding.containerFileBrowser, KEY_FILE_BROWSER_EXPANDED, false),
            // S0255: Authorization group is collapsed by default per strategic §6.4.
            ExpandableSection(binding.headerAuthorization, binding.containerAuthorization, KEY_AUTHORIZATION_EXPANDED, false),
            ExpandableSection(binding.headerAppData, binding.containerAppData, KEY_APP_DATA_EXPANDED, true),
            ExpandableSection(binding.headerSystem, binding.containerSystem, KEY_SYSTEM_EXPANDED, false),
        )
        if (BuildConfig.DEBUG) {
            sections += ExpandableSection(binding.headerDebugSettings, binding.containerDebugSettings, KEY_DEBUG_EXPANDED, false)
        }

        sections.forEach { section ->
            if (!section.header.isVisible) return@forEach
            val expanded = savedStates[section.prefKey] ?: section.defaultExpanded
            section.header.setExpanded(expanded, notify = false)
            section.container.isVisible = expanded
            section.header.setOnExpandedChangeListener { isExpanded ->
                section.container.isVisible = isExpanded
                saveSectionState(section.prefKey, isExpanded)
            }
        }
    }

    private fun getSavedSectionStates(): Map<String, Boolean> {
        return StrictModeHelper.allowDiskReads {
            val prefs = fragment.requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            mapOf(
                KEY_INTERFACE_EXPANDED to prefs.getBoolean(KEY_INTERFACE_EXPANDED, false),
                KEY_FILE_BROWSER_EXPANDED to prefs.getBoolean(KEY_FILE_BROWSER_EXPANDED, false),
                KEY_AUTHORIZATION_EXPANDED to prefs.getBoolean(KEY_AUTHORIZATION_EXPANDED, false),
                KEY_APP_DATA_EXPANDED to prefs.getBoolean(KEY_APP_DATA_EXPANDED, true),
                KEY_SYSTEM_EXPANDED to prefs.getBoolean(KEY_SYSTEM_EXPANDED, false),
                KEY_DEBUG_EXPANDED to prefs.getBoolean(KEY_DEBUG_EXPANDED, false)
            )
        }
    }

    private fun saveSectionState(key: String, expanded: Boolean) {
        StrictModeHelper.allowDiskWrites {
            fragment.requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(key, expanded)
                .apply()
        }
    }
}
