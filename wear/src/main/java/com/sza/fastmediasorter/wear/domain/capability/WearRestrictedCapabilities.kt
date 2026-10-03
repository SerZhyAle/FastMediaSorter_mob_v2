package com.sza.fastmediasorter.wear.domain.capability

import com.sza.fastmediasorter.wear.domain.model.WearFaceSlotOption
import com.sza.fastmediasorter.wear.domain.model.WearFaceSlots

/**
 * S2486: the carrier for capabilities the Play review refuses on a watch, answered per product flavor.
 *
 * The question this interface exists to ask is "does this build go through the store", never "is this a
 * debug build" - the two coincide only by accident, and S2486 was opened because the accident had run out:
 * the credential-entry path was gated on the build type, so the sideload RELEASE the owner installs hid it
 * while the store DEBUG build showed it, both backwards.
 *
 * Members are named after the capability, not after the flavor. The next Play-refused capability adds a
 * property here; it does not add a second interface, and no consumer ever asks which flavor it is running
 * in. Implementations live one per flavor source set with a `@Binds` module beside each
 * (`dev/FLAVOR_DEVELOPMENT_RULES.md` Rule 8) - there is deliberately no default in shared code, because two
 * same-named declarations across `main` and a flavor set diverge silently.
 */
interface WearRestrictedCapabilities {

    /**
     * Whether this build offers a way to type a new network source - a username and a password - on the
     * watch itself. Wear OS review item WO-P6 refuses credential entry; the owner ruled on 2026-08-16
     * (S1707) to hide the way in rather than remove the capability, so the screen, its ViewModel and every
     * already-saved source stay in both flavors regardless of this answer.
     */
    val offersCredentialEntry: Boolean

    /**
     * Whether this build offers the body-sensor diagnostic - one foreground heart-rate reading with the
     * reason printed when it cannot be taken. Play reviews both `BODY_SENSORS` and
     * `health.READ_HEART_RATE` against six admitted use cases - fitness and coaching, rewards, corporate
     * wellness, medical care, human-subjects research, activity games - and a media sorter matches none,
     * so the store build withholds the way in (S2457). Unlike [offersCredentialEntry] this also decides
     * whether the program appears in the Apps catalog at all, because there is no already-saved content
     * behind it to keep reachable.
     */
    val offersBodySensorDiagnostics: Boolean

    /**
     * Whether this build may hold the system shade shut while a screen asks for it - the pull-down that
     * reaches airplane mode and brightness over a screen the user cannot touch away.
     *
     * The mechanism is lock task mode, which leaves a task with no shade at all; an ordinary app entering
     * it is screen pinning. Play reviews a kiosk-shaped capability against a short list of admitted uses -
     * dedicated devices, enterprise deployment, guided access - and a media sorter matches none of them,
     * so the store build does not ask for it (S2812). The overlay route that would otherwise cover the
     * shade is closed twice over: Wear OS 4 removed the system UI granting `SYSTEM_ALERT_WINDOW`, and an
     * application overlay may not draw over the status bar in any case.
     */
    val locksSystemShade: Boolean

    /**
     * S2995: Whether this build offers health and fitness programs (Blood Pressure log, Motion Monitor).
     * Play Console declarations prohibit health features in store builds unless declared in Play Console.
     * Withheld in `standard` flavor (store build), offered in `noLegal`.
     */
    val offersHealthFeatures: Boolean

    /**
     * S3178: whether this build reaches the user's own media - the watch's local library, the browse
     * graph over it, the three players and the document reader.
     *
     * Google Play requires strong core justification for broad photo and video access; browsing and
     * playing the watch's own library is this product's core function, and S4029 returned it to the
     * store build with that justification in the Play permission declaration.
     */
    val offersMediaAccess: Boolean

    /**
     * S3178: whether this build offers the voice recorder, its note list and the watch's own audio
     * broadcast - every capability that opens the microphone.
     *
     * Play treats microphone input as personal and sensitive user data. Every path here starts from a
     * tap on the watch and shows a live indicator; the one that does not - the automatic listening
     * start - is answered separately by [startsListeningAutomatically].
     */
    val offersVoiceRecording: Boolean

