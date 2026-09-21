package com.sza.fastmediasorter.wear.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3362: the settings root offered three pages the store artifact does not register, and tapping one
 * handed `NavController.navigate` an address its graph cannot serve.
 *
 * Compiling `standard` proves the code builds there, never that the row is withheld - the answers are
 * parameters, so this is the only place both editions can be seen at once.
 */
class SettingsDestinationCatalogTest {

    @Test
    fun `the offering build lists every settings page`() {
        val routes = SettingsDestinationCatalog.destinations(
            offersMediaAccess = true,
            offersRemoteSources = true,
            hasPermissionRows = true
        )

        assertEquals(
            listOf(
                SettingsRoutes.MEDIA_TYPES,
                SettingsRoutes.SLIDESHOW,
                SettingsRoutes.SCREEN,
                SettingsRoutes.OTHER,
                SettingsRoutes.TILE_TARGETS,
                SettingsRoutes.PERMISSIONS,
                SettingsRoutes.ABOUT
            ),
            routes
        )
    }

    /** The store artifact: no media access, no remote sources and no sensitive permission declared. */
    @Test
    fun `the withholding build lists only the pages its graph registers`() {
        val routes = SettingsDestinationCatalog.destinations(
            offersMediaAccess = false,
            offersRemoteSources = false,
            hasPermissionRows = false
        )

        assertEquals(
            listOf(SettingsRoutes.SCREEN, SettingsRoutes.OTHER, SettingsRoutes.ABOUT),
            routes
        )
    }

    @Test
    fun `media access carries both of its pages and nothing else`() {
        val routes = SettingsDestinationCatalog.destinations(
            offersMediaAccess = true,
            offersRemoteSources = false,
            hasPermissionRows = false
        )

        assertTrue(routes.contains(SettingsRoutes.MEDIA_TYPES))
        assertTrue(routes.contains(SettingsRoutes.SLIDESHOW))
        assertFalse(routes.contains(SettingsRoutes.TILE_TARGETS))
    }

    @Test
    fun `remote sources carry the tile targets page`() {
        val routes = SettingsDestinationCatalog.destinations(
            offersMediaAccess = false,
            offersRemoteSources = true,
            hasPermissionRows = false
        )

        assertTrue(routes.contains(SettingsRoutes.TILE_TARGETS))
        assertFalse(routes.contains(SettingsRoutes.MEDIA_TYPES))
    }

    /**
     * S3226: the permissions page is registered in every edition, so its row is decided by whether the
     * screen behind it found anything to show rather than by a capability.
     */
    @Test
    fun `the permissions row follows its rows in both editions`() {
        val withRows = SettingsDestinationCatalog.destinations(
            offersMediaAccess = false,
            offersRemoteSources = false,
            hasPermissionRows = true
        )

        assertTrue(withRows.contains(SettingsRoutes.PERMISSIONS))
        assertEquals(SettingsRoutes.ABOUT, withRows.last())
    }
}
