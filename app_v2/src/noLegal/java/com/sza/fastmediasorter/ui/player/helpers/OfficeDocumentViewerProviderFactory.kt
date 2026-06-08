package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.data.common.OfficeDocumentFamilyCatalog
import com.sza.fastmediasorter.domain.model.MediaFile
import kotlinx.coroutines.CoroutineScope

/**
 * noLegal flavor Office viewer factory (S0301 Phase 02 seam, Phase 03 engine).
 *
 * noLegal is the only flavor that ships the embedded Office viewer. Phase 03 plugs in the
 * engine-backed [OfficeDocumentViewerManager] and flips the decision provider to internal
 * rendering for the supported families ([OfficeDocumentFamilyCatalog]). Unsupported / legacy
 * binary formats still fall back to the external-open path at the manager level.
 */
class OfficeDocumentViewerProviderFactory {

    fun create(): OfficeDocumentViewerProvider = NoLegalOfficeDocumentViewerProvider()

    /** Build the engine-backed, read-only in-app Office viewer host for noLegal. */
    fun createViewerHost(
        root: android.view.View,
        coroutineScope: CoroutineScope,
        callback: OfficeDocumentViewerHost.Callback,
    ): OfficeDocumentViewerHost = OfficeDocumentViewerManager(root, coroutineScope, callback)
}

/**
 * noLegal embedded-viewer provider (S0301 Phase 03).
 *
 * Reports internal display for any Office family the catalog supports; the viewer manager
 * performs the final per-format feasibility check and falls back to external open when the
 * engine cannot render the concrete file (e.g. legacy binary .doc / .xls / .ppt).
 */
class NoLegalOfficeDocumentViewerProvider : OfficeDocumentViewerProvider {

    override val supportsInternalViewing: Boolean = true

    override fun resolve(mediaFile: MediaFile): OfficeDocumentViewerSession {
        val ext = mediaFile.name.substringAfterLast('.', "").lowercase()
        val family = OfficeDocumentFamilyCatalog.extensionToFamily[ext]
        val outcome = if (family != null && family in OfficeDocumentFamilyCatalog.supportedFamilies) {
            OfficeDocumentViewerOutcome.DISPLAY_INTERNALLY
        } else {
            OfficeDocumentViewerOutcome.DELEGATE_EXTERNAL
        }
        return OfficeDocumentViewerSession(mediaFile = mediaFile, outcome = outcome)
    }
}
