package com.sza.fastmediasorter.ui.common.input

import com.sza.fastmediasorter.R

/**
 * Central registry of F1 help entries per [InputSurface].
 *
 * Kept in one place so documentation generators and the in-app help
 * dialog share a single source of truth. Per-surface tables only list
 * shortcuts that actually exist on that surface; global shortcuts
 * (F1 help itself, F10 exit, Tab navigation) live in the common tail
 * appended by [get].
 */
object InputHelpRegistry {

    private val GLOBAL: List<InputHelpEntry.Section> = listOf(
        InputHelpEntry.Section(
            titleRes = R.string.kbm_help_section_global,
            entries = listOf(
                entry("F1", R.string.kbm_help_global_f1),
                entry("F10 / Alt+F4", R.string.kbm_help_global_exit),
                entry("Esc", R.string.kbm_help_global_escape),
                entry("Tab / Shift+Tab", R.string.kbm_help_global_tab),
                entry("Ctrl+F", R.string.kbm_help_global_search),
                entry("Ctrl+Z / Ctrl+Y", R.string.kbm_help_global_undo_redo),
            ),
        ),
    )

    private val MAIN: List<InputHelpEntry.Section> = listOf(
        InputHelpEntry.Section(
            titleRes = R.string.kbm_help_section_navigation,
            entries = listOf(
                entry("↑ / ↓ / ← / →", R.string.kbm_help_nav_arrows),
                entry("PgUp / PgDn", R.string.kbm_help_nav_page),
                entry("Home / End", R.string.kbm_help_nav_home_end),
                entry("Enter", R.string.kbm_help_main_open),
                entry("Insert / +", R.string.kbm_help_main_add),
            ),
        ),
        InputHelpEntry.Section(
            titleRes = R.string.kbm_help_section_file_ops,
            entries = listOf(
                entry("F2 / Ctrl+R", R.string.kbm_help_op_rename),
                entry("F5 / Ctrl+C", R.string.kbm_help_op_copy),
                entry("F8 / Del / Ctrl+D", R.string.kbm_help_op_delete),
                entry("Ctrl+Shift+R / Ctrl+F5", R.string.kbm_help_op_refresh),
            ),
        ),
    )

    private val BROWSE: List<InputHelpEntry.Section> = listOf(
        InputHelpEntry.Section(
            titleRes = R.string.kbm_help_section_navigation,
            entries = listOf(
                entry("↑ / ↓ / ← / →", R.string.kbm_help_nav_arrows),
                entry("PgUp / PgDn", R.string.kbm_help_nav_page),
                entry("Home / End", R.string.kbm_help_nav_home_end),
                entry("Enter", R.string.kbm_help_browse_open),
                entry("Backspace", R.string.kbm_help_browse_back),
            ),
        ),
        InputHelpEntry.Section(
            titleRes = R.string.kbm_help_section_selection,
            entries = listOf(
                entry("Insert / Space", R.string.kbm_help_sel_toggle),
                entry("Num+ / Ctrl+A", R.string.kbm_help_sel_all),
                entry("Num- / Ctrl+Shift+A / Esc", R.string.kbm_help_sel_clear),
                entry("Num*", R.string.kbm_help_sel_invert),
                entry("Shift+↑ / Shift+↓", R.string.kbm_help_sel_range),
            ),
        ),
        InputHelpEntry.Section(
            titleRes = R.string.kbm_help_section_file_ops,
            entries = listOf(
                entry("F2 / Ctrl+R", R.string.kbm_help_op_rename),
                entry("F3 / Ctrl+Q", R.string.kbm_help_op_view),
                entry("F4 / Ctrl+E", R.string.kbm_help_op_edit),
                entry("F5 / Ctrl+C", R.string.kbm_help_op_copy),
                entry("F6 / Ctrl+X", R.string.kbm_help_op_move),
                entry("F7 / Ctrl+Shift+N", R.string.kbm_help_op_create_folder),
                entry("F8 / Del / Ctrl+D", R.string.kbm_help_op_delete),
                entry("Ctrl+Shift+R", R.string.kbm_help_op_refresh),
            ),
        ),
    )

