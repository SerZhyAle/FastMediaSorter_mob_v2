package com.sza.fastmediasorter.ui.player.helpers

/**
 * legacy flavor Office viewer factory (S0301 Phase 02).
 *
 * Preserves the S0299 external-handoff path via [OfficeDocumentOpenManager]; legacy
 * has no in-app Office engine.
 */
class OfficeDocumentViewerProviderFactory {

    fun create(): OfficeDocumentViewerProvider = ExternalOfficeDocumentViewerProvider()

    /**
     * Market builds have no embedded Office engine (S0301 Phase 03): always the no-op host,
     * so the player keeps the S0299 external-handoff behavior.
     */
    fun createViewerHost(
        binding: com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding,
        coroutineScope: kotlinx.coroutines.CoroutineScope,
        callback: OfficeDocumentViewerHost.Callback,
    ): OfficeDocumentViewerHost = NoOpOfficeDocumentViewerHost
}
