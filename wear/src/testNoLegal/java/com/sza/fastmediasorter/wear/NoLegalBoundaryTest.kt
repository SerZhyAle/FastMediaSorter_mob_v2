package com.sza.fastmediasorter.wear

import com.sza.fastmediasorter.wear.capability.NoLegalWearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.catalog.HomeSectionCatalog
import com.sza.fastmediasorter.wear.domain.catalog.WearAppCatalog
import com.sza.fastmediasorter.wear.domain.model.HomeSectionId
import com.sza.fastmediasorter.wear.domain.model.HomeSectionVisibility
import com.sza.fastmediasorter.wear.domain.model.WearAppId
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3178: the other half of the boundary - the sideload artifact keeps the whole product.
 *
 * Strategic §2 states it as a non-goal: `noLegal` does not change behaviour to make the store variant
 * simpler. This test is what makes that checkable, because the cheapest way to pass `StoreBoundaryTest`
 * would be to delete the capability from both flavors, and that would fail here.
 */
class NoLegalBoundaryTest {

    private val capabilities = NoLegalWearRestrictedCapabilities()

    @Test
    fun `every policy category is offered by the sideload build`() {
        assertTrue("media access", capabilities.offersMediaAccess)
        assertTrue("voice recording", capabilities.offersVoiceRecording)
        assertTrue("remote sources", capabilities.offersRemoteSources)
        assertTrue("device diagnostics", capabilities.offersDeviceDiagnostics)
        assertTrue("nearby device state", capabilities.offersNearbyDeviceState)
        assertTrue("health features", capabilities.offersHealthFeatures)
        assertTrue("body sensor diagnostics", capabilities.offersBodySensorDiagnostics)
        assertTrue("screen capture", capabilities.offersScreenCapture)
        assertTrue("content transfer", capabilities.offersContentTransfer)
        assertTrue("external entry points", capabilities.offersExternalEntryPoints)
        assertTrue("credential entry", capabilities.offersCredentialEntry)
    }

    @Test
    fun `every home origin is still drawn`() {
        val sections = HomeSectionCatalog.sectionsFor(sideloadVisibility(streamsEnabled = true)).map { it.id }

        listOf(
            HomeSectionId.RESOURCES,
            HomeSectionId.PHONE,
            HomeSectionId.LOCAL,
            HomeSectionId.STREAMS,
            HomeSectionId.APPS,
            HomeSectionId.BROADCAST,
            HomeSectionId.PHONE_CAMERA,
            HomeSectionId.FAVOURITES
        ).forEach { id ->
            assertTrue("$id is missing from the sideload home screen", sections.contains(id))
        }
    }

    @Test
    fun `every program the store build withholds is still offered here`() {
        val apps = WearAppCatalog.apps(capabilities).map { it.id }

        listOf(
            WearAppId.NETWORK_MONITOR,
            WearAppId.VOICE_RECORDER,
            WearAppId.SYSTEM_INFO,
            WearAppId.MOTION_MONITOR,
            WearAppId.BODY_SENSOR,
            WearAppId.BLOOD_PRESSURE,
            WearAppId.BROADCAST,
            WearAppId.TOURIST
        ).forEach { id ->
            assertTrue("$id is missing from the sideload apps list", apps.contains(id))
        }
    }

    private fun sideloadVisibility(streamsEnabled: Boolean) = HomeSectionVisibility(
        streamsEnabled = streamsEnabled,
        lastUsedApp = null,
        offersMediaAccess = capabilities.offersMediaAccess,
        offersRemoteSources = capabilities.offersRemoteSources,
        offersContentTransfer = capabilities.offersContentTransfer,
        offersVoiceRecording = capabilities.offersVoiceRecording
    )
}
