package com.sza.fastmediasorter.wear.ui.testing

import com.sza.fastmediasorter.wear.domain.model.HomeSectionId
import com.sza.fastmediasorter.wear.domain.model.WearBrowseCategory
import com.sza.fastmediasorter.wear.domain.model.WearContentType

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
    const val WEAR_ABOUT_WEB_PORTAL = "wear_about_web_portal"
    const val WEAR_ABOUT_WEB_PORTAL_ON_PHONE = "wear_about_web_portal_on_phone"
    const val WEAR_ABOUT_SEND_LOGS = "wear_about_send_logs"

    /**
     * S3186: the chip that moves the first-run walk on WITHOUT raising a system request - Next, Start or
     * Skip. One address for all three lets a sweep on a fresh install tap it until the walk is gone.
     */
    const val WEAR_ONBOARDING_FORWARD = "wear_onboarding_forward"

    private const val HOME_SECTION_PREFIX = "wear_home_section_"
    private const val SETTINGS_ROW_PREFIX = "wear_settings_row_"
    private const val BROWSE_CATEGORY_PREFIX = "wear_category_"
    private const val MEDIA_TYPE_PREFIX = "wear_media_type_"
    private const val MEDIA_FILE_PREFIX = "wear_media_file_"

    private val NON_TAG_CHARS = Regex("[^a-z0-9]+")

    fun homeSection(id: HomeSectionId): String = HOME_SECTION_PREFIX + id.name.lowercase()

    /**
     * A category is named by its route token, which is a wire value the navigation already depends
     * on, so the id tracks the token and never the translated caption beside it.
     */
    fun browseCategory(category: WearBrowseCategory): String =
        BROWSE_CATEGORY_PREFIX + NON_TAG_CHARS.replace(category.token.lowercase(), "_").trim('_')

    fun mediaType(type: WearContentType): String = MEDIA_TYPE_PREFIX + type.name.lowercase()

    /**
     * S3078: a file cell is addressed by its POSITION in the list, not by its name or its database
     * id - a flow is written against a seeded stand whose file names it cannot know, and the row id
     * changes with every rescan. The index is taken over the flat file list, so the same file keeps
     * the same address whether the screen drew one column or three.
     */
    fun mediaFileAt(index: Int): String = MEDIA_FILE_PREFIX + index

    /**
     * A navigation route carries slashes, braces and argument placeholders; a `resource-id` that keeps
     * them is unquotable in a flow selector, so everything outside `a-z0-9` collapses to one underscore.
     */
    fun settingsRow(route: String): String =
        SETTINGS_ROW_PREFIX + NON_TAG_CHARS.replace(route.lowercase(), "_").trim('_')
}
