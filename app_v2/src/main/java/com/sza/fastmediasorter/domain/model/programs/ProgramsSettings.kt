package com.sza.fastmediasorter.domain.model.programs

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.sos.SosMode

/**
 * S4547: the program switches and the values our own programs remember, held by [AppSettings] as one
 * nested field for the reason recorded on
 * [com.sza.fastmediasorter.domain.model.launcher.LauncherSettings]: the synthetic default-argument
 * constructor of [AppSettings] spends one JVM descriptor slot per parameter and ART refuses a class
 * past 255 of them at launch. A field added for a program belongs here, where it costs no slot.
 *
 * The set is exactly what `ProgramsSettingsStore` reads and writes together. The class lives in a
 * subpackage rather than beside [AppSettings] on purpose: `scripts/check_device_profile_presets.ps1`
 * prefixes the members of a `*Settings` group found next to the model with the group's name, while
 * the preset CSV names these fields bare - the read-through properties on [AppSettings] give the
 * check those bare names.
 */
data class ProgramsSettings(
    val enableCalculator: Boolean = false,
    // S1411: the stopwatch is a sub-program like the calculator - off until the user asks for it.
    val enableStopwatch: Boolean = false,
    val embeddedGameEnabled: Boolean = false,
    // S1796: the front flashlight is a program like its neighbours - off until the user asks for it.
    val frontFlashlightEnabled: Boolean = false,
    // S1796: ARGB of the glow. White is the requested start state; the picker writes any other choice here.
    val frontFlashlightColor: Int = AppSettings.FRONT_FLASHLIGHT_DEFAULT_COLOR,
    // S2516: the water flashlight is its own program beside the one above, off until asked for.
    val waterFlashlightEnabled: Boolean = false,
    // S3216: the distress signal is a program like its neighbours - off until the user asks for it, so
    // an update never puts a siren one tap away from a pocket.
    val enableSos: Boolean = false,
    // S4542: on by default because Filler shipped in the programs menu before it had a switch; an
    // update must not hide it from an existing install.
    val enableFiller: Boolean = true,
    // S3216: which halves of the signal the SOS screen starts with, and what the paired device is asked
    // for. Written by the screen's own mode chips rather than by a settings row, so the last choice is
    // the next start - the mode is a decision taken in the emergency, not in advance.
    val sosMode: SosMode = SosMode.ALL,
    // S2776: the camera flashlight has no switch of its own - it is offered wherever the device has a
    // flash - so its shade shortcut needs one here, and a permanent notification nobody asked for is
    // a defect rather than a service.
    val flashlightShortcutNotificationEnabled: Boolean = false,
    // S1924: on by default wherever a front lens exists, unlike the flashlight above - ADR-3 records
    // the divergence so a later reader does not take it for an oversight and align the two.
    val mirrorEnabled: Boolean = true,
    val mirrorZoomRatio: Float = AppSettings.MIRROR_DEFAULT_ZOOM_RATIO,
    val mirrorHorizontallyFlipped: Boolean = true,
    val mirrorBacklightOn: Boolean = true,
    // S1433: the Network Monitor program is off until the user asks for it, like every other program.
    val enableNetworkMonitor: Boolean = false,
    // S1433: recording a GNSS track is a separate choice from opening the Monitor - on by default it
    // would change the Play Data Safety answer without the user ever acting.
    val recordGnssTrack: Boolean = false,
    // S1733: system information becomes a program like its neighbours - off until the user asks for it,
    // so an update never changes the composition of the programs panel on its own.
    val enableSystemInfo: Boolean = false,
    // S2997: the Tourist dashboard is a sub-program like its neighbours - off until the user asks for it,
    // so an update never changes the composition of the programs panel on its own.
    val enableTourist: Boolean = false,
    // S1735: the Wear companion becomes a program like its neighbours. Off by default so an update never
    // adds an entry to the programs panel on its own; it is shown at all only where the build carries the
    // watch bridge, which is a separate condition read from MediaCapabilities.
    val enableWearCompanion: Boolean = false,
    // S2810: suppress the Wear OS system media-control takeover of the watch screen. When ON, the
    // phone's AudioPlaybackService refuses a MediaSession connection from the Wear OS companion bridge
    // so the watch stops surfacing the phone's player as a system "now playing" screen. Off by default
    // so an update never changes current behavior. Phone-only toggle (read by the phone service), not a
    // watch-mirror field, so it lives here rather than in WearSettingsMirrorStore (see S2050).
    val suppressWearMediaTakeover: Boolean = false,
    // S0755: mirror the programs "three-dots" menu as a horizontal panel on the main window. Default
    // OFF (no behaviour change on upgrade); when ON the top three-dots button is hidden (panel replaces it).
    val showProgramsPanelInMainWindow: Boolean = false,
    // Enable "Favorites" feature (enabled by default).
    val enableFavorites: Boolean = true,
)
