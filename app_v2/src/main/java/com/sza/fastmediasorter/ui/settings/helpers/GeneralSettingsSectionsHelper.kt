package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding

class GeneralSettingsSectionsHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val fragment: Fragment,
) {
    companion object {
        const val PREFS_NAME = "general_sections_state"
        const val KEY_INTERFACE_EXPANDED = "section_interface_expanded"
        const val KEY_PERMISSIONS_EXPANDED = "section_permissions_expanded"
        const val KEY_APP_DATA_EXPANDED = "section_app_data_expanded"
        const val KEY_SYSTEM_EXPANDED = "section_system_expanded"
        const val KEY_DEBUG_EXPANDED = "section_debug_expanded"
    }

    fun setup() {
        val savedStates = getSavedSectionStates()
        bindSectionToggle(
            binding.headerInterface, binding.containerInterface,
            fragment.getString(R.string.settings_category_interface),
            KEY_INTERFACE_EXPANDED, savedStates[KEY_INTERFACE_EXPANDED] ?: false
        )
        bindSectionToggle(
            binding.headerPermissions, binding.containerPermissions,
            fragment.getString(R.string.settings_category_permissions),
            KEY_PERMISSIONS_EXPANDED, savedStates[KEY_PERMISSIONS_EXPANDED] ?: false
        )
        bindSectionToggle(
            binding.headerAppData, binding.containerAppData,
            fragment.getString(R.string.settings_category_app_data),
            KEY_APP_DATA_EXPANDED, savedStates[KEY_APP_DATA_EXPANDED] ?: false
        )
        bindSectionToggle(
            binding.headerSystem, binding.containerSystem,
            fragment.getString(R.string.settings_category_system),
            KEY_SYSTEM_EXPANDED, savedStates[KEY_SYSTEM_EXPANDED] ?: false
        )
        if (BuildConfig.DEBUG) {
            bindSectionToggle(
                binding.headerDebugSettings, binding.containerDebugSettings,
                fragment.getString(R.string.debug_settings_title),
                KEY_DEBUG_EXPANDED, savedStates[KEY_DEBUG_EXPANDED] ?: false
            )
        }
    }

    private fun bindSectionToggle(
        header: android.widget.TextView,
        content: View,
        title: String,
        prefKey: String,
        initiallyExpanded: Boolean,
    ) {
        if (!header.isVisible) return
        content.isVisible = initiallyExpanded
        updateHeader(header, title, initiallyExpanded)
        header.setOnClickListener {
            val expanded = !content.isVisible
            content.isVisible = expanded
            updateHeader(header, title, expanded)
            saveSectionState(prefKey, expanded)
        }
    }

    private fun updateHeader(header: android.widget.TextView, title: String, expanded: Boolean) {
        val prefix = if (expanded) "▼" else "▶"
        header.text = fragment.getString(R.string.string_format_two_args, prefix, title)
    }

    private fun getSavedSectionStates(): Map<String, Boolean> {
        return StrictModeHelper.allowDiskReads {
            val prefs = fragment.requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            mapOf(
                KEY_INTERFACE_EXPANDED to prefs.getBoolean(KEY_INTERFACE_EXPANDED, false),
                KEY_PERMISSIONS_EXPANDED to prefs.getBoolean(KEY_PERMISSIONS_EXPANDED, false),
                KEY_APP_DATA_EXPANDED to prefs.getBoolean(KEY_APP_DATA_EXPANDED, false),
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
