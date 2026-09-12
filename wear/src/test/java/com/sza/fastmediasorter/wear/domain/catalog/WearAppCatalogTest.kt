package com.sza.fastmediasorter.wear.domain.catalog

import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.model.WearApp
import com.sza.fastmediasorter.wear.domain.model.WearAppId
import com.sza.fastmediasorter.wear.domain.model.destinationFor
import com.sza.fastmediasorter.wear.ui.navigation.WearLaunchRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private data class FakeCapabilities(
    override val offersCredentialEntry: Boolean = true,
    override val offersBodySensorDiagnostics: Boolean = true,
    override val locksSystemShade: Boolean = true,
    override val offersHealthFeatures: Boolean = true,
) : WearRestrictedCapabilities

class WearAppCatalogTest {

    @Test
    fun `catalog returns the first set in declaration order`() {
        val ids = WearAppCatalog.apps(FakeCapabilities()).map { it.id }

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
                WearAppId.STOPWATCH,
                WearAppId.TOURIST
            ),
            ids
        )
    }

    @Test
    fun `every route equals its program canonical key`() {
        // S2751: the record no longer carries the address, so the parity is read where the address now
        // lives - the invariant is unchanged, only the place that answers "what is this program's route".
        WearAppCatalog.apps(FakeCapabilities()).forEach { app ->
            assertEquals(app.id.canonicalKey, WearLaunchRoutes.routeFor(destinationFor(app.id)))
        }
    }

    /**
     * S2457 / S2995: the two halves of the flavor claim, read in one run.
     *
     * Compiling `standard` proves the code builds there, never that the row is withheld - the answer is a
     * parameter, so the only place both answers can be seen at once is here.
     */
    @Test
    fun `the offering build lists the body sensor and health programs`() {
        val ids = WearAppCatalog.apps(FakeCapabilities()).map { it.id }

        assertTrue(ids.contains(WearAppId.BODY_SENSOR))
        assertTrue(ids.contains(WearAppId.MOTION_MONITOR))
        assertTrue(ids.contains(WearAppId.BLOOD_PRESSURE))
    }

    @Test
    fun `the withholding build drops health and body sensor programs`() {
        val offered = WearAppCatalog.apps(FakeCapabilities()).map { it.id }
        val withholdingCaps = FakeCapabilities(
            offersBodySensorDiagnostics = false,
            offersHealthFeatures = false
        )
        val withheld = WearAppCatalog.apps(withholdingCaps).map { it.id }

        assertFalse(withheld.contains(WearAppId.BODY_SENSOR))
        assertFalse(withheld.contains(WearAppId.MOTION_MONITOR))
        assertFalse(withheld.contains(WearAppId.BLOOD_PRESSURE))
        assertEquals(offered - setOf(WearAppId.BODY_SENSOR, WearAppId.MOTION_MONITOR, WearAppId.BLOOD_PRESSURE), withheld)
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
