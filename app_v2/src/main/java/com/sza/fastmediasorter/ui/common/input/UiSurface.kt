package com.sza.fastmediasorter.ui.common.input

/**
 * Semantic UI surface profile used by the shared keyboard parser to resolve
 * surface-appropriate shortcut mapping.
 *
 * Every Activity or DialogFragment that wants unified keyboard/mouse
 * behaviour declares its surface; the shared parser then emits the right
 * [InputAction] for the same physical key.
 *
 * This is a screen-identity enum of the UI layer; it is deliberately distinct
 * from the domain InputSurface resolve-bucket used by the binding table.
 *
 * Latin-only policy for letter shortcuts applies regardless of surface.
 */
enum class UiSurface {
    /** Main resource list on `MainActivity`. */
    MAIN,

    /** File browser surface. */
    BROWSE,

    /** In-app player (`PlayerActivity`) and standalone player ("Open with"). */
    PLAYER,

    /** Settings host surface. */
    SETTINGS,

    /** Add-resource form. */
    ADD_RESOURCE,

    /** Cloud folder picker (Google Drive, Dropbox, OneDrive, ...). */
    CLOUD_PICKER,

    /** Duplicate-files surface. */
    DUPLICATES,

    /** Resource editor surface. */
    RESOURCE_EDITOR,

    /** Receive-share activity. */
    RECEIVE_SHARE,

    /** Widget configuration activity. */
    WIDGET_CONFIG,

    /** Welcome / first-run flow. */
    WELCOME,

    /** Generic dialog / DialogFragment. */
    DIALOG,

    /** VR player surface (Bluetooth keyboard only). */
    VR_PLAYER,

    /** Scheduled operations surface. */
    SCHEDULED_OPS,

    /** Live streams and channel catalog surface. */
    STREAMS,

    /** Cloud authentication sessions surface. */
    AUTH_SESSIONS,

    /** Keybinding and remote remap surface. */
    KEYBINDING_REMAP,

    /** Wear OS companion sync and pairing surface. */
    WEAR_COMPANION,
}
