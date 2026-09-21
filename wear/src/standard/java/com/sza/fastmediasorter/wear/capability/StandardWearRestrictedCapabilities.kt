package com.sza.fastmediasorter.wear.capability

import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
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
 * First content of `wear/src/standard/`, which had no source set at all until this ticket. It is not the
 * placeholder `dev/FLAVOR_DEVELOPMENT_RULES.md` Rule 8 bans - a two-sided `@Binds` contract has no
 * implementation unless both sides declare one.
 */
class StandardWearRestrictedCapabilities @Inject constructor() : WearRestrictedCapabilities {

    override val offersCredentialEntry: Boolean = false

    /**
     * S2457: neither heart-rate permission is declared in this flavor, so the diagnostic has no path and
     * the Apps catalog withholds its row rather than offering one that could only report a refusal.
     */
    override val offersBodySensorDiagnostics: Boolean = false

    /**
     * S2812: the store build leaves the system shade alone. Nothing else about the water flashlight changes -
     * the screen still lights, still swallows touch, and still leaves on a hardware key.
     */
    override val locksSystemShade: Boolean = false

    /**
     * S2995: health features (Blood Pressure log, Motion Monitor) are withheld from the store build.
     */
    override val offersHealthFeatures: Boolean = false

    /**
     * S3178: the first Google Play publication is an allowlist artifact, and none of the eight answers
     * below is on the list. Each one is `false` for the same reason and it is stated once here rather
     * than eight times: the permissions and components behind them are declared only in the sideload
     * manifest, so in this flavor the capability has no path at all - offering the way in could only
     * produce a refusal on the user's screen. Returning any one of them is a separate ticket that edits
     * wear/config/store-boundary-policy.json first.
     */
    override val offersMediaAccess: Boolean = false

    override val offersVoiceRecording: Boolean = false

    override val offersRemoteSources: Boolean = false

    override val offersDeviceDiagnostics: Boolean = false

    override val offersNearbyDeviceState: Boolean = false

    override val offersScreenCapture: Boolean = false

    override val offersContentTransfer: Boolean = false

    override val offersExternalEntryPoints: Boolean = false

    /**
     * S3362: the water flashlight and the distress signal swallow every pointer event so the wet
     * glass cannot dismiss them, which is exactly the gesture WO-V3 requires from almost every
     * screen; the store build therefore offers neither. The sideload build keeps both unchanged.
     */
    override val offersScreenTakeoverPrograms: Boolean = false
}
