package com.sza.fastmediasorter.ui.common.input

/**
 * Maps an [UiSurface] to the full documentation URL shown in the F1
 * help dialog CTA.
 *
 * Base URL and surface anchors are kept here so a future documentation
 * reshuffle does not require touching every call site. The resolver is
 * surface-specific; for general support routing (help, bug report, feedback)
 * use [com.sza.fastmediasorter.ui.common.support.SupportIntentFactory] -
 * that factory owns the canonical S0118 channels.
 */
object InputHelpLinkResolver {

    /** Root docs URL for keyboard / mouse support. */
    private const val BASE = "https://sza-apps.github.io/FastMediaSorter/docs/keyboard-shortcuts.html"

    /**
     * @return direct URL to the surface-specific section of the docs;
     *   falls back to [BASE] when no dedicated anchor is known yet.
     */
    fun urlFor(surface: UiSurface): String = when (surface) {
        UiSurface.MAIN -> "$BASE#main"
        UiSurface.BROWSE -> "$BASE#browse"
        UiSurface.PLAYER -> "$BASE#player"
        UiSurface.VR_PLAYER -> "$BASE#vr"
        UiSurface.SETTINGS -> "$BASE#settings"
        UiSurface.ADD_RESOURCE -> "$BASE#add-resource"
        UiSurface.CLOUD_PICKER -> "$BASE#cloud-picker"
        UiSurface.DUPLICATES -> "$BASE#duplicates"
        UiSurface.RESOURCE_EDITOR -> "$BASE#resource-editor"
        UiSurface.RECEIVE_SHARE -> "$BASE#receive-share"
        UiSurface.WIDGET_CONFIG -> "$BASE#widget-config"
        UiSurface.WELCOME -> "$BASE#welcome"
        UiSurface.DIALOG -> "$BASE#dialogs"
    }
}
