package com.sza.fastmediasorter.wear.capability

import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.model.WearDestinationId
import com.sza.fastmediasorter.wear.domain.model.WearFaceSlotOption
import com.sza.fastmediasorter.wear.domain.model.WearFaceSystemItem
import javax.inject.Inject

/**
 * S2486: the answers for the flavor Play distributes.
 *
 * `standard` is the build that goes through the store, so every capability the review refuses is withheld
 * here - in the release AND in the debug build, because the predicate is about the distribution channel and
 * a debug build of this flavor is the same product one step earlier. Keeping a debug carve-out would mean
 * the shipped answer is never the one exercised during development, which is exactly how the build-type
 * gate this replaced went unnoticed.
 *
 * S4029: the owner ruled on 2026-10-01 that the store build offers every capability Play permits on a
 * watch, reversing S3178's allowlist-only first publication. What stays withheld is what is sensitive for
 * the store - health and body data, credential entry on the watch (WO-P6), the shade lock, the screen
 * takeover programs (WO-V3/WO-V13) and the automatic listening start. The classification and its policy
 * sources: `PLAN/S4029_wear-standard-return-allowed-capabilities/research/01__play-capability-classification.md`.
 */
class StandardWearRestrictedCapabilities @Inject constructor() : WearRestrictedCapabilities {

    /** WO-P6 refuses typing a username and password on the watch; a phone-provisioned source still works. */
    override val offersCredentialEntry: Boolean = false

    /**
     * S2457: neither heart-rate permission is declared in this flavor, so the diagnostic has no path and
     * the Apps catalog withholds its row rather than offering one that could only report a refusal.
     */
    override val offersBodySensorDiagnostics: Boolean = false

    /**
     * S2812: the store build leaves the system shade alone. Lock task mode is screen pinning for an
     * ordinary app, and a media sorter matches none of the uses Play admits for it.
     */
    override val locksSystemShade: Boolean = false

    /**
     * S2995: health features (Blood Pressure log, Motion Monitor) are withheld from the store build -
     * the owner's own example of what stays sideload-only.
     */
    override val offersHealthFeatures: Boolean = false

    /**
     * S4029: the eight answers below came back to the store build together with their permissions and
     * components, which wear/src/main/AndroidManifest.xml declares for both editions again. Each one is
     * a core media, transfer or user-initiated function, disclosed in the store listing and the Play
     * permission declaration; the sensitive halves of them are answered separately -
     * [offersCredentialEntry] for remote sources and [startsListeningAutomatically] for the microphone.
     */
    override val offersMediaAccess: Boolean = true

    override val offersVoiceRecording: Boolean = true

    /**
     * S4029: a phone's listening request waits for Allow on the watch and raises no full-screen
     * window; the store build never opens the microphone without that tap.
     */
    override val startsListeningAutomatically: Boolean = false

    override val offersRemoteSources: Boolean = true

    override val offersDeviceDiagnostics: Boolean = true

    override val offersNearbyDeviceState: Boolean = true

    override val offersScreenCapture: Boolean = true

    override val offersContentTransfer: Boolean = true

    override val offersExternalEntryPoints: Boolean = true

    /**
     * S3362: the water flashlight and the distress signal swallow every pointer event so the wet
     * glass cannot dismiss them, which is exactly the gesture WO-V3 requires from almost every
     * screen; the store build therefore offers neither. The sideload build keeps both unchanged.
     * S4029 keeps this: the Data Layer listener refuses the phone's distress-signal start too.
     */
    override val offersScreenTakeoverPrograms: Boolean = false

    /**
     * S4023: three system values that need no permission and the Apps shortcut. Kept after S4029
     * returned app data to this edition: a fresh store install has no favourites and nothing played,
     * so the app-data default would draw three empty buttons until the phone chooses.
     */
    override val faceSlotDefaults: List<WearFaceSlotOption> = listOf(
        WearFaceSlotOption.System(WearFaceSystemItem.BATTERY),
        WearFaceSlotOption.System(WearFaceSystemItem.DATE),
        WearFaceSlotOption.System(WearFaceSystemItem.NEXT_ALARM),
        WearFaceSlotOption.Destination(WearDestinationId.APPS)
    )
}
