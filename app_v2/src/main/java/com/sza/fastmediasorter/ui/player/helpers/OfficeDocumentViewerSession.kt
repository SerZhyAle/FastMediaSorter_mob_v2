package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.domain.model.MediaFile

/**
 * Result of [OfficeDocumentViewerProvider.resolve] (S0301 Phase 02).
 *
 * Describes the intended presentation for a single Office document without performing
 * it, so PlayerActivity and StandaloneViewManager can own the actual UI (internal
 * render, explicit fallback dialog, or external delegation).
 */
data class OfficeDocumentViewerSession(
    val mediaFile: MediaFile,
    val outcome: OfficeDocumentViewerOutcome,
)

/** Mutually exclusive presentation decisions for an Office document (S0301 Phase 02). */
enum class OfficeDocumentViewerOutcome {

    /** Render inside the app shell (noLegal embedded viewer, wired in Phase 03). */
    DISPLAY_INTERNALLY,

    /** Show the explicit `external app / share / cancel` fallback dialog (Phase 05 UI). */
    SHOW_FALLBACK_DIALOG,

    /** Hand off directly to an external app (S0299 behavior for market flavors). */
    DELEGATE_EXTERNAL,
}
