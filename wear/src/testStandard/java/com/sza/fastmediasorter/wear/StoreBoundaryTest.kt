package com.sza.fastmediasorter.wear

import com.sza.fastmediasorter.wear.capability.StandardWearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.catalog.HomeSectionCatalog
import com.sza.fastmediasorter.wear.domain.catalog.WearAppCatalog
import com.sza.fastmediasorter.wear.domain.model.HomeSectionId
import com.sza.fastmediasorter.wear.domain.model.HomeSectionVisibility
import com.sza.fastmediasorter.wear.domain.model.PhoneCompanionState
import com.sza.fastmediasorter.wear.domain.model.WearAppId
import com.sza.fastmediasorter.wear.domain.model.WearDestinationId
import com.sza.fastmediasorter.wear.domain.model.WearFaceSlotOption
import com.sza.fastmediasorter.wear.domain.model.WearFaceSlots
import com.sza.fastmediasorter.wear.domain.model.WearFaceSystemItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3178 / S4029: the store boundary asserted from inside the flavor that ships through Google Play.
 *
 * In `wear/src/testStandard/` rather than in the shared test set on purpose: the subject is the answer
 * THIS flavor gives, and a shared test could only assert it by constructing the implementation it wants,
 * which proves nothing about which one the build binds. Its mirror is `NoLegalBoundaryTest`.
 *
 * S4029 replaced the Programs-only expectations of the first publication: the store build offers every
 * capability Play permits on a watch and withholds only the sensitive ones, and both halves are asserted
 * whole so a capability moving either way is a decision rather than an accident.
 */
class StoreBoundaryTest {

    private val capabilities = StandardWearRestrictedCapabilities()

    @Test
    fun `every Play-permitted capability is offered by the store build`() {
        assertTrue("media access", capabilities.offersMediaAccess)
        assertTrue("voice recording", capabilities.offersVoiceRecording)
        assertTrue("remote sources", capabilities.offersRemoteSources)
        assertTrue("device diagnostics", capabilities.offersDeviceDiagnostics)
        assertTrue("nearby device state", capabilities.offersNearbyDeviceState)
        assertTrue("screen capture", capabilities.offersScreenCapture)
        assertTrue("content transfer", capabilities.offersContentTransfer)
        assertTrue("external entry points", capabilities.offersExternalEntryPoints)
    }

    @Test
    fun `every sensitive capability stays withheld from the store build`() {
        assertFalse("health features", capabilities.offersHealthFeatures)
        assertFalse("body sensor diagnostics", capabilities.offersBodySensorDiagnostics)
        assertFalse("credential entry", capabilities.offersCredentialEntry)
        assertFalse("screen takeover programs", capabilities.offersScreenTakeoverPrograms)
        assertFalse("system shade lock", capabilities.locksSystemShade)
        assertFalse("automatic listening start", capabilities.startsListeningAutomatically)
    }

    /**
     * S4023: a fresh store install has no favourites and nothing played, so its face starts on system
     * values and the Apps shortcut. Asserted whole so a fifth kind of content is a decision.
     */
    @Test
    fun `the face slots default to content a fresh install can draw`() {
        val defaults = capabilities.faceSlotDefaults

        assertEquals(
            listOf(
                WearFaceSlotOption.System(WearFaceSystemItem.BATTERY),
                WearFaceSlotOption.System(WearFaceSystemItem.DATE),
                WearFaceSlotOption.System(WearFaceSystemItem.NEXT_ALARM),
                WearFaceSlotOption.Destination(WearDestinationId.APPS)
            ),
            defaults
        )
        assertEquals(WearFaceSlots.SLOT_COUNT, defaults.size)
    }

    @Test
    fun `the home screen draws every media origin with the phone present`() {
        val sections = HomeSectionCatalog.sectionsFor(storeVisibility(streamsEnabled = true)).map { it.id }

        assertEquals(
            listOf(
                HomeSectionId.RESOURCES,
                HomeSectionId.PHONE,
                HomeSectionId.LOCAL,
                HomeSectionId.STREAMS,
                HomeSectionId.APPS,
                HomeSectionId.BROADCAST,
                HomeSectionId.PHONE_CAMERA,
                HomeSectionId.FAVOURITES
            ),
            sections
        )
    }

