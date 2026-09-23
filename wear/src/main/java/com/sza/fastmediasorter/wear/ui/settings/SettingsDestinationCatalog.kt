package com.sza.fastmediasorter.wear.ui.settings

/**
 * S3362: which settings pages the root menu offers, decided by the same answers that decide whether
 * their routes are registered at all.
 *
 * A row here is a promise that the page behind it exists. `MainActivity` registers MEDIA_TYPES and
 * SLIDESHOW only where media access is offered and TILE_TARGETS only where remote sources are, while
 * the menu listed all seven unconditionally, so in the store artifact three of them handed
 * `NavController.navigate` an address the graph cannot serve and the press ended in
 * `IllegalArgumentException` instead of on a page.
 *
 * Plain booleans rather than the capability carrier: this is the ROUTE agreement and it depends on
 * exactly the two answers named below, which keeps the list readable beside the registration block
 * it mirrors and testable without a carrier that grows a member per Play refusal.
 */
object SettingsDestinationCatalog {

    /**
     * The routes of the root menu, in the order they are drawn.
     *
     * @param offersMediaAccess mirrors the guard around the MEDIA_TYPES and SLIDESHOW registration.
     * @param offersRemoteSources mirrors the guard around the TILE_TARGETS registration.
     * @param hasPermissionRows S3226: the permissions page is registered in every edition and its row
     * follows its content instead, because a build that declares no sensitive permission has nothing
     * to show there.
     */
    fun destinations(
        offersMediaAccess: Boolean,
        offersRemoteSources: Boolean,
        hasPermissionRows: Boolean
    ): List<String> = buildList {
        if (offersMediaAccess) {
            add(SettingsRoutes.MEDIA_TYPES)
            add(SettingsRoutes.SLIDESHOW)
        }
        add(SettingsRoutes.SCREEN)
        add(SettingsRoutes.OTHER)
        if (offersRemoteSources) {
            add(SettingsRoutes.TILE_TARGETS)
        }
        if (hasPermissionRows) {
            add(SettingsRoutes.PERMISSIONS)
        }
        add(SettingsRoutes.ABOUT)
    }
}
