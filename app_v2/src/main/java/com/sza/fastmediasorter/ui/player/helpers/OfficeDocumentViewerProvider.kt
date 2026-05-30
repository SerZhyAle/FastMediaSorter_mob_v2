package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.domain.model.MediaFile

/**
 * Flavor-safe seam for handling Office documents in the player stack (S0301 Phase 02).
 *
 * The provider is engine-neutral: it only decides *how* an Office file should be
 * presented in the current flavor, it never performs the presentation itself.
 * PlayerActivity and StandaloneViewManager own the actual UI side effects
 * (internal render, explicit fallback dialog, or external delegation).
 *
 * Flavor mapping:
 *   - standard / legacy / vr / photos / lite -> external delegation (S0299 behavior).
 *   - noLegal -> embedded viewer once the Phase 03 engine is plugged into this seam.
 *
 * Keeping the concrete engine out of [src/main] avoids leaking noLegal-only code into
 * market builds and removes any need for `BuildConfig.IS_*` checks in shared code.
 */
interface OfficeDocumentViewerProvider {

    /** True when this flavor can render Office documents inside the app shell. */
    val supportsInternalViewing: Boolean

    /**
     * Decide how the given Office [mediaFile] should be presented in this flavor.
     *
     * Pure decision: no Activity side effects, no dialogs, no navigation. The caller
     * inspects [OfficeDocumentViewerSession.outcome] and performs the matching UI action.
     */
    fun resolve(mediaFile: MediaFile): OfficeDocumentViewerSession
}

/**
 * S0299 external-handoff provider shared by all market flavors (S0301 Phase 02).
 *
 * Reports [OfficeDocumentViewerOutcome.DELEGATE_EXTERNAL] so the player keeps opening
 * Office documents through an installed external app via [OfficeDocumentOpenManager].
 * There is no in-app Office engine in market builds.
 */
class ExternalOfficeDocumentViewerProvider : OfficeDocumentViewerProvider {

    override val supportsInternalViewing: Boolean = false

    override fun resolve(mediaFile: MediaFile): OfficeDocumentViewerSession =
        OfficeDocumentViewerSession(
            mediaFile = mediaFile,
            outcome = OfficeDocumentViewerOutcome.DELEGATE_EXTERNAL,
        )
}
