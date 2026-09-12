package com.sza.fastmediasorter.wear.domain.capability

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
}
