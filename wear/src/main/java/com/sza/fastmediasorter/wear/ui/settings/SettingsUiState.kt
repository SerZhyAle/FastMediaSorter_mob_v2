package com.sza.fastmediasorter.wear.ui.settings

import com.sza.fastmediasorter.wear.domain.model.PowerSavingTrigger
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteSendPolicy
import com.sza.fastmediasorter.wear.domain.model.WearBackgroundMode
import com.sza.fastmediasorter.wear.domain.model.WearColorScheme
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.WearGeometryMode
import com.sza.fastmediasorter.wear.domain.model.WearViewMode

/**
 * UI state for Settings screen.
 */
data class SettingsUiState(
    val isLoading: Boolean = true,
    
    // Media types
    val isAudioEnabled: Boolean = true,
    val isVideoEnabled: Boolean = true,
    val isImagesEnabled: Boolean = true,
    val isDocumentsEnabled: Boolean = true,

    // Slideshow
    val isSlideshowEnabled: Boolean = false,
    val slideshowIntervalSeconds: Int = 5,

    /** S2505: player panel auto-hide duration in seconds. */
    val panelAutoHideSeconds: Int = 15,
    
    // Album art
    val downloadAlbumArt: Boolean = false,

    /** S1781: one view shared by the home screen and the Resources page. */
    val viewMode: WearViewMode = WearViewMode.LIST,

    /** S1730: the view of a file list inside a resource, separate from [viewMode] by owner ruling. */
    val fileListViewMode: WearViewMode = WearViewMode.LIST,

    /** S1781: the Streams section ships on and is switched off from the Media types settings. */
    val streamsSectionEnabled: Boolean = true,

    /** S1781: the three players hold the screen unconditionally, so this covers every other screen. */
    val keepScreenAwakeOutsidePlayers: Boolean = false,
    val backgroundPlaybackEnabled: Boolean = false,
    /** S2166: the notification permission was refused, so the setting stayed off and says why. */
    val backgroundPlaybackNeedsNotifications: Boolean = false,

    /** S1718: watch screen auto-rotation preference. Default: false (forbidden per owner decision 2026-08-16). */
    val isAutoRotationEnabled: Boolean = false,

    /** S1718: true if watch has accelerometer sensor; when false, toggle row is hidden. */
    val hasAutoRotationSensor: Boolean = false,

    /** S2209: disable visual transition and decorative animations across the Wear OS app. */
    val isAnimationsDisabled: Boolean = false,

    /**
     * S3383: whether a file's action menu offers the two FileDO encryption operations.
     *
     * Opening a container is not behind it, so this false is "do not offer to write one", never
     * "do not read one".
     */
    val fileDoOperationsEnabled: Boolean = false,
    /** S2536: the charge at which this watch quietens itself. Judged locally, never sent as a verdict. */
    val powerSavingTrigger: PowerSavingTrigger = PowerSavingTrigger.DEFAULT,

    /** S1862: who decides that a finished voice note leaves the watch. Ships automatic (§6 item 1). */
    val voiceNoteSendPolicy: VoiceNoteSendPolicy = VoiceNoteSendPolicy.AUTOMATIC,

    /**
     * S2093 / ADR-3: what is drawn behind the screens. The mode is two values and so belongs on both
     * sides; the picture it selects stays a phone-side choice, because picking one means a gallery.
     */
    val backgroundMode: WearBackgroundMode = WearBackgroundMode.BRANDED_ANIMATION,

    /** S2522: the scheme the interface is drawn in. The default reproduces today's appearance. */
    val colorScheme: WearColorScheme = WearColorScheme.DEFAULT,
    /** S2773: the screen geometry in force, resolved from the stored choice and the build variant. */
    val geometryMode: WearGeometryMode = WearGeometryMode.STORE,
    /** S2773: false in the published variant, where the switch is deliberately withheld (ADR-3). */
    val offersGeometryModeSwitch: Boolean = false,
    /** S3256: clock and status overlay on dimmed screen. */
    val dimClockOverlayEnabled: Boolean = false,

    /**
     * S3362: whether this build reaches the user's own media. False withholds the rows that configure
     * the library, the players and their album art, none of which the store artifact carries.
     *
     * The three capability answers default to the WITHHOLDING side, as [offersGeometryModeSwitch]
     * already does: the view model fills all of them from `WearRestrictedCapabilities` before the
     * first frame, so a default is only ever read where nobody said, and there the store boundary is
     * the safe answer (`wear/config/store-boundary-policy.json` is an allowlist).
     */
    val offersMediaAccess: Boolean = false,

    /** S3362: whether this build has a recorder, and so a send policy for the notes it would write. */
    val offersVoiceRecording: Boolean = false,

    /**
     * S3362: whether content may cross the boundary of this watch. False withholds every row whose
     * action ends on the paired phone, which the store artifact has no path to.
     */
    val offersContentTransfer: Boolean = false,

    /** S2093: epoch-millis the two sides last agreed, or 0 when they never have. */
    val lastSyncedAtEpochMillis: Long = 0L,

    /** S2093: true while an exchange started from this watch is in flight. */
    val isSyncing: Boolean = false,
    
    // App info
    val appVersion: String = "",
    val buildNumber: String = ""
)

/**
 * The content types the user has left switched on.
 *
 * S2130: each category screen used to map its own token strings back onto these booleans in a private
 * `when`, so a type could be added to the vocabulary without a single screen failing to compile. This
 * is the one translation from settings into the language `BrowseCategoryCatalog` speaks.
 *
 * Whether an origin can present a type at all is a separate question, and the catalog's availability
 * predicate is what answers it: this set says what the user allows, not what the source can show.
 */
fun SettingsUiState.allowedContentTypes(): Set<WearContentType> = buildSet {
    if (isAudioEnabled) {
        add(WearContentType.MUSIC)
    }
    if (isVideoEnabled) {
        add(WearContentType.VIDEO)
    }
    if (isImagesEnabled) {
        add(WearContentType.IMAGE)
    }
    if (isDocumentsEnabled) {
        add(WearContentType.DOCUMENT)
    }
}
