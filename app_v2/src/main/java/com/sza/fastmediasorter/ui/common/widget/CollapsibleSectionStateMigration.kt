package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.content.SharedPreferences
import com.sza.fastmediasorter.core.debug.StrictModeHelper

/**
 * One-time migration of the legacy per-screen collapsible-state namespaces into the consolidated store.
 *
 * Guarded by a flag in the consolidated namespace so it runs once and legacy stores are never read
 * again afterwards. Copy-only: legacy preference files are left intact to avoid any data-loss risk.
 * Idempotent - running twice is a no-op. Only keys actually present in a legacy store are copied, so
 * sections the user never touched keep their new default-expansion behaviour.
 */
class CollapsibleSectionStateMigration(private val context: Context) {

    fun migrateIfNeeded() {
        StrictModeHelper.allowDiskReads {
            val target = consolidatedPrefs()
            if (target.getBoolean(MIGRATION_DONE_KEY, false)) return@allowDiskReads

            val migrated = LinkedHashMap<String, Boolean>()
            collectFixedKeyNamespaces(migrated)
            collectAddResource(migrated)
            collectResourceEditor(migrated)

            StrictModeHelper.allowDiskWrites {
                target.edit().apply {
                    migrated.forEach { (key, value) -> putBoolean(key, value) }
                    putBoolean(MIGRATION_DONE_KEY, true)
                }.apply()
            }
        }
    }

    private fun collectFixedKeyNamespaces(out: MutableMap<String, Boolean>) {
        FIXED_KEY_MIGRATIONS.forEach { (namespace, mapping) ->
            val prefs = legacyPrefs(namespace)
            mapping.forEach { (oldKey, newKey) ->
                if (prefs.contains(oldKey)) out[newKey] = prefs.getBoolean(oldKey, false)
            }
        }
    }

    // add_<type>_<orientation>_<section> -> add_resource__<type>__<section> (orientation dropped, last present wins).
    private fun collectAddResource(out: MutableMap<String, Boolean>) {
        val prefs = legacyPrefs(ADD_RESOURCE_NAMESPACE)
        ADD_RESOURCE_SECTIONS.forEach { (type, section) ->
            ORIENTATIONS.forEach { orientation ->
                val oldKey = "add_${type}_${orientation}_$section"
                if (prefs.contains(oldKey)) {
                    out["add_resource__${type}__$section"] = prefs.getBoolean(oldKey, false)
                }
            }
        }
    }

    // editor_<type>_<orientation>_<section> -> resource_editor__<section> (type+orientation dropped, last present wins).
    private fun collectResourceEditor(out: MutableMap<String, Boolean>) {
        val prefs = legacyPrefs(RESOURCE_EDITOR_NAMESPACE)
        RESOURCE_EDITOR_TYPES.forEach { type ->
            RESOURCE_EDITOR_SECTIONS.forEach { section ->
                ORIENTATIONS.forEach { orientation ->
                    val oldKey = "editor_${type}_${orientation}_$section"
                    if (prefs.contains(oldKey)) {
                        out["resource_editor__$section"] = prefs.getBoolean(oldKey, false)
                    }
                }
            }
        }
    }

    private fun consolidatedPrefs(): SharedPreferences =
        context.applicationContext
            .getSharedPreferences(CollapsibleSectionStore.NAMESPACE, Context.MODE_PRIVATE)

    private fun legacyPrefs(namespace: String): SharedPreferences =
        context.applicationContext.getSharedPreferences(namespace, Context.MODE_PRIVATE)

    companion object {
        const val MIGRATION_DONE_KEY = "migration_done_v1"

        private const val ADD_RESOURCE_NAMESPACE = "add_resource_ui_state"
        private const val RESOURCE_EDITOR_NAMESPACE = "resource_editor_ui_state"
        private val ORIENTATIONS = listOf("port", "land")

        private val FIXED_KEY_MIGRATIONS: Map<String, Map<String, String>> = mapOf(
            "general_sections_state" to mapOf(
                "section_interface_expanded" to "general__interface",
                "section_file_browser_expanded" to "general__file_browser",
                "section_remote_sources_expanded" to "general__remote_sources",
                "section_authorization_expanded" to "general__authorization",
                "section_app_data_expanded" to "general__app_data",
                "section_system_expanded" to "general__system",
                "section_debug_expanded" to "general__debug",
            ),
            "settings_section_states" to mapOf(
                "operations_safety_expanded" to "operations__safety",
                "destinations_file_ops_expanded" to "operations__file_ops",
                "destinations_list_expanded" to "operations__destinations",
                "scheduled_ops_expanded" to "operations__scheduled",
                "mgmt_behaviour_expanded" to "operations__behaviour",
                "mgmt_system_apps_expanded" to "operations__system_apps",
                "mgmt_screen_gestures_expanded" to "operations__screen_gestures",
            ),
            "playback_sections_state" to mapOf(
                "section_sorting_expanded" to "playback__sorting",
                "section_file_ops_expanded" to "playback__file_ops",
                "section_player_ui_expanded" to "playback__player_ui",
                "section_touch_zones_expanded" to "playback__touch_zones",
                "section_send_commands_expanded" to "playback__send_commands",
            ),
            "media_sections_state" to mapOf(
                "section_images_expanded" to "media__images",
                "section_video_expanded" to "media__video",
                "section_vr_expanded" to "media__vr",
                "section_audio_expanded" to "media__audio",
                "section_documents_expanded" to "media__documents",
                "section_other_expanded" to "media__other",
            ),
        )

        // type -> section, taken from AddResourceFormManager.sectionKey() call sites.
        private val ADD_RESOURCE_SECTIONS: List<Pair<String, String>> = listOf(
            "smb" to "conditions",
            "smb" to "media_types",
            "smb" to "additional",
            "sftp" to "server_verification",
            "sftp" to "conditions",
            "sftp" to "media_types",
            "sftp" to "additional",
        )

        private val RESOURCE_EDITOR_TYPES = listOf("local", "smb", "sftp", "ftp", "cloud")
        private val RESOURCE_EDITOR_SECTIONS = listOf(
            "connection", "media_types", "scanning", "destination", "advanced", "statistics",
        )
    }
}
