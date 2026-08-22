package com.sza.fastmediasorter.ui.welcome.helpers

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController
import com.sza.fastmediasorter.databinding.PageWelcomeFunctionalityBinding
import com.sza.fastmediasorter.domain.delivery.DeliverableDownloadRunner
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DownloadProgress
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.EnsureAllFilesPredefinedResourceUseCase
import com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow
import com.sza.fastmediasorter.ui.delivery.ExtensionsManagerFragment
import com.sza.fastmediasorter.ui.settings.exitAllFilesForManualSupportToggle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Owns all logic of the welcome functionality page (S0400): which capability toggles are visible
 * (per the S0396 availability contract + install source), reading post-preset defaults, persisting
 * each toggle to settings, and driving OCR/translation deliverable downloads with inline progress
 * (enable the capability only after a successful install - S0386 invariant).
 *
 * Hilt-injected into WelcomeActivity and wired into the page via [bind]; keeping the heavy logic here
 * (not in the ViewHolder/Activity) follows Clean+MVVM and lets the page stay a thin renderer.
 *
 * The VR toggle is intentionally out of scope for this pass - it needs the src/vr XR master-pref
 * wiring and is deferred to a follow-up.
 */
class WelcomeFunctionalityController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val downloadRunner: DeliverableDownloadRunner,
    private val ensureAllFilesPredefinedResourceUseCase: EnsureAllFilesPredefinedResourceUseCase,
    private val capabilityAvailability: CapabilityAvailability,
    private val streamsRowManager: WelcomeStreamsRowManager,
    private val mediaCapabilities: MediaCapabilities,
    private val screenGestureControllers: Set<@JvmSuppressWildcards ScreenGestureOverlayController>,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {

    // Held so a rebind (the page can be re-created) cancels the prior collections before re-collecting.
    private var ocrProgressJob: Job? = null
    private var translationProgressJob: Job? = null

    // Host-owned launcher for the overlay-permission system screen; re-attached on each Activity
    // recreation. The manager is rebuilt on every page bind, so the result callback routes through it.
    private var gesturePermissionLauncher: ActivityResultLauncher<Intent>? = null
    private var gesturesManager: WelcomeGesturesManager? = null

    fun attachGesturePermissionLauncher(launcher: ActivityResultLauncher<Intent>) {
        gesturePermissionLauncher = launcher
    }

    fun onGesturePermissionResult() {
        gesturesManager?.onOverlayPermissionResult()
    }

    fun bind(binding: PageWelcomeFunctionalityBinding, owner: LifecycleOwner) {
        ocrProgressJob?.cancel()
        translationProgressJob?.cancel()
        ocrProgressJob = null
        translationProgressJob = null
        streamsRowManager.cancel()

        // Post-preset defaults: a one-shot read of the current settings drives the initial checked
        // state. The welcome page is the only writer during onboarding, so a live observer is not
        // needed and would fight the "enable only after install" rule for OCR/translation rows.
        owner.lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            bindRows(binding, owner, settings)
        }
    }

    private fun bindRows(
        binding: PageWelcomeFunctionalityBinding,
        owner: LifecycleOwner,
        settings: AppSettings,
    ) {
        bindFileManagerRow(binding.rowFileManager, settings)
        bindAudioRow(binding.rowAudio, settings)
        bindVideoRow(binding.rowVideo, settings)
        bindDocumentsRow(binding.rowDocuments, settings)
        bindOcrRow(binding, owner, settings)
        bindTranslationRow(binding, owner, settings)
        streamsRowManager.bind(binding, owner, settings, ::persist)
        bindStatisticsRow(binding.rowStatistics, settings)
        bindGesturesRow(binding, owner, settings)
        bindElementsButton(binding, owner)
    }

    // Hidden when the host launcher is not attached, the owner is not an Activity, or the flavor has
    // no screen-gesture capability (empty controller set - handled inside the manager). Enabling routes
    // through the same permission flow as Settings; the per-direction bindings are seeded at first run.
    private fun bindGesturesRow(
        binding: PageWelcomeFunctionalityBinding,
        owner: LifecycleOwner,
        settings: AppSettings,
    ) {
        val activity = owner as? FragmentActivity
        val launcher = gesturePermissionLauncher
        if (activity == null || launcher == null) {
            binding.rowGestures.visibility = View.GONE
            return
        }
        gesturesManager = WelcomeGesturesManager(
            row = binding.rowGestures,
            screenGestureControllers = screenGestureControllers,
            overlayPermissionLauncher = launcher,
            activity = activity,
            initialEnabled = settings.gestureOverlayEnabled,
            persist = ::persist,
        ).also { it.setup() }
    }

    private fun bindStatisticsRow(row: SettingsToggleRow, settings: AppSettings) {
        row.setCheckedSilently(settings.enableStatistics)
        row.setOnCheckedChangeListener { isChecked ->
            persist { it.copy(enableStatistics = isChecked) }
        }
    }

    private fun bindFileManagerRow(row: SettingsToggleRow, settings: AppSettings) {
        row.visibility = View.VISIBLE
        row.setCheckedSilently(settings.allFiles)
        row.setOnCheckedChangeListener { isChecked ->
            // allFiles ON implicitly enables every media type via getGloballyEnabledMediaTypes();
            // turning it OFF leaves the individual type flags untouched (spec).
            persist { it.copy(allFiles = isChecked) }
            if (isChecked) {
                // S0471: Welcome has no separate "create resource" affordance like Settings, so enabling
                // the All Files shortcut here also materializes the browsable predefined resource -
                // mirroring the Enable-all sequence. Idempotent: a no-op when it already exists.
                appScope.launch {
                    ensureAllFilesPredefinedResourceUseCase()
                        .onFailure { Timber.w(it, "WelcomeFunctionalityController: failed to ensure All Files resource") }
                }
            }
        }
    }

    private fun bindAudioRow(row: SettingsToggleRow, settings: AppSettings) {
        if (!mediaCapabilities.supportsAudio) {
            row.visibility = View.GONE
            return
        }
        row.visibility = View.VISIBLE
        row.setCheckedSilently(settings.supportAudio)
        row.setOnCheckedChangeListener { isChecked ->
            persist { it.exitAllFilesForManualSupportToggle(isChecked).copy(supportAudio = isChecked) }
        }
    }

    private fun bindVideoRow(row: SettingsToggleRow, settings: AppSettings) {
        if (!mediaCapabilities.supportsVideo) {
            row.visibility = View.GONE
            return
        }
        row.visibility = View.VISIBLE
        row.setCheckedSilently(settings.supportVideos)
        row.setOnCheckedChangeListener { isChecked ->
            persist { it.exitAllFilesForManualSupportToggle(isChecked).copy(supportVideos = isChecked) }
        }
    }

    private fun bindDocumentsRow(row: SettingsToggleRow, settings: AppSettings) {
        if (!mediaCapabilities.supportsDocuments) {
            row.visibility = View.GONE
            return
        }
        row.visibility = View.VISIBLE
        // Master over four document fields: checked when any of them is on.
        row.setCheckedSilently(documentsChecked(settings))
        row.setOnCheckedChangeListener { isChecked ->
            val supportEpub = isChecked && mediaCapabilities.supportsEpub
            persist { current ->
                val base = if (isChecked) current else current.exitAllFilesForManualSupportToggle(false)
                base.copy(
                    supportText = isChecked,
                    supportPdf = isChecked,
                    supportEpub = supportEpub,
                    supportOfficeDocuments = isChecked,
                )
            }
        }
    }

    private fun documentsChecked(settings: AppSettings): Boolean =
        settings.supportText || settings.supportPdf || settings.supportEpub || settings.supportOfficeDocuments

    private fun bindOcrRow(
        binding: PageWelcomeFunctionalityBinding,
        owner: LifecycleOwner,
        settings: AppSettings,
    ) {
        val row = binding.rowOcr
        if (!isOcrVisible()) {
            row.visibility = View.GONE
            binding.groupOcrProgress.visibility = View.GONE
            return
        }
        row.visibility = View.VISIBLE
        row.setCheckedSilently(settings.enableOcr)
        binding.groupOcrProgress.visibility = View.GONE
        row.setOnCheckedChangeListener { isChecked ->
            if (isChecked) {
                startDeliverableDownload(
                    set = DeliverableSet.OCR_ENGINES,
                    owner = owner,
                    progressGroup = binding.groupOcrProgress,
                    progressBar = binding.progressOcr,
                    statusView = binding.tvOcrStatus,
                    onInstalled = { persist { it.copy(enableOcr = true) } },
                    setJob = { ocrProgressJob = it },
                )
            } else {
                binding.groupOcrProgress.visibility = View.GONE
                ocrProgressJob?.cancel()
                ocrProgressJob = null
                persist { it.copy(enableOcr = false) }
            }
        }
    }

    private fun bindTranslationRow(
        binding: PageWelcomeFunctionalityBinding,
        owner: LifecycleOwner,
        settings: AppSettings,
    ) {
        val row = binding.rowTranslation
        if (!isTranslationVisible()) {
            row.visibility = View.GONE
            binding.groupTranslationProgress.visibility = View.GONE
            return
        }
        row.visibility = View.VISIBLE
        row.setCheckedSilently(settings.enableTranslation)
        binding.groupTranslationProgress.visibility = View.GONE
        row.setOnCheckedChangeListener { isChecked ->
            if (isChecked) {
                startDeliverableDownload(
                    set = DeliverableSet.TRANSLATION,
                    owner = owner,
                    progressGroup = binding.groupTranslationProgress,
                    progressBar = binding.progressTranslation,
                    statusView = binding.tvTranslationStatus,
                    onInstalled = { persist { it.copy(enableTranslation = true) } },
                    setJob = { translationProgressJob = it },
                )
            } else {
                binding.groupTranslationProgress.visibility = View.GONE
                translationProgressJob?.cancel()
                translationProgressJob = null
                persist { it.copy(enableTranslation = false) }
            }
        }
    }

    // Show the translation row on all flavors where translation is supported.
    // Standard and legacy flavors can use the Google Play DFM or fall back to the GitHub mirror.
    private fun isTranslationVisible(): Boolean =
        capabilityAvailability.isTranslationAvailable()

    // S0575: enabling Streams commits the master flag immediately; the source-catalog fetch is offered
    // but optional - a refusal or failure leaves the feature ON (manual sources still work) and never
    // blocks navigation. This is deliberately NOT the OCR/translation install-then-persist pattern.
    // S0971: OCR engines are now bundled in the APK (no GitHub download), so they work on a Play
    // install too - the former `!isPlayInstall()` gate is gone; compile-time OCR capability is the
    // only axis left.
    private fun isOcrVisible(): Boolean =
        capabilityAvailability.isOcrAvailable(context)

    private fun bindElementsButton(binding: PageWelcomeFunctionalityBinding, owner: LifecycleOwner) {
        val button = binding.btnElements
        if (!capabilityAvailability.isExtensionsScreenAvailable()) {
            button.visibility = View.GONE
            return
        }
        button.visibility = View.VISIBLE
        button.setOnClickListener {
            val activity = owner as? FragmentActivity ?: return@setOnClickListener
            activity.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, ExtensionsManagerFragment(), ExtensionsManagerFragment.TAG)
                .addToBackStack(null)
                .commit()
        }
    }

    /**
     * Enqueues [set] and reflects its progress on the row's inline group. The capability setting is
     * written true only on [DownloadProgress.Installed] (S0386 invariant); a failure leaves it OFF
     * and arms a retry (re-enqueue) on the next toggle-ON. Navigation is never blocked - this only
     * drives the inline UI.
     */
    private fun startDeliverableDownload(
        set: DeliverableSet,
        owner: LifecycleOwner,
        progressGroup: View,
        progressBar: ProgressBar,
        statusView: TextView,
        onInstalled: () -> Unit,
        setJob: (Job?) -> Unit,
    ) {
        progressGroup.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        statusView.text = statusView.context.getString(R.string.welcome_func_downloading, 0)

        downloadRunner.enqueue(set)
        Timber.i("WelcomeFunctionalityController: enqueued deliverable %s", set)

        // repeatOnLifecycle (not the bare collectOnLifecycle helper) because the returned Job must be
        // retained so a rebind or a toggle-OFF can cancel this collection before starting a new one.
        val job = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                downloadRunner.progressOf(set).collect { progress ->
                    renderProgress(set, progress, progressBar, statusView)
                    if (progress is DownloadProgress.Installed) {
                        onInstalled()
                    }
                }
            }
        }
        setJob(job)
    }

    private fun renderProgress(
        set: DeliverableSet,
        progress: DownloadProgress,
        progressBar: ProgressBar,
        statusView: TextView,
    ) {
        when (progress) {
            is DownloadProgress.Queued, is DownloadProgress.Verifying -> {
                progressBar.isIndeterminate = true
                statusView.text = statusView.context.getString(R.string.welcome_func_downloading, 0)
            }
            is DownloadProgress.Running -> {
                progressBar.isIndeterminate = false
                progressBar.progress = progress.percent
                statusView.text = statusView.context.getString(R.string.welcome_func_downloading, progress.percent)
            }
            is DownloadProgress.Installed -> {
                progressBar.isIndeterminate = false
                progressBar.progress = 100
                statusView.text = statusView.context.getString(R.string.welcome_func_download_done)
            }
            is DownloadProgress.Failed -> {
                progressBar.isIndeterminate = false
                statusView.text = statusView.context.getString(R.string.welcome_func_download_failed)
                Timber.i("WelcomeFunctionalityController: deliverable %s failed - %s", set, progress.reason)
            }
        }
    }

    // Persists settings as a read-modify-write of the latest snapshot so concurrent toggle taps do not
    // clobber each other's fields. Runs on the application scope (IO) so the write survives the page
    // being torn down right after a tap - the settings change must not be lost on navigation.
    // S0876: the transform overload is mutex-serialized against every other transform() writer
    // (see SettingsRepository KDoc) - a plain getSettings().first()+updateSettings() pair here could
    // race the Welcome enable-all flow's concurrent deliverable-install writers.
    private fun persist(transform: suspend (AppSettings) -> AppSettings) {
        appScope.launch {
            settingsRepository.updateSettings(transform)
        }
    }
}