    /**
     * S4029: whether a listening request from the paired phone opens the microphone on its own.
     *
     * Separate from [offersVoiceRecording] because the store build offers the recorder again but not
     * this: an automatic start raises the request window through a full-screen intent and confirms it
     * without a tap, which is unexpected microphone access under Play's user-data policy. When false,
     * the request is an ordinary notification and the window waits for Allow or Decline.
     */
    val startsListeningAutomatically: Boolean

    /**
     * S3178: whether this build reaches a source that is not this watch - a network share, a stream
     * channel, and the credential entry [offersCredentialEntry] already governed on its own.
     *
     * Wider than [offersCredentialEntry] by design: that one hides only the way to TYPE a new source.
     * S4029 returned the transport to the store build, so a source provisioned from the phone connects
     * there while typing credentials on the watch stays sideload-only.
     */
    val offersRemoteSources: Boolean

    /**
     * S3178: whether this build offers the diagnostics that read the device itself - the Network
     * Monitor, the watch's own system report and the Tourist telemetry dashboard.
     *
     * Play counts device and usage data as personal and sensitive whatever the screen does with it.
     * The readings stay on the watch and are shown to its owner, which is what the store listing
     * discloses since S4029; the Tourist dashboard also needs [offersHealthFeatures] (S3042).
     */
    val offersDeviceDiagnostics: Boolean

    /**
     * S3178: whether this build may read what is around the watch - the visible Wi-Fi networks and the
     * Bluetooth adapter's state.
     *
     * Separate from [offersDeviceDiagnostics] even though the Network Monitor is the only consumer of
     * both: the permission families are reviewed separately, so returning one to the store variant must
     * not silently return the other.
     */
    val offersNearbyDeviceState: Boolean

    /**
     * S3178: whether the paired phone may ask this watch for a picture of its own screen.
     *
     * No permission carries it - the Data Layer listener and the capture path are the whole capability,
     * and the capture covers this app's own window only. Every edition answers true since S4029, so
     * the listener does not consult it; a build answering false would also need a refusal word on the
     * wire that the phone half understands, which is why none is added before such a build exists.
     */
    val offersScreenCapture: Boolean

    /**
     * S3178: whether content may cross the boundary of this watch - a file handed to another
     * application through the provider, or anything moved to or from the paired phone.
     *
     * The transfer path is also how the phone's virtual resources and its camera view arrive, so this
     * one answer governs every direction of the Data Layer content protocol rather than one screen.
     */
    val offersContentTransfer: Boolean

    /**
     * S3362: whether this build offers the programs that take the screen over - the water flashlight
     * and the distress signal.
     *
     * Both are built so that no touch and no swipe leaves them: every pointer event is consumed on
     * the initial pass and the exit is a hardware key. Wear OS review item WO-V3 asks for swipe to
     * close from almost all screens and admits two exemptions, an ongoing fitness activity and a
     * panning surface, which neither of these is. They also draw a full white screen against WO-V13
     * and, in the signal's case, strobe at about three flashes a second with the alarm stream raised
     * to its maximum. The owner ruled on 2026-09-21 that the first publication simply carries neither.
     *
     * Unlike [offersCredentialEntry] this decides whether the programs appear at all: there is no
     * saved content behind them to keep reachable, so the catalog row, the route, the tile cell and
     * the launch address all close together.
     */
    val offersScreenTakeoverPrograms: Boolean

    /**
     * S3178: whether this build exposes an entry point that does not pass through a screen - a tile
     * pointing at an excluded capability, a complication reading one, the Data Layer listener, or a
     * foreground service.
     *
     * The one predicate a navigation change alone can never satisfy: a component declared in the merged
     * manifest is startable by the platform, by GMS or by another application regardless of what the
     * route graph offers, which is why the declarations moved rather than the routes only.
     */
    val offersExternalEntryPoints: Boolean

    /**
     * S4023: what the four watch-face slots show until the phone chooses, slot 1 first, always
     * [WearFaceSlots.SLOT_COUNT] entries.
     *
     * A list rather than a yes/no because the two editions differ in what is safe to show, not in
     * whether the slots exist. S4023 gave the store build system values because it then had no
     * Data Layer listener; S4029 returned the listener and kept that default for a fresh install.
     */
    val faceSlotDefaults: List<WearFaceSlotOption>
}
