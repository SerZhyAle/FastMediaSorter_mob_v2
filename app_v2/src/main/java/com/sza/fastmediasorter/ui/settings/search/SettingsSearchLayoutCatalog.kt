package com.sza.fastmediasorter.ui.settings.search

import com.sza.fastmediasorter.R

/**
 * Source of truth for layout resource ids scanned by `LayoutSettingsSearchSource`.
 *
 * Any new settings fragment layout that contains user-facing rows MUST be appended here;
 * otherwise its rows will be silently invisible to the settings search. The
 * `fragment_settings_media_container` layout is intentionally excluded because it is a
 * tab host with no rows of its own — the actual rows live in the sub-fragment layouts.
 */
object SettingsSearchLayoutCatalog {

    val layoutResIds: List<Int> = listOf(
        R.layout.fragment_settings_general,
        R.layout.fragment_settings_playback,
        R.layout.fragment_settings_images,
        R.layout.fragment_settings_video,
        R.layout.fragment_settings_audio,
        R.layout.fragment_settings_documents,
        R.layout.fragment_settings_other,
        R.layout.fragment_settings_destinations
        // fragment_settings_backup_restore is intentionally excluded: it backs the orphan
        // BackupRestoreFragment and is never shown. The live backup/restore/export-import rows
        // are part of fragment_settings_general, so scanning this layout would only yield
        // duplicate (btnBackup/btnRestore) and phantom (export/import favorites/resources)
        // entries that route to a tab where the view ids do not exist.
    )
}
