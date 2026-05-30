package com.sza.fastmediasorter.ui.player.helpers

/**
 * vr flavor Office viewer factory (S0301 Phase 02).
 *
 * Lives in `src/vrOnly/java` (mounted only by the `vr` flavor), NOT `src/vr/java`,
 * because `src/vr/java` is also mounted by the `noLegal` flavor and a peer there would
 * collide with the noLegal factory. Preserves the S0299 external-handoff path via
 * [OfficeDocumentOpenManager]; vr has no in-app Office engine.
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
