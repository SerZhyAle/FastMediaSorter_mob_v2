package com.sza.fastmediasorter.wear.domain.model

import androidx.annotation.StringRes

/**
 * Mini-programs of the watch, in the order the owner fixed for the first set.
 *
 * [canonicalKey] is the phone's route key for the same program, not a name invented here. The phone's
 * program registry addresses a program by that key, so keeping it identical is what lets the watch list
 * be absorbed into that registry later without renaming a key already saved on a device.
 *
 * S2579: the rule is two-part, because a program of this list may have no counterpart at all. A key is
 * either one of `InternalRouteCatalog`'s route keys, spelled identically, or the program lives only on
 * the watch and is named in `scripts/quality/wear-canonical-key-watch-only-baseline.txt` with its reason.
 * The single-part wording above held on the first five entries and quietly stopped being true when the
 * sensor programs joined; the gate `assert-wear-canonical-key-parity.ps1` now judges both halves.
 */
enum class WearAppId(val canonicalKey: String) {
    CALCULATOR("calculator"),
    NETWORK_MONITOR("network_monitor"),
    GAME("game"),

    /**
     * S2579: the watch's dictaphone, which the phone addresses as `quick_voice`. The watch yielded rather
     * than the phone: the phone's key is saved in launcher cells and app-launch panel layouts, so renaming
     * it would devalue a layout the owner built, while this key is saved nowhere at all.
     */
    VOICE_RECORDER("quick_voice"),

    /**
     * S2008: the watch's own report, moved here from Settings - it configures nothing, so it belongs
     * with the programs rather than with the switches.
     */
    SYSTEM_INFO("system_info"),

    /**
     * S2516: the display used as a light behind a lock no touch opens. Shares its `canonicalKey` with
     * the phone's program of the same name, which has a torch as well - the watch has no flash unit,
     * so only the lit screen half exists here.
     */
    WATER_FLASHLIGHT("water_flashlight"),

    /**
     * S2458: live motion and activity readings, for as long as its screen is open. Appended rather than
     * placed beside the Network Monitor it resembles: the order of this list is the owner's, and moving
     * an existing program is a decision this ticket was not asked to make.
     */
    MOTION_MONITOR("motion_monitor"),

    /**
     * S2457: one foreground heart-rate reading, with the reason printed when there cannot be one. The only
     * program of this list whose row is withheld in `standard` - Play reviews the heart-rate permissions
     * against six admitted use cases and a media sorter matches none (ADR-1), so the catalog answers
     * `isAvailable` from the build rather than listing it everywhere and explaining the absence inside.
     */
    BODY_SENSOR("body_sensor"),

    /**
     * S2809: manual blood pressure entry with history. Available in both flavors - no
     * permission or Health Services dependency, unlike the body sensor above. Watch-only,
     * recorded in the watch-only canonical-key baseline.
     */
    BLOOD_PRESSURE("blood_pressure"),

    /**
     * S2509: the watch's own audio broadcast. Watch-only by boundary decision rather than by omission -
     * the phone broadcasts too (S2508), but it does so from the player rather than as a launcher
     * program, so there is no `InternalRouteCatalog` key for this one to match. Recorded with that
     * reason in `scripts/quality/wear-canonical-key-watch-only-baseline.txt`.
     */
    BROADCAST("broadcast"),

    /**
     * S2825: the watch's stopwatch, sharing the phone's `stopwatch` route key. The phone half (S1411)
     * is addressed by that key in launcher cells and panel layouts, so the watch spells it identically
     * rather than inventing a second name for one program.
     */
    STOPWATCH("stopwatch"),

    /**
     * S3007: the watch's Tourist telemetry and navigation dashboard, sharing the phone's `tourist_info` route key.
     */
    TOURIST("tourist_info"),

    /**
     * S3109: the watch's text clipboard and the one action that hands it to the paired phone. Watch-only
     * by boundary decision - the phone sends its own clipboard from the Wear companion screen rather than
     * as a launcher program, so there is no `InternalRouteCatalog` key for this one to match. Recorded
     * with that reason in `scripts/quality/wear-canonical-key-watch-only-baseline.txt`.
     */
    CLIPBOARD("clipboard"),

    /**
     * S3216: siren and white-screen strobe on the Morse SOS cadence, behind the water flashlight's
     * touch lock. Shares its `canonicalKey` with the phone's program of the same name - starting it on
     * one device starts it on the other, so the two must be addressable as one program.
     */
    SOS("sos")
}

/**
 * One row of the Apps section.
 *
 * [isAvailable] exists from the first entry although none of the first three ever clears it: a program
 * that later needs a switch or a hardware check sets this field instead of forcing the list to grow a
 * second concept.
 */
data class WearApp(
    val id: WearAppId,
    @StringRes val labelRes: Int,
    val isAvailable: Boolean = true
)
