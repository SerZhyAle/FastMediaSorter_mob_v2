package com.sza.fastmediasorter.ui.addresource

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.companion.CompanionConfigException
import com.sza.fastmediasorter.domain.usecase.companion.CompanionImportResult
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

    // Set when an import came back PASSWORD_REQUIRED: re-runs that parsed config with the typed password,
    // so the UI never holds the config (and its data-layer type) itself.
    @Volatile
    private var pendingPasswordImport: ((String) -> Unit)? = null

    /** Completes the import that asked for a password; a no-op when none is pending. */
    fun importWithPassword(password: String) {
        val resume = pendingPasswordImport ?: return
        pendingPasswordImport = null
        resume(password)
    }

    private fun emitOutcome(result: Result<CompanionImportResult>) {
        result.onSuccess { r ->
            bridge.emit(AddResourceEvent.ShowMessage(buildSummary(r)))
            // S1423: a companion config import restores many resources at once and is not the
            // single-resource creation act this ticket pins for, so it names no subject to pin.
            bridge.emit(AddResourceEvent.ResourcesAdded(emptyList()))
        }.onFailure { e ->
            val pending = (e as? CompanionConfigException)
                ?.takeIf { it.reason == CompanionConfigException.Reason.PASSWORD_REQUIRED }
                ?.config
            if (pending == null) {
                bridge.emit(AddResourceEvent.ShowError(errorMessage(e)))
                return@onFailure
            }
            pendingPasswordImport = { password ->
                bridge.vmScope.launch(bridge.ioDispatcher + bridge.exHandler) {
                    bridge.markLoading(true)
                    val withPassword = pending.copy(password = password)
                    emitOutcome(importCompanionConfigUseCase.import(withPassword, matchExistingByPath = true))
                    bridge.markLoading(false)
                }
            }
            val host = pending.accessPaths?.firstOrNull()?.host.orEmpty()
            bridge.emit(
                AddResourceEvent.CompanionPasswordRequired(
                    resourceName = pending.resourceName ?: host,
                    host = host,
                    rootCount = pending.roots?.size ?: 0,
                    hasFingerprint = !pending.hostKeyFingerprintSha256.isNullOrBlank()
                )
            )
        }
    }

    /**
     * S1012: builds the in-app import toast. Reports how many resources were added and updated; when
     * exactly one resource was affected in total it names that resource (added vs updated). A byte-
     * identical re-import affects nothing and reports "no changes".
     */
    private fun buildSummary(r: CompanionImportResult): String {
        val added = r.addedNames.size
        val updated = r.updatedNames.size
        val res = context.resources
        return when {
            added + updated == 0 -> context.getString(R.string.companion_import_no_changes)
            added + updated == 1 && added == 1 ->
                context.getString(R.string.companion_import_added_one, r.addedNames.first())
            added + updated == 1 ->
                context.getString(R.string.companion_import_updated_one, r.updatedNames.first())
            added > 0 && updated > 0 -> context.getString(
                R.string.companion_import_summary,
                res.getQuantityString(R.plurals.added_n_resources, added, added),
                res.getQuantityString(R.plurals.updated_n_resources, updated, updated)
            )
            added > 0 -> res.getQuantityString(R.plurals.added_n_resources, added, added)
            else -> res.getQuantityString(R.plurals.updated_n_resources, updated, updated)
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
