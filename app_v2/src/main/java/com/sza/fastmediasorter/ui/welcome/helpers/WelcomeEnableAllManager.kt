package com.sza.fastmediasorter.ui.welcome.helpers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.domain.delivery.DeliverableDownloadRunner
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DownloadProgress
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.ApplyEnableAllSettingsUseCase
import com.sza.fastmediasorter.domain.usecase.EnsureAllFilesPredefinedResourceUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ImportStreamCatalogUseCase
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Orchestrates the welcome "Enable all" sequence (S0409) as a rotation-safe state machine: set profile
 * OTHER + enable every available function, then walk the permission system dialogs one at a time, then
 * the default-player system dialogs one at a time, then finish onboarding.
 *
 * Field-injected into WelcomeActivity (Hilt deps) and [attach]ed to it so it can own an
 * ActivityResultLauncher. [attach] re-wires the host references on every recreation; the default-player
 * stage progress (in-progress flag + current type index) survives rotation via
 * [onSaveInstanceState]/[onRestoreInstanceState], mirroring [WelcomePermissionsManager]. A recreation
 * mid permissions-stage also re-arms [WelcomePermissionsManager.reattachGrantAllCallback] so the finished
 * permissions run still chains into this stage instead of silently stalling (S0910).
 */
class WelcomeEnableAllManager @Inject constructor(
    private val enableAllSettingsUseCase: ApplyEnableAllSettingsUseCase,
    private val ensureAllFilesPredefinedResourceUseCase: EnsureAllFilesPredefinedResourceUseCase,
    private val mediaCapabilities: MediaCapabilities,
    private val capabilityAvailability: CapabilityAvailability,
    private val downloadRunner: DeliverableDownloadRunner,
    private val importStreamCatalogUseCase: ImportStreamCatalogUseCase,
    private val settingsRepository: SettingsRepository,
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {

    private var activity: FragmentActivity? = null
    private var permissionsManager: WelcomePermissionsManager? = null
    private var onFinished: (() -> Unit)? = null
    private var defaultPlayerLauncher: ActivityResultLauncher<Intent>? = null

    // True from start() until the sequence finishes; survives rotation so a recreated Activity resumes
    // the default-player walk instead of treating a returning dialog as a stray result.
    private var inProgress = false
    private var currentTypeIndex = 0

    // True once beginDefaultPlayerStage() has run at least once in this sequence. Distinguishes, on
    // recreation, "still mid permissions stage" (needs its completion callback reattached, S0910) from
    // "already walking the default-player types" (resume already works via currentTypeIndex + the
    // re-registered defaultPlayerLauncher).
    private var defaultPlayerStageStarted = false

    /** Wire the manager to its host. Called from setupViews on every (re)creation so the launcher and
     *  host references are valid again after a configuration change. */
    fun attach(
        activity: FragmentActivity,
        permissionsManager: WelcomePermissionsManager,
        onFinished: () -> Unit,
    ) {
        this.activity = activity
        this.permissionsManager = permissionsManager
        this.onFinished = onFinished
        // S0910: attach() runs from setupViews, which BaseActivity defers to a post{} - i.e. AFTER
        // onRestoreInstanceState has already restored inProgress/defaultPlayerStageStarted here and the
        // permissions manager's own grantAllInProgress. Re-arm the grant-all completion callback here
        // (NOT in onRestoreInstanceState, where permissionsManager was still null and the call no-op'd),
        // so a recreation mid permissions-stage still chains into the default-player stage, not stalls.
        if (inProgress && !defaultPlayerStageStarted) {
            permissionsManager.reattachGrantAllCallback { beginDefaultPlayerStage() }
        }
        defaultPlayerLauncher = activity.activityResultRegistry.register(
            KEY_DEFAULT_PLAYER,
            ActivityResultContracts.StartActivityForResult(),
        ) {
            // Result code is irrelevant (a chooser returns CANCELED on stock Android); a return just
            // means "advance to the next type".
            if (inProgress) onDefaultPlayerDialogReturned()
        }
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_IN_PROGRESS, inProgress)
        outState.putInt(STATE_TYPE_INDEX, currentTypeIndex)
        outState.putBoolean(STATE_DEFAULT_PLAYER_STAGE_STARTED, defaultPlayerStageStarted)
    }

    fun onRestoreInstanceState(state: Bundle?) {
        state ?: return
        inProgress = state.getBoolean(STATE_IN_PROGRESS, false)
        currentTypeIndex = state.getInt(STATE_TYPE_INDEX, 0)
        defaultPlayerStageStarted = state.getBoolean(STATE_DEFAULT_PLAYER_STAGE_STARTED, false)
        // S0910: the grant-all completion callback is re-armed in attach(), NOT here - attach() runs
        // via BaseActivity's deferred setupViews (a post{}), which is the first point permissionsManager
        // is non-null. Calling reattach here (before attach) silently no-op'd and the sequence stalled.
    }

    /** Kick off the full sequence. [applyProfileOther] sets the device profile to OTHER and persists it
     *  (host-supplied so the manager stays free of ViewModel coupling). */
    fun start(applyProfileOther: () -> Unit) {
        if (inProgress) return
        inProgress = true
        currentTypeIndex = 0
        applyProfileOther()
        val pm = permissionsManager ?: run { finishSequence(); return }
        // Apply settings strictly after the profile save and enqueue optional deliverables, then hand
        // off to the permission walk on the main thread (it drives Activity-owned launchers).
        appScope.launch {
            enableAllSettingsUseCase()
            // allFiles=true alone does not materialize the browsable "All Files" resource; the OTHER
            // profile's preset never implies it, so create it here to match "everything enabled".
            ensureAllFilesPredefinedResourceUseCase()
                .onFailure { Timber.w(it, "WelcomeEnableAllManager: failed to ensure All Files resource") }
            enqueueOptionalDeliverables()
            withContext(Dispatchers.Main) {
                pm.runGrantAll { beginDefaultPlayerStage() }
            }
        }
    }

    private fun beginDefaultPlayerStage() {
        defaultPlayerStageStarted = true
        // lite and other builds without default-player support skip straight to completion (S0409 crit. 9).
        if (!mediaCapabilities.supportsDefaultPlayer) {
            finishSequence()
            return
        }
        currentTypeIndex = 0
        launchCurrentDefaultPlayerType()
    }

    private fun launchCurrentDefaultPlayerType() {
        val act = activity ?: return finishSequence()
        val launcher = defaultPlayerLauncher ?: return finishSequence()
        val types = applicableMimeTypes()
        if (currentTypeIndex >= types.size) {
            finishSequence()
            return
        }
        DefaultPlayerHelper.openChooserOrFallbackForResult(act, launcher, types[currentTypeIndex])
    }

    private fun onDefaultPlayerDialogReturned() {
        currentTypeIndex += 1
        launchCurrentDefaultPlayerType()
    }

    private fun finishSequence() {
        inProgress = false
        currentTypeIndex = 0
        defaultPlayerStageStarted = false
        onFinished?.invoke()
    }

    /** MIME types offered for default-player registration, gated by capability so an unsupported type is
     *  silently skipped (S0409 §6.4). */
    private fun applicableMimeTypes(): List<String> = buildList {
        if (mediaCapabilities.supportsAudio) add("audio/*")
        if (mediaCapabilities.supportsVideo) add("video/*")
        if (mediaCapabilities.supportsImages) add("image/*")
        if (mediaCapabilities.supportsDocuments) add("application/pdf")
    }

    /** Enqueue OCR/translation engines where available and flip their settings only on a successful
     *  install (enable-only-after-install, S0386). Never blocks the sequence. */
    private fun enqueueOptionalDeliverables() {
        // S0971: OCR engines are bundled now, so the enqueue resolves to an immediate install on every
        // install source (including Play) - the former `!isPlayInstall()` gate is gone.
        if (capabilityAvailability.isOcrAvailable(context)) {
            enqueueAndEnableOnInstall(DeliverableSet.OCR_ENGINES) { it.copy(enableOcr = true) }
        }
        if (capabilityAvailability.isTranslationAvailable()) {
            enqueueAndEnableOnInstall(DeliverableSet.TRANSLATION) { it.copy(enableTranslation = true) }
        }
        if (capabilityAvailability.isStreamsAvailable()) {
            // S0575: enable Streams and best-effort fetch the catalog. The flag is set unconditionally
            // (not install-gated); a failed/empty import is a non-event - manual sources still work.
            appScope.launch {
                // S0876: transform overload serializes against the concurrent enableOcr/enableTranslation
                // writers below - a plain getSettings().first()+.copy()+updateSettings() here would race them.
                settingsRepository.updateSettings { it.copy(enableStreams = true) }
                importStreamCatalogUseCase()
            }
        }
    }

    private fun enqueueAndEnableOnInstall(
        set: DeliverableSet,
        enable: suspend (AppSettings) -> AppSettings,
    ) {
        downloadRunner.enqueue(set)
        appScope.launch {
            val terminal = downloadRunner.progressOf(set)
                .first { it is DownloadProgress.Installed || it is DownloadProgress.Failed }
            if (terminal is DownloadProgress.Installed) {
                // S0876: this is invoked once per deliverable set (OCR_ENGINES, TRANSLATION) as two
                // independent coroutines - the transform overload serializes their writes so an
                // install completing while the other's write is in flight cannot clobber it.
                settingsRepository.updateSettings(enable)
            }
        }
    }

    companion object {
        private const val STATE_IN_PROGRESS = "welcome_enable_all_in_progress"
        private const val STATE_TYPE_INDEX = "welcome_enable_all_type_index"
        private const val STATE_DEFAULT_PLAYER_STAGE_STARTED = "welcome_enable_all_default_player_stage_started"
        private const val KEY_DEFAULT_PLAYER = "welcome_enable_all_default_player"
    }
}
