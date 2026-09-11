package com.sza.fastmediasorter.core.panel

import androidx.annotation.ColorRes
import com.sza.fastmediasorter.R

/**
 * The single place in app_v2 that knows what colour a sub-program is.
 *
 * S2510: four surfaces used to decide a sub-program icon's tint independently - the quick-launch grid
 * and its edit picker to one neutral on-surface colour, the programs menu to whatever its vector
 * baked in, the panel strip to white - so "make the icons colourful" could not be one edit. A surface
 * asks here instead of deciding, exactly as MediaTypeColorCatalog already does for content types.
 *
 * Colours are returned as resource ids, never as resolved ints, so the theme picks the day or the
 * night member on its own. A caller that memoises a resolved value must key that cache by night mode.
 *
 * The eight `color_program_accent_*` names are frozen: wear/src/main/res/values/colors.xml declares
 * the same set against its own values, and that name match is the whole contract between two modules
 * that share no source (ADR-4). The watch resolves them through ui/apps/WearAppAccentCatalog.
 *
 * The palette is narrow on purpose (ADR-3). What makes a list scannable is how far apart the hues
 * sit, not how many there are; handing each of nearly thirty programs its own tone would produce
 * neighbours indistinguishable on a glyph the size of a fingernail. The assignment below is authored
 * rather than derived from the key, so related programs can be grouped and programs that stand next
 * to each other in a list can be deliberately separated - a hash can be neither reviewed nor
 * corrected, and reshuffles wholesale when a key is renamed.
 */
object SubProgramAccentCatalog {

    /** Distinct tones in the palette. Pinned by the completeness test so widening it is deliberate. */
    const val PALETTE_SIZE = 8

    /** The accent of the sub-program owning [routeKey], or null when that key has none. */
    @ColorRes
    fun accentFor(routeKey: String): Int? = accents[routeKey]

    /**
     * The same accent for a surface whose background does not follow the theme, or null when the key has none.
     *
     * S2889: a caller picks between this and [accentFor] by the background it draws on, never by the theme
     * (strategic ADR-2). The one surface that needs it today is the main-window programs panel, an opaque
     * dark strip in both themes - the theme-following tone there is a dark glyph on dark olive by day, which
     * measured 2.14 against a 3.0 threshold at its worst. The `_on_dark` resources have no values-night twin,
     * so they stay on the light half of the palette whatever the theme is doing.
     *
     * Derived from [accents] rather than authored a second time: a route that carried two different tones
     * would be a divergence of exactly the kind this catalog exists to prevent, and reassigning a program's
     * tone must stay one edit.
     */
    @ColorRes
    fun accentOnDarkFor(routeKey: String): Int? = accents[routeKey]?.let(onDarkTones::get)

    /** The light-half twin of each frozen tone. Pinned to [PALETTE_SIZE] by the completeness test. */
    private val onDarkTones: Map<Int, Int> = mapOf(
        R.color.color_program_accent_red to R.color.color_program_accent_red_on_dark,
        R.color.color_program_accent_orange to R.color.color_program_accent_orange_on_dark,
        R.color.color_program_accent_amber to R.color.color_program_accent_amber_on_dark,
        R.color.color_program_accent_green to R.color.color_program_accent_green_on_dark,
        R.color.color_program_accent_teal to R.color.color_program_accent_teal_on_dark,
        R.color.color_program_accent_blue to R.color.color_program_accent_blue_on_dark,
        R.color.color_program_accent_indigo to R.color.color_program_accent_indigo_on_dark,
        R.color.color_program_accent_purple to R.color.color_program_accent_purple_on_dark,
    )

    private val accents: Map<String, Int> = mapOf(
        // Recognition and voice: what the program reads or hears.
        InternalRouteCatalog.KEY_OCR to R.color.color_program_accent_red,
        InternalRouteCatalog.KEY_TAKE_PHOTO_OCR_TRANSLATE to R.color.color_program_accent_red,
        InternalRouteCatalog.KEY_QUICK_VOICE to R.color.color_program_accent_red,

        // Compute and the panel itself.
        InternalRouteCatalog.KEY_CALCULATOR to R.color.color_program_accent_orange,
        // S1411: the stopwatch measures rather than computes, but it is the calculator's neighbour in
        // every other respect, so it takes the same accent instead of minting a colour for one tool.
        InternalRouteCatalog.KEY_STOPWATCH to R.color.color_program_accent_orange,
        InternalRouteCatalog.KEY_APP_LAUNCH_PANEL to R.color.color_program_accent_orange,
        InternalRouteCatalog.KEY_SCHEDULED_TASKS to R.color.color_program_accent_orange,

        // The screen or the lamp used as a light.
        InternalRouteCatalog.KEY_FRONT_FLASHLIGHT to R.color.color_program_accent_amber,
        InternalRouteCatalog.KEY_PHYSICAL_FLASHLIGHT to R.color.color_program_accent_amber,
        // S2516: the amber of the light family rather than a colour of its own - it is a flashlight
        // before it is anything else, and the menu reads the family by its tone.
        InternalRouteCatalog.KEY_WATER_FLASHLIGHT to R.color.color_program_accent_amber,
        InternalRouteCatalog.KEY_BLACK_SCREEN to R.color.color_program_accent_amber,

        // Play and outdoors.
        InternalRouteCatalog.KEY_GAME to R.color.color_program_accent_green,
        InternalRouteCatalog.KEY_TOURIST_INFO to R.color.color_program_accent_green,

        // Anything that reaches another machine.
        InternalRouteCatalog.KEY_NETWORK_MONITOR to R.color.color_program_accent_teal,
        InternalRouteCatalog.KEY_STREAMS to R.color.color_program_accent_teal,
        InternalRouteCatalog.KEY_LINK_DOWNLOAD to R.color.color_program_accent_teal,
        InternalRouteCatalog.KEY_WEAR_COMPANION to R.color.color_program_accent_teal,
        InternalRouteCatalog.KEY_WATCH_LISTEN to R.color.color_program_accent_teal,
        InternalRouteCatalog.KEY_WATCH_LISTEN_RECORD to R.color.color_program_accent_teal,

        // Taking a photo.
        InternalRouteCatalog.KEY_QUICK_CAMERA to R.color.color_program_accent_blue,
        InternalRouteCatalog.KEY_TAKE_PHOTO_SEND_TO to R.color.color_program_accent_blue,
        InternalRouteCatalog.KEY_TAKE_PHOTO_EDIT to R.color.color_program_accent_blue,
        InternalRouteCatalog.KEY_CAMERA_PHOTOS to R.color.color_program_accent_blue,
        InternalRouteCatalog.KEY_CAMERA_LAUNCH to R.color.color_program_accent_blue,

        // The device and its screen. System info sits here rather than with the calculator so that the
        // watch's five programs, which include both, land on five different tones.
        InternalRouteCatalog.KEY_SYSTEM_INFO to R.color.color_program_accent_indigo,
        InternalRouteCatalog.KEY_SCREEN_RECORDING to R.color.color_program_accent_indigo,
        InternalRouteCatalog.KEY_MIRROR to R.color.color_program_accent_indigo,
        InternalRouteCatalog.KEY_START_VIDEO_RECORDING to R.color.color_program_accent_indigo,

        // Content the user comes back to.
        InternalRouteCatalog.KEY_FAVORITES to R.color.color_program_accent_purple,
        InternalRouteCatalog.KEY_CONTINUE_READING to R.color.color_program_accent_purple,
        InternalRouteCatalog.KEY_RANDOM_MUSIC to R.color.color_program_accent_purple,
    )
}
