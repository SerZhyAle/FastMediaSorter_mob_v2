package com.sza.fastmediasorter.wear.ui.testing

import com.sza.fastmediasorter.wear.domain.model.HomeSectionId

/**
 * S2548: the addresses the watch flow tree taps, declared once so the value the UI writes and the
 * value a flow under `maestro/wear` reads cannot drift apart.
 *
 * The path above is spelled without a glob on purpose: Kotlin block comments NEST, so a `/` followed
 * by a `*` inside this KDoc opens a nested comment and swallows the rest of the file.
 *
 * A tag reaches the UiAutomator tree as a `resource-id` only because `testTagsAsResourceId` is set on
 * the navigation root in `MainActivity`; without that opt-in every value here is invisible to a driver.
 *
 * Every value is lowercase snake_case: an id survives a locale change and a caption does not, which is
 * the whole reason strategic ADR-4 forbids addressing a flow step by the text on screen.
 */
object WearTestTags {

    const val WEAR_NAV_ROOT = "wear_nav_root"
    const val WEAR_SETTINGS_ENTRY = "wear_settings_entry"
    const val WEAR_ABOUT_VERSION = "wear_about_version"

    private const val HOME_SECTION_PREFIX = "wear_home_section_"
    private const val SETTINGS_ROW_PREFIX = "wear_settings_row_"

    private val NON_TAG_CHARS = Regex("[^a-z0-9]+")

    fun homeSection(id: HomeSectionId): String = HOME_SECTION_PREFIX + id.name.lowercase()

    /**
     * A navigation route carries slashes, braces and argument placeholders; a `resource-id` that keeps
     * them is unquotable in a flow selector, so everything outside `a-z0-9` collapses to one underscore.
     */
    fun settingsRow(route: String): String =
        SETTINGS_ROW_PREFIX + NON_TAG_CHARS.replace(route.lowercase(), "_").trim('_')
}
