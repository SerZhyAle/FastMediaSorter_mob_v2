package com.sza.fastmediasorter.ui.welcome.helpers

import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.databinding.PageWelcomeFunctionalityBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.usecase.streams.CountCatalogStreamSourcesUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ImportStreamCatalogUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject

// S1106: hard UI-side deadline so the onboarding stream-catalog import always resolves (done/failed),
// never hangs on a post-download step the OkHttp callTimeout does not cover.
private const val STREAMS_IMPORT_DEADLINE_MS = 90_000L

/**
 * Owns the Streams row of the welcome functionality page (S0575): visibility per the flavor capability,
 * the master flag, and the catalog download with its inline progress.
 *
 * Split out of [WelcomeFunctionalityController] with S1918, which added the second catalog collaborator
 * and pushed that constructor past the injection-count threshold.
 */
class WelcomeStreamsRowManager @Inject constructor(
    private val capabilityAvailability: CapabilityAvailability,
    private val importStreamCatalogUseCase: ImportStreamCatalogUseCase,
    private val countCatalogStreamSourcesUseCase: CountCatalogStreamSourcesUseCase,
) {

    // Held so a rebind (the page can be re-created) cancels the prior import before starting a new one.
    private var catalogJob: Job? = null

    /** Cancels an in-flight import. Called by the host before it re-binds the page. */
    fun cancel() {
        catalogJob?.cancel()
        catalogJob = null
    }

    fun bind(
        binding: PageWelcomeFunctionalityBinding,
        owner: LifecycleOwner,
        settings: AppSettings,
        persist: (suspend (AppSettings) -> AppSettings) -> Unit,
    ) {
        val row = binding.rowStreams
        if (!capabilityAvailability.isStreamsAvailable()) {
            row.visibility = View.GONE
            binding.groupStreamsProgress.visibility = View.GONE
            return
        }
        row.visibility = View.VISIBLE
        row.setCheckedSilently(settings.enableStreams)
        binding.groupStreamsProgress.visibility = View.GONE
        row.setOnCheckedChangeListener { isChecked ->
            if (isChecked) {
                persist { it.copy(enableStreams = true) }
                startCatalogImport(binding, owner)
            } else {
                cancel()
                binding.groupStreamsProgress.visibility = View.GONE
                persist { it.copy(enableStreams = false) }
            }
        }
        if (settings.enableStreams) {
            maybeImportForPreset(binding, owner)
        }
    }

    /**
     * S1918: the toggle can arrive already ON from the device-profile preset (S0471 applies it silently,
     * so the listener above never runs) and then onboarding would end with Streams enabled and an empty
     * channel list. Bind the import to the *state* instead: enabled plus an empty catalog starts the same
     * download, with the same progress row, that a tap would. The emptiness check is also the re-entry
     * guard - after a successful import the catalog is non-empty, so revisiting the page downloads nothing.
     */
    private fun maybeImportForPreset(binding: PageWelcomeFunctionalityBinding, owner: LifecycleOwner) {
        owner.lifecycleScope.launch {
            val catalogCount = countCatalogStreamSourcesUseCase()
            Timber.d("S1918: preset streams row bound, catalog rows=%d", catalogCount)
            if (catalogCount > 0) return@launch
            // The count is a suspending read, so the user can have switched the row OFF meanwhile;
            // starting the download then would contradict a choice already persisted.
            if (!binding.rowStreams.isChecked) return@launch
            Timber.i("WelcomeStreamsRowManager: streams enabled by preset, importing catalog")
            startCatalogImport(binding, owner)
        }
    }

    // Page-scoped best-effort import: if the user advances or it fails, the catalog can still be pulled
    // later from the Extensions screen; the master flag is already persisted on the app scope.
    private fun startCatalogImport(binding: PageWelcomeFunctionalityBinding, owner: LifecycleOwner) {
        val statusView = binding.tvStreamsStatus
        binding.groupStreamsProgress.visibility = View.VISIBLE
        binding.progressStreams.isIndeterminate = true
        statusView.text = statusView.context.getString(R.string.welcome_streams_catalog_downloading)

        catalogJob?.cancel()
        catalogJob = owner.lifecycleScope.launch {
            val result = try {
                withTimeout(STREAMS_IMPORT_DEADLINE_MS) { importStreamCatalogUseCase() }
            } catch (e: TimeoutCancellationException) {
                Timber.w(e, "Stream catalog import: UI deadline exceeded")
                ImportStreamCatalogUseCase.CatalogImportResult.Failure("timeout")
            }
            binding.progressStreams.isIndeterminate = false
            statusView.setText(
                if (result is ImportStreamCatalogUseCase.CatalogImportResult.Success) {
                    R.string.welcome_streams_catalog_done
                } else {
                    R.string.welcome_streams_catalog_failed
                }
            )
        }
    }
}
