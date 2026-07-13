package com.sza.fastmediasorter.ui.addresource

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.companion.CompanionConfigException
import com.sza.fastmediasorter.domain.usecase.companion.ImportCompanionConfigUseCase
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S0421: one-action import of a Windows-companion `.fmscfg` config picked via SAF.
 * Parse/credential/resource work lives in [ImportCompanionConfigUseCase]; this
 * coordinator only maps the outcome onto Add-Resource events.
 */
internal class AddResourceCompanionCoordinator(
    private val context: Context,
    private val importCompanionConfigUseCase: ImportCompanionConfigUseCase,
    private val bridge: AddResourceBridge
) {

    fun importFromUri(uri: Uri) {
        Timber.d("Companion config import started")
        bridge.vmScope.launch(bridge.ioDispatcher + bridge.exHandler) {
            bridge.markLoading(true)
            emitOutcome(importCompanionConfigUseCase(uri))
            bridge.markLoading(false)
        }
    }

    /** S0988: import from a QR payload string; same outcome surface as the file path. */
    fun importFromPayload(payload: String) {
        Timber.d("Companion QR import started")
        bridge.vmScope.launch(bridge.ioDispatcher + bridge.exHandler) {
            bridge.markLoading(true)
            emitOutcome(importCompanionConfigUseCase.importFromPayload(payload))
            bridge.markLoading(false)
        }
    }

    private fun emitOutcome(result: Result<com.sza.fastmediasorter.domain.usecase.companion.CompanionImportResult>) {
        result.onSuccess { r ->
            bridge.emit(
                AddResourceEvent.ShowMessage(
                    context.getString(
                        R.string.companion_import_success,
                        r.resourceNames.joinToString(),
                        "${r.host}:${r.port}"
                    )
                )
            )
            bridge.emit(AddResourceEvent.ResourcesAdded)
        }.onFailure { e ->
            bridge.emit(AddResourceEvent.ShowError(errorMessage(e)))
        }
    }

    private fun errorMessage(e: Throwable): String = when {
        e is CompanionConfigException && e.reason == CompanionConfigException.Reason.UNSUPPORTED_VERSION ->
            context.getString(R.string.companion_import_version_error)
        e is CompanionConfigException ->
            context.getString(R.string.companion_import_invalid_error)
        else ->
            context.getString(R.string.companion_import_failed)
    }
}