    private val PLAYER: List<InputHelpEntry.Section> = listOf(
        InputHelpEntry.Section(
            titleRes = R.string.kbm_help_section_playback,
            entries = listOf(
                entry("Space / Enter", R.string.kbm_help_player_playpause),
                entry("← / →", R.string.kbm_help_player_prev_next),
                entry("PgUp / PgDn", R.string.kbm_help_player_prev_next),
                entry("[ / ]", R.string.kbm_help_player_seek_10s),
                entry("Shift+← / Shift+→", R.string.kbm_help_player_seek_60s),
                entry("↑ / ↓", R.string.kbm_help_player_volume),
                entry("M", R.string.kbm_help_player_mute),
                entry("F", R.string.kbm_help_player_fullscreen),
                entry(", / .", R.string.kbm_help_player_frame_step),
            ),
        ),
        InputHelpEntry.Section(
            titleRes = R.string.kbm_help_section_file_ops,
            entries = listOf(
                entry("F2 / Ctrl+R", R.string.kbm_help_op_rename),
                entry("F3 / Ctrl+I", R.string.kbm_help_player_info),
                entry("F5 / Ctrl+C", R.string.kbm_help_op_copy),
                entry("F6 / Ctrl+X", R.string.kbm_help_op_move),
                entry("F8 / Del / Ctrl+D", R.string.kbm_help_op_delete),
                entry("F9 / Ctrl+M", R.string.kbm_help_op_context_menu),
                entry("Ctrl+S", R.string.kbm_help_player_save),
            ),
        ),
    )

    private val SETTINGS: List<InputHelpEntry.Section> = listOf(
        InputHelpEntry.Section(
            titleRes = R.string.kbm_help_section_navigation,
            entries = listOf(
                entry("← / →", R.string.kbm_help_settings_tabs),
                entry("↑ / ↓", R.string.kbm_help_settings_rows),
                entry("Enter / Space", R.string.kbm_help_settings_activate),
                entry("Tab", R.string.kbm_help_global_tab),
                entry("Ctrl+F", R.string.kbm_help_settings_search),
                entry("Esc / F10", R.string.kbm_help_settings_back),
            ),
        ),
    )

    private val DIALOG: List<InputHelpEntry.Section> = listOf(
        InputHelpEntry.Section(
            titleRes = R.string.kbm_help_section_dialog,
            entries = listOf(
                entry("Enter / Ctrl+Enter", R.string.kbm_help_dialog_primary),
                entry("Esc", R.string.kbm_help_dialog_cancel),
                entry("↑ / ↓", R.string.kbm_help_dialog_arrows),
                entry("Space", R.string.kbm_help_dialog_toggle),
            ),
        ),
    )

    private val GENERIC_NAV: List<InputHelpEntry.Section> = listOf(
        InputHelpEntry.Section(
            titleRes = R.string.kbm_help_section_navigation,
            entries = listOf(
                entry("↑ / ↓ / ← / →", R.string.kbm_help_nav_arrows),
                entry("Enter", R.string.kbm_help_nav_enter),
                entry("Backspace", R.string.kbm_help_nav_backspace),
                entry("Esc", R.string.kbm_help_nav_escape),
            ),
        ),
    )

    /**
     * @return help sections for [surface] followed by the shared global
     *   section. Returns an empty list only if no entries exist.
     */
    fun get(surface: InputSurface): List<InputHelpEntry.Section> {
        val surfaceSections = when (surface) {
            InputSurface.MAIN -> MAIN
            InputSurface.BROWSE -> BROWSE
            InputSurface.PLAYER, InputSurface.VR_PLAYER -> PLAYER
            InputSurface.SETTINGS -> SETTINGS
            InputSurface.DIALOG -> DIALOG
            InputSurface.ADD_RESOURCE,
            InputSurface.CLOUD_PICKER,
            InputSurface.DUPLICATES,
            InputSurface.RESOURCE_EDITOR,
            InputSurface.RECEIVE_SHARE,
            InputSurface.WIDGET_CONFIG,
            InputSurface.WELCOME -> GENERIC_NAV
        }
        return surfaceSections + GLOBAL
    }

    private fun entry(keys: String, descriptionRes: Int) =
        InputHelpEntry(keys = keys, descriptionRes = descriptionRes)
}
