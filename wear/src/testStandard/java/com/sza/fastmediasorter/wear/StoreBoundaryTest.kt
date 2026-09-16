package com.sza.fastmediasorter.wear

import com.sza.fastmediasorter.wear.capability.StandardWearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.catalog.HomeSectionCatalog
import com.sza.fastmediasorter.wear.domain.catalog.WearAppCatalog
import com.sza.fastmediasorter.wear.domain.model.HomeSectionId
import com.sza.fastmediasorter.wear.domain.model.HomeSectionVisibility
import com.sza.fastmediasorter.wear.domain.model.WearAppId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3178: the store boundary asserted from inside the flavor that ships through Google Play.
 *
 * In `wear/src/testStandard/` rather than in the shared test set on purpose: the subject is the answer
 * THIS flavor gives, and a shared test could only assert it by constructing the implementation it wants,
 * which proves nothing about which one the build binds. Its mirror is `NoLegalBoundaryTest`, and the two
 * are deliberately opposite - a change that made both pass by weakening the contract would have to
 * contradict itself.
 */
class StoreBoundaryTest {

    private val capabilities = StandardWearRestrictedCapabilities()

    @Test
    fun `every policy category is withheld from the store build`() {
        assertFalse("media access", capabilities.offersMediaAccess)
        assertFalse("voice recording", capabilities.offersVoiceRecording)
        assertFalse("remote sources", capabilities.offersRemoteSources)
        assertFalse("device diagnostics", capabilities.offersDeviceDiagnostics)
        assertFalse("nearby device state", capabilities.offersNearbyDeviceState)
        assertFalse("health features", capabilities.offersHealthFeatures)
        assertFalse("body sensor diagnostics", capabilities.offersBodySensorDiagnostics)
        assertFalse("screen capture", capabilities.offersScreenCapture)
        assertFalse("content transfer", capabilities.offersContentTransfer)
        assertFalse("external entry points", capabilities.offersExternalEntryPoints)
        assertFalse("credential entry", capabilities.offersCredentialEntry)
    }

    @Test
    fun `the home screen offers only the store-safe sections`() {
        val sections = HomeSectionCatalog.sectionsFor(storeVisibility()).map { it.id }

        assertEquals(listOf(HomeSectionId.APPS), sections)
    }

    @Test
    fun `no excluded origin survives the streams preference being on`() {
        val sections = HomeSectionCatalog.sectionsFor(storeVisibility(streamsEnabled = true)).map { it.id }

        // The preference is the user's and outlives an update, so a watch that had Streams switched on
        // in the sideload build must not get the row back after installing the store one.
        assertFalse(sections.contains(HomeSectionId.STREAMS))
        assertFalse(sections.contains(HomeSectionId.RESOURCES))
        assertFalse(sections.contains(HomeSectionId.LOCAL))
        assertFalse(sections.contains(HomeSectionId.PHONE))
        assertFalse(sections.contains(HomeSectionId.PHONE_CAMERA))
        assertFalse(sections.contains(HomeSectionId.FAVOURITES))
        assertFalse(sections.contains(HomeSectionId.BROADCAST))
    }

    @Test
    fun `the apps list offers only the programs that need nothing`() {
        val apps = WearAppCatalog.apps(capabilities).map { it.id }

        assertEquals(
            listOf(
                WearAppId.CALCULATOR,
                WearAppId.GAME,
                WearAppId.WATER_FLASHLIGHT,
                WearAppId.STOPWATCH,
                WearAppId.CLIPBOARD
            ),
            apps
        )
    }

    @Test
    fun `no withheld program is reachable through the apps list`() {
        val apps = WearAppCatalog.apps(capabilities).map { it.id }

        assertTrue(
            "a withheld program is offered: $apps",
            WITHHELD_PROGRAMS.none { apps.contains(it) }
        )
    }

    private fun storeVisibility(streamsEnabled: Boolean = false) = HomeSectionVisibility(
        streamsEnabled = streamsEnabled,
        lastUsedApp = null,
        offersMediaAccess = capabilities.offersMediaAccess,
        offersRemoteSources = capabilities.offersRemoteSources,
        offersContentTransfer = capabilities.offersContentTransfer,
        offersVoiceRecording = capabilities.offersVoiceRecording
    )

    private companion object {
        val WITHHELD_PROGRAMS = listOf(
            WearAppId.NETWORK_MONITOR,
            WearAppId.VOICE_RECORDER,
            WearAppId.SYSTEM_INFO,
            WearAppId.MOTION_MONITOR,
            WearAppId.BODY_SENSOR,
            WearAppId.BLOOD_PRESSURE,
            WearAppId.BROADCAST,
            WearAppId.TOURIST
        )
    }
}
