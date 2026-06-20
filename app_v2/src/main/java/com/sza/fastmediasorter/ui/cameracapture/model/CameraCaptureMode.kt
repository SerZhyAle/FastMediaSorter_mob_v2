package com.sza.fastmediasorter.ui.cameracapture.model

/**
 * Capture mode for the unified in-app camera host.
 *
 * The mode is fixed by the launching entry point (S0545 §3.4): a photo command opens the host in
 * [PHOTO], a video command opens it in [VIDEO]. There is deliberately no in-screen switch in S0545,
 * so each caller keeps a strict result contract (e.g. an OCR-photo launch can never return a video).
 * In-screen mode switching for a dedicated "Camera" entry point is handled separately (S0563).
 */
enum class CameraCaptureMode {
    PHOTO,
    VIDEO,
    ;

    companion object {
        /** Defaults to [PHOTO] for legacy callers that pass no explicit mode. */
        fun fromName(name: String?): CameraCaptureMode =
            entries.firstOrNull { it.name == name } ?: PHOTO
    }
}
