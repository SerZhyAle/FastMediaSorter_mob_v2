package com.sza.fastmediasorter.core.xr

import java.io.Serializable

/**
 * Shared transport and caller-side models for immersive XR launch flows.
 * The contract lives in src/main so both VR and phone flavors compile against the same types.
 */
enum class VrLaunchMode : Serializable {
    DIAGNOSTIC_PLAYLIST,
    FILE_URI,
}

enum class VrLaunchDeliveryMode : Serializable {
    ACTIVITY_RESULT,
    LEGACY_PANEL_RETURN,
}

enum class VrMediaType : Serializable {
    IMAGE,
    VIDEO,
    GIF,
}

enum class VrLaunchPoint : Serializable {
    PLAYER_BADGE,
    OVERFLOW_MENU,
    SETTINGS_TEST,
    BROWSE_TILE,
}

/**
 * Caller-owned playback state reserved for the flat-player round-trip.
 * S0295 defines the shared shape; concrete player restoration lands in S0292.
 */
data class PlayerStateSnapshot(
    val fileUriString: String? = null,
    val playlistIndex: Int = -1,
    val videoPositionMs: Long = 0L,
    val videoPlaybackSpeed: Float = 1.0f,
    val photoZoom: Float = 1.0f,
    val photoPanX: Float = 0.0f,
    val photoPanY: Float = 0.0f,
    val commandPanelVisible: Boolean = true,
    val fullscreen: Boolean = false,
    val slideshowState: String? = null,
    val sleepTimerRemainingMs: Long? = null,
    val audioFocusState: String? = null,
    val videoIsPlaying: Boolean = false,
    val videoVolume: Float = 1.0f,
) : Serializable {
    companion object {
        val EMPTY = PlayerStateSnapshot()
    }
}

sealed class VrPanelReturnTarget : Serializable {
    data class Player(
        val resourceId: Long,
        val windowId: String,
        val sourceFilePath: String,
        val playlistIndex: Int,
        val resumeIsPlaying: Boolean,
        val resumeSlideshowEnabled: Boolean,
        val detectedStereoModeName: String? = null,
        val snapshot: PlayerStateSnapshot = PlayerStateSnapshot.EMPTY,
    ) : VrPanelReturnTarget()

    data class Settings(
        val initialTab: Int,
    ) : VrPanelReturnTarget()
}

data class StartVrPlaybackRequest(
    val launchMode: VrLaunchMode,
    val fileUriString: String? = null,
    val mediaType: VrMediaType,
    val source: VrLaunchPoint,
    val snapshot: PlayerStateSnapshot? = null,
    val deliveryMode: VrLaunchDeliveryMode = VrLaunchDeliveryMode.ACTIVITY_RESULT,
) : Serializable {

    companion object {
        fun diagnosticPlaylist(
            source: VrLaunchPoint,
            snapshot: PlayerStateSnapshot? = null,
            deliveryMode: VrLaunchDeliveryMode = VrLaunchDeliveryMode.ACTIVITY_RESULT,
        ): StartVrPlaybackRequest = StartVrPlaybackRequest(
            launchMode = VrLaunchMode.DIAGNOSTIC_PLAYLIST,
            mediaType = VrMediaType.IMAGE,
            source = source,
            snapshot = snapshot,
            deliveryMode = deliveryMode,
        )
    }
}

data class VrLaunchInput(
    val launchMode: VrLaunchMode,
    val fileUriString: String? = null,
    val mediaType: VrMediaType,
    val deliveryMode: VrLaunchDeliveryMode = VrLaunchDeliveryMode.ACTIVITY_RESULT,
    val snapshot: PlayerStateSnapshot? = null,
) : Serializable {

    fun requireFileUriString(): String = requireNotNull(fileUriString) {
        "VrLaunchInput requires a fileUriString when launchMode=FILE_URI"
    }

    companion object {
        const val EXTRA_LAUNCH_INPUT = "extra_vr_launch_input"
        const val EXTRA_LAUNCH_RESULT = "extra_vr_launch_result"
        const val EXTRA_RETURN_TARGET = "extra_vr_return_target"

        fun fromRequest(request: StartVrPlaybackRequest): VrLaunchInput = VrLaunchInput(
            launchMode = request.launchMode,
            fileUriString = request.fileUriString,
            mediaType = request.mediaType,
            deliveryMode = request.deliveryMode,
            snapshot = request.snapshot,
        )
    }
}

enum class VrLaunchUnavailableReason : Serializable {
    NoRuntime,
    RuntimeDied,
    NotYetSupported,
    InvalidUri,
    DecoderFailed,
    DisabledByUser,
}

sealed class VrLaunchResult : Serializable {
    data object CompletedNormally : VrLaunchResult()
    data object CancelledByUser : VrLaunchResult()
    data class Crashed(val reason: String) : VrLaunchResult()
    data class Unavailable(val reason: VrLaunchUnavailableReason) : VrLaunchResult()
}
