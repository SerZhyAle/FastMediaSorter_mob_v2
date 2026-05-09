package com.sza.fastmediasorter.ui.common.input

/**
 * Maps an [InputSurface] to the full documentation URL shown in the F1
 * help dialog CTA.
 *
 * Base URL and surface anchors are kept here so a future documentation
 * reshuffle does not require touching every call site. The resolver is
 * surface-specific; for general support routing (help, bug report, feedback)
 * use [com.sza.fastmediasorter.ui.common.support.SupportIntentFactory] —
 * that factory owns the canonical S0118 channels.
 */
object InputHelpLinkResolver {

    /** Root docs URL for keyboard / mouse support. */
    private const val BASE = "https://sza-apps.github.io/FastMediaSorter/docs/keyboard-shortcuts.html"

    /**
     * @return direct URL to the surface-specific section of the docs;
     *   falls back to [BASE] when no dedicated anchor is known yet.
     */
    fun urlFor(surface: InputSurface): String = when (surface) {
        InputSurface.MAIN -> "$BASE#main"
        InputSurface.BROWSE -> "$BASE#browse"
        InputSurface.PLAYER -> "$BASE#player"
        InputSurface.VR_PLAYER -> "$BASE#vr"
        InputSurface.SETTINGS -> "$BASE#settings"
        InputSurface.ADD_RESOURCE -> "$BASE#add-resource"
        InputSurface.CLOUD_PICKER -> "$BASE#cloud-picker"
        InputSurface.DUPLICATES -> "$BASE#duplicates"
        InputSurface.RESOURCE_EDITOR -> "$BASE#resource-editor"
        InputSurface.RECEIVE_SHARE -> "$BASE#receive-share"
        InputSurface.WIDGET_CONFIG -> "$BASE#widget-config"
        InputSurface.WELCOME -> "$BASE#welcome"
        InputSurface.DIALOG -> "$BASE#dialogs"
    }
}