    @Test
    fun `the streams row follows the user preference`() {
        val sections = HomeSectionCatalog.sectionsFor(storeVisibility(streamsEnabled = false)).map { it.id }

        assertFalse(sections.contains(HomeSectionId.STREAMS))
        assertTrue(sections.contains(HomeSectionId.LOCAL))
    }

    /**
     * S4011 still decides the phone-bound rows: with no companion the store build keeps the watch's own
     * media and hides Phone and Phone camera instead of offering screens that can only fail.
     */
    @Test
    fun `phone-bound rows wait for the companion`() {
        val visibility = storeVisibility(streamsEnabled = true, phoneCompanion = PhoneCompanionState.ABSENT)
        val sections = HomeSectionCatalog.sectionsFor(visibility).map { it.id }

        assertFalse(sections.contains(HomeSectionId.PHONE))
        assertFalse(sections.contains(HomeSectionId.PHONE_CAMERA))
        assertFalse(sections.contains(HomeSectionId.RESOURCES))
        assertTrue(sections.contains(HomeSectionId.LOCAL))
        assertTrue(sections.contains(HomeSectionId.FAVOURITES))
        assertTrue(HomeSectionCatalog.companionHintFor(visibility) != null)
    }

    @Test
    fun `the apps list offers every program except the sensitive ones`() {
        val apps = WearAppCatalog.apps(capabilities).map { it.id }

        assertEquals(
            listOf(
                WearAppId.CALCULATOR,
                WearAppId.NETWORK_MONITOR,
                WearAppId.GAME,
                WearAppId.VOICE_RECORDER,
                WearAppId.SYSTEM_INFO,
                WearAppId.BROADCAST,
                WearAppId.STOPWATCH,
                WearAppId.CLIPBOARD
            ),
            apps
        )
    }

    /**
     * S3362: a program id this build does not offer resolves to no record, and never throws.
     *
     * The stored id outlives the install: a watch that ran the sideload build keeps `lastUsedApp` in its
     * own DataStore across an update. `HomeViewModel.availableApp` looks that id up in exactly this
     * catalog, so a null here is what turns the row into the broadcast fallback instead.
     */
    @Test
    fun `a sensitive program id stored by the sideload build resolves to no record here`() {
        val apps = WearAppCatalog.apps(capabilities)

        WITHHELD_PROGRAMS.forEach { stored ->
            assertNull(
                "$stored was recorded as last used before the update and still resolves",
                apps.firstOrNull { it.id == stored }
            )
        }
    }

    /**
     * S3358: the pre-release walk declares which rows this flavor draws, and here it is held to it.
     *
     * `streamsEnabled = true` on purpose - the Streams row has a user preference in front of the
     * capability, and the question here is the flavor's answer alone. `lastUsedApp = null` for the
     * same reason: with a program in that slot the catalog emits LAST_USED_APP, which is nobody's
     * declared entry, instead of the BROADCAST row the walk names.
     */
    @Test
    fun `the declared walk claims only the rows this flavor draws`() {
        WearWalkContract.assertScopeMatchesCatalogs(
            flavor = "standard",
            sections = HomeSectionCatalog.sectionsFor(storeVisibility(streamsEnabled = true)).map { it.id },
            apps = WearAppCatalog.apps(capabilities).map { it.id }
        )
    }

    private fun storeVisibility(
        streamsEnabled: Boolean,
        phoneCompanion: PhoneCompanionState = PhoneCompanionState.PRESENT
    ) = HomeSectionVisibility(
        streamsEnabled = streamsEnabled,
        lastUsedApp = null,
        offersMediaAccess = capabilities.offersMediaAccess,
        offersRemoteSources = capabilities.offersRemoteSources,
        offersContentTransfer = capabilities.offersContentTransfer,
        offersVoiceRecording = capabilities.offersVoiceRecording,
        phoneCompanion = phoneCompanion
    )

    private companion object {
        val WITHHELD_PROGRAMS = listOf(
            WearAppId.MOTION_MONITOR,
            WearAppId.BODY_SENSOR,
            WearAppId.BLOOD_PRESSURE,
            WearAppId.TOURIST,
            WearAppId.WATER_FLASHLIGHT,
            WearAppId.SOS
        )
    }
}
