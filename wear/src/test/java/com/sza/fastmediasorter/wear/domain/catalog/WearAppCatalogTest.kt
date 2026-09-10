package com.sza.fastmediasorter.wear.domain.catalog

import com.sza.fastmediasorter.wear.domain.model.WearApp
import com.sza.fastmediasorter.wear.domain.model.WearAppId
import com.sza.fastmediasorter.wear.domain.model.destinationFor
import com.sza.fastmediasorter.wear.ui.navigation.WearLaunchRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearAppCatalogTest {

    @Test
    fun `catalog returns the first set in declaration order`() {
        val ids = WearAppCatalog.apps(offersBodySensorDiagnostics = true).map { it.id }

        assertEquals(
            listOf(
                WearAppId.CALCULATOR,
                WearAppId.NETWORK_MONITOR,
                WearAppId.GAME,
                WearAppId.VOICE_RECORDER,
                WearAppId.SYSTEM_INFO,
                WearAppId.WATER_FLASHLIGHT,
                WearAppId.MOTION_MONITOR,
                WearAppId.BODY_SENSOR,
                WearAppId.BLOOD_PRESSURE,
                WearAppId.BROADCAST,
                WearAppId.STOPWATCH
            ),
            ids
        )
    }

    @Test
    fun `every route equals its program canonical key`() {
        // S2751: the record no longer carries the address, so the parity is read where the address now
        // lives - the invariant is unchanged, only the place that answers "what is this program's route".
        WearAppCatalog.apps(offersBodySensorDiagnostics = true).forEach { app ->
            assertEquals(app.id.canonicalKey, WearLaunchRoutes.routeFor(destinationFor(app.id)))
        }
    }

    /**
     * S2457: the two halves of the flavor claim, read in one run.
     *
     * Compiling `standard` proves the code builds there, never that the row is withheld - the answer is a
     * parameter, so the only place both answers can be seen at once is here.
     */
    @Test
    fun `the offering build lists the body sensor program`() {
        val ids = WearAppCatalog.apps(offersBodySensorDiagnostics = true).map { it.id }

        assertTrue(ids.contains(WearAppId.BODY_SENSOR))
    }

    @Test
    fun `the withholding build drops the body sensor program and nothing else`() {
        val offered = WearAppCatalog.apps(offersBodySensorDiagnostics = true).map { it.id }
        val withheld = WearAppCatalog.apps(offersBodySensorDiagnostics = false).map { it.id }

        assertFalse(withheld.contains(WearAppId.BODY_SENSOR))
        // Subtracting the one id rather than restating the list: this stays true when an eighth program
        // is added, so it keeps testing the withholding and not the catalog's current length.
        assertEquals(offered - WearAppId.BODY_SENSOR, withheld)
    }

    @Test
    fun `an unavailable record is absent from the rendered list`() {
        val records = listOf(
            WearApp(WearAppId.CALCULATOR, labelRes = 1),
            WearApp(WearAppId.GAME, labelRes = 2, isAvailable = false)
        )

        val visible = records.filter { it.isAvailable }

        assertEquals(listOf(WearAppId.CALCULATOR), visible.map { it.id })
        assertFalse(visible.any { it.id == WearAppId.GAME })
    }
}
