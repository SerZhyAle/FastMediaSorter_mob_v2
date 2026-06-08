package com.sza.fastmediasorter.ui.player.helpers

/**
 * lite flavor Office viewer factory (S0301 Phase 02).
 *
 * The lite flavor does not classify Office files as documents (empty
 * `OfficeDocumentFamilyCatalog`), so this path is effectively unreachable. The factory
 * still returns the neutral external provider to keep the seam total and avoid any
 * `BuildConfig.IS_*` checks in shared code.
 */
class OfficeDocumentViewerProviderFactory {

    fun create(): OfficeDocumentViewerProvider = ExternalOfficeDocumentViewerProvider()

    /**
     * Market builds have no embedded Office engine (S0301 Phase 03): always the no-op host,
     * so the player keeps the S0299 external-handoff behavior.
     */
    fun createViewerHost(
        root: android.view.View,
        coroutineScope: kotlinx.coroutines.CoroutineScope,
        callback: OfficeDocumentViewerHost.Callback,
    ): OfficeDocumentViewerHost = NoOpOfficeDocumentViewerHost
}
