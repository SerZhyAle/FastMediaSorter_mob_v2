package com.sza.fastmediasorter.ui.common.input

import com.sza.fastmediasorter.domain.input.InputSurface

/**
 * Semantic UI surface profile used by the shared keyboard parser to resolve
 * surface-appropriate shortcut mapping.
 *
 * Every Activity or DialogFragment that wants unified keyboard/mouse
 * behaviour declares its surface; the shared parser then emits the right
 * [InputAction] for the same physical key.
 *
 * This is a screen-identity enum of the UI layer; it is deliberately distinct
 * from the domain [InputSurface] resolve-bucket. Use [toResolveSurface] to
 * bridge from a screen identity to the binding-resolution surface.
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
}

/**
 * Maps a UI [UiSurface] (screen identity) to the domain [InputSurface]
 * resolve-bucket that the key-binding system understands, or null when the
 * screen does not participate in binding resolution.
 *
 * Domain resolve-surfaces are intentionally fewer than UI surfaces: only
 * player, browser, dialog and VR carry remappable bindings. Screens with no
 * domain equivalent (MAIN, SETTINGS, forms, pickers, ..) return null so the
 * binding key-space stays free of phantom surfaces.
 */
fun UiSurface.toResolveSurface(): InputSurface? = when (this) {
    UiSurface.PLAYER -> InputSurface.PLAYER
    UiSurface.VR_PLAYER -> InputSurface.VR
    UiSurface.BROWSE -> InputSurface.BROWSER
    UiSurface.DIALOG -> InputSurface.DIALOG
    UiSurface.MAIN,
    UiSurface.SETTINGS,
    UiSurface.ADD_RESOURCE,
    UiSurface.CLOUD_PICKER,
    UiSurface.DUPLICATES,
    UiSurface.RESOURCE_EDITOR,
    UiSurface.RECEIVE_SHARE,
    UiSurface.WIDGET_CONFIG,
    UiSurface.WELCOME -> null
}
