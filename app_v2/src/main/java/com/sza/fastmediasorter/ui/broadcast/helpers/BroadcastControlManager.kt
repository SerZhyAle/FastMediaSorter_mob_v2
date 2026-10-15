package com.sza.fastmediasorter.ui.broadcast.helpers

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.broadcast.BroadcastLensChoice
import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.broadcast.BroadcastPreviewBinder
import com.sza.fastmediasorter.broadcast.BroadcastSourceController
import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.broadcast.ListBroadcastCameraLensesUseCase
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.databinding.ActivityBroadcastControlBinding
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.SendStreamToWatchUseCase
import com.sza.fastmediasorter.domain.usecase.streams.BuildWatchBroadcastHandoffUseCase
import com.sza.fastmediasorter.util.showBoundTo
import dagger.Lazy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
// S4261: one past detekt's ceiling with the watch hand-off form; this class is the screen's coordinator and every
// collaborator is already a single-purpose manager or use case, so a wrapper would only move the list.
@Suppress("LongParameterList")
class BroadcastControlManager @Inject constructor(
    private val controller: BroadcastSourceController,
    private val previewBinder: BroadcastPreviewBinder,
    private val listLenses: ListBroadcastCameraLensesUseCase,
    private val sharePanel: BroadcastSharePanelManager,
    private val blankScreenManager: BroadcastBlankScreenManager,
    private val settingsRepository: SettingsRepository,
    private val mediaCapabilities: MediaCapabilities,
    private val sendStreamToWatch: Lazy<SendStreamToWatchUseCase>,
    private val buildWatchBroadcastHandoff: Lazy<BuildWatchBroadcastHandoffUseCase>,
    private val settingsPanelManager: BroadcastSettingsPanelManager,
) {
    /** Takes no dependencies of its own, so it is constructed here instead of going through Hilt. */
    private val preStreamPreview = BroadcastPreStreamPreviewManager()
    private val sourceEntries = BroadcastSourceEntriesManager()

    /**
     * The mode a live switch is re-opening the session in, null while nothing switches. Until that mode
     * is on air the screen keeps its live layout: an audio-video switch passes through `Idle`, and the
     * idle layout would start the pre-start preview on the camera the new session is about to open.
     */
    private var switchTargetMode: BroadcastMode? = null

    private var selectedMode: BroadcastMode = BroadcastMode.AUDIO_ONLY
    private var selectedLensId: String? = null
    private var wearSendAvailable = false
    private var wearSendInProgress = false

    // Nullable and cleared in onDetach(): each launcher is registered on the activity's own
    // ActivityResultRegistry and its callback captures that activity, so a @Singleton holding
    // them would retain the last destroyed BroadcastControlActivity for the rest of the process.
    private var cameraPermissionLauncher: ActivityResultLauncher<String>? = null
    private var startPermissionLauncher: ActivityResultLauncher<String>? = null

    /** The permission the pending start is waiting for, so its denial can name the feature it blocked. */
    private var pendingStartPermission: String? = null

    /** One prompt per screen: a refused camera leaves the preview area empty instead of asking again. */
    private var cameraPermissionAsked = false

    fun setup(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        blankScreenManager.attach(activity, binding.root)
        cameraPermissionAsked = false
        keepPendingStartPermission(activity)
        cameraPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) refreshPreStreamPreview(activity, binding)
        }
        startPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            val permission = pendingStartPermission
            pendingStartPermission = null
            if (granted) {
                startSession(activity, binding)
            } else if (permission != null) {
                (controller.state.value as? BroadcastState.Live)?.let { live ->
                    checkModeChip(binding, live.descriptor.mode)
                }
                showStartPermissionDenied(binding, permission)
            }
        }
        setupPreStreamControls(activity, binding)
        setupLiveControls(activity, binding)
        sharePanel.setup(activity, binding)
        sourceEntries.setup(controller, activity, binding)

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    controller.state.collect { state ->
                        renderState(activity, binding, state)
                    }
                }
                launch {
                    controller.listenerCount.collect { count ->
                        renderListenerCount(activity, binding, count)
                    }
                }
                launch {
                    // The announcement that adds `auth` lands after the session is live, not with a state change.
                    sharePanel.shareChanges.collect {
                        sharePanel.render(activity, binding)
                        renderSendToWatch(binding, controller.state.value)
                    }
                }
                launch {
                    controller.feedbackSuppressed.collect { suppressed ->
                        renderFeedbackWarning(activity, binding, suppressed)
                    }
                }
                launch {
                    settingsRepository.getSettings().collect { settings ->
                        wearSendAvailable = settings.enableWearCompanion &&
                            mediaCapabilities.supportsWearCompanion
                        renderSendToWatch(binding, controller.state.value)
                    }
                }
            }
        }
    }

    private fun setupPreStreamControls(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        binding.cgBroadcastMode.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: R.id.chipModeAudioOnly
            selectedMode = when (checkedId) {
                R.id.chipModeVideoAudio -> BroadcastMode.VIDEO_AUDIO
                R.id.chipModeVideoOnly -> BroadcastMode.VIDEO_ONLY
                else -> BroadcastMode.AUDIO_ONLY
            }
            updateLensSelectionVisibility(activity, binding)
            requestLiveModeSwitch(activity, binding)
        }

        // The pre-start preview owns the camera the broadcast is about to open, so the service starts from
        // the close callback rather than from the tap (S3174).
        binding.btnStartBroadcast.setOnClickListener {
            // The port and title rows keep focus through the tap, so their edits reach the store only
            // once flushed - the stream would otherwise start on the previous port (S3234).
            settingsPanelManager.flushPending(binding.layoutBroadcastSettings)
            binding.btnStartBroadcast.isEnabled = false
            preStreamPreview.stop {
                // The commit only starts the write; the stream must read the port the user typed, so
                // the start waits for it to land (S3234).
                activity.lifecycleScope.launch {
                    settingsPanelManager.awaitPendingWrites()
                    binding.btnStartBroadcast.isEnabled = true
                    startSession(activity, binding)
                }
            }
        }

        settingsPanelManager.bind(activity, binding.layoutBroadcastSettings)
        updateLensSelectionVisibility(activity, binding)
    }

    /**
     * S3267: the screen used to start the service with no permission pre-flight at all, and a session
     * the service cannot honour is killed by the platform together with the process - so the grants the
     * chosen mode needs are collected here first, exactly as the main screen collects them. A live mode
     * switch goes through the same pre-flight: the new mode may need a grant the old one did not.
     */
    private fun startSession(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        val missing = missingStartPermission(activity)
        if (missing != null) {
            pendingStartPermission = missing
            startPermissionLauncher?.launch(missing)
        } else if (controller.state.value is BroadcastState.Live) {
            switchTargetMode = selectedMode
            setModeControlsEnabled(binding, false)
            controller.switchMode(selectedMode, selectedLensId)
            val message = binding.root.context.getString(R.string.broadcast_control_mode_switching)
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        } else {
            controller.start(selectedMode, selectedLensId)
        }
    }

    /**
     * A mode chip picked while live re-opens the session in that mode. The lens is resolved first, so
     * an audio session turning into video opens the lens the idle chips would have shown.
     */
    private fun requestLiveModeSwitch(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        val live = controller.state.value as? BroadcastState.Live ?: return
        if (switchTargetMode != null || live.descriptor.mode == selectedMode.name) return
        activity.lifecycleScope.launch {
            if (selectedMode != BroadcastMode.AUDIO_ONLY) {
                selectedLensId = live.activeLensId ?: resolveLensId(listLenses.listOptions())
            }
            startSession(activity, binding)
        }
    }

    @Suppress("ReturnCount")
    private fun missingStartPermission(activity: AppCompatActivity): String? {
        if (needsMicrophone() && !isGranted(activity, Manifest.permission.RECORD_AUDIO)) {
            return Manifest.permission.RECORD_AUDIO
        }
        if (selectedMode != BroadcastMode.AUDIO_ONLY && !isGranted(activity, Manifest.permission.CAMERA)) {
            return Manifest.permission.CAMERA
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !isGranted(activity, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            return Manifest.permission.POST_NOTIFICATIONS
        }
        return null
    }

    // A build without the camera foreground-service type keeps a video-only session foreground through
    // the microphone type, which Android 14 refuses until the permission is granted (S3154).
    private fun needsMicrophone(): Boolean = selectedMode != BroadcastMode.VIDEO_ONLY ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !controller.cameraSurvivesBackground)

    private fun isGranted(activity: AppCompatActivity, permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    private fun showStartPermissionDenied(
        binding: ActivityBroadcastControlBinding,
        permission: String
    ) {
        Timber.w("Broadcast start permission denied: %s", permission)
        val messageRes = when (permission) {
            Manifest.permission.RECORD_AUDIO -> R.string.broadcast_permission_microphone_required
            Manifest.permission.CAMERA -> R.string.broadcast_permission_camera_required
            else -> R.string.broadcast_permission_notifications_required
        }
        Snackbar.make(binding.root, binding.root.context.getString(messageRes), Snackbar.LENGTH_LONG).show()
    }

    private fun updateLensSelectionVisibility(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        val hasCamera = selectedMode != BroadcastMode.AUDIO_ONLY
        if (hasCamera) {
            activity.lifecycleScope.launch {
                val choice = listLenses.listOptions()
                selectedLensId = (controller.state.value as? BroadcastState.Live)?.activeLensId
                    ?: resolveLensId(choice)
                val lensChips = BroadcastLensChipsRenderer(
                    label = binding.tvLensHeader,
                    group = binding.cgLensSelection,
                    onLensSelected = { lensId ->
                        selectedLensId = lensId
                        persistLensId(activity, lensId)
                        if (controller.state.value is BroadcastState.Live) {
                            controller.selectLens(lensId)
                        } else {
                            refreshPreStreamPreview(activity, binding)
                        }
                    }
                )
                lensChips.render(
                    BroadcastEntryUi.LensUi(
                        options = choice.options,
                        selectedLensId = selectedLensId,
                        visible = choice.options.isNotEmpty()
                    )
                )
                refreshPreStreamPreview(activity, binding)
            }
        } else {
            binding.tvLensHeader.visibility = View.GONE
            binding.cgLensSelection.visibility = View.GONE
            selectedLensId = null
            refreshPreStreamPreview(activity, binding)
        }
    }

    /**
     * S3237: the lens choice outlives the screen. The in-memory pick wins while the screen is alive,
     * the persisted one restores it after a recreation or a relaunch, and either is kept only while the
     * phone still enumerates that lens - a lens that no longer exists would open nothing.
     */
    private suspend fun resolveLensId(choice: BroadcastLensChoice): String? {
        val stored = settingsRepository.getSettings().first().broadcast.cameraLensId
        return listOfNotNull(selectedLensId, stored)
            .firstOrNull { candidate -> choice.options.any { it.id == candidate } }
            ?: choice.initialLensId
    }

    private fun persistLensId(activity: AppCompatActivity, lensId: String) {
        activity.lifecycleScope.launch {
            settingsRepository.updateSettings { settings ->
                settings.copy(broadcast = settings.broadcast.copy(cameraLensId = lensId))
            }
        }
    }

    /**
     * S3174: the picture the user picks a lens by. It exists only while the screen is idle - a live
     * session's own camera belongs to the service, and its preview arrives through [previewBinder].
     */
    private fun refreshPreStreamPreview(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        val wantsCamera = selectedMode != BroadcastMode.AUDIO_ONLY
        val granted = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        when {
            controller.state.value is BroadcastState.Live || switchTargetMode != null -> Unit
            !wantsCamera -> {
                preStreamPreview.stop()
                binding.previewContainer.visibility = View.GONE
            }
            !granted -> {
                binding.previewContainer.visibility = View.GONE
                if (!cameraPermissionAsked) {
                    cameraPermissionAsked = true
                    cameraPermissionLauncher?.launch(Manifest.permission.CAMERA)
                }
            }
            else -> {
                binding.previewContainer.visibility = View.VISIBLE
                preStreamPreview.start(activity, binding.previewContainer, selectedLensId)
            }
        }
    }

    private fun setupLiveControls(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        binding.btnToggleCamera.setOnClickListener {
            controller.toggleCamera()
        }

        binding.btnToggleMic.setOnClickListener {
            controller.toggleMicrophone()
        }

        binding.btnSwitchCamera.setOnClickListener {
            showLensPicker(activity)
        }

        binding.btnScreenOff.setOnClickListener {
            blankScreenManager.onScreenOffRequested(activity)
        }

        binding.btnSendToWatch.setOnClickListener {
            sendBroadcastToWatch(activity, binding)
        }

        binding.btnStopBroadcast.setOnClickListener {
            switchTargetMode = null
            controller.stop()
        }
    }

    private fun sendBroadcastToWatch(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding,
    ) {
        val liveState = controller.state.value as? BroadcastState.Live ?: return
        if (!wearSendAvailable || wearSendInProgress) return

        wearSendInProgress = true
        Timber.d("S4261: live broadcast to watch")
        renderSendToWatch(binding, liveState)
        activity.lifecycleScope.launch {
            try {
                val descriptor = liveState.descriptor
                // S4261: a protected or server-reachable broadcast goes with its descriptor and the own pair.
                val form = buildWatchBroadcastHandoff.get().forLiveBroadcast(descriptor)
                val outcome = sendStreamToWatch.get()(
                    title = descriptor.title ?: descriptor.url,
                    url = descriptor.url,
                    mediaKind = descriptor.mode,
                    openNow = true,
                    handoff = form as? BuildWatchBroadcastHandoffUseCase.Result.Handoff,
                )
                Toast.makeText(activity, outcome.toMessageRes(), Toast.LENGTH_SHORT).show()
            } finally {
                wearSendInProgress = false
                renderSendToWatch(binding, controller.state.value)
            }
        }
    }

    @StringRes
    private fun SendStreamToWatchUseCase.Outcome.toMessageRes(): Int = when (this) {
        is SendStreamToWatchUseCase.Outcome.Delivered ->
            if (updated) R.string.stream_send_to_watch_updated else R.string.stream_send_to_watch_done

        SendStreamToWatchUseCase.Outcome.WatchUnavailable ->
            R.string.stream_send_to_watch_watch_unavailable

        SendStreamToWatchUseCase.Outcome.NoReply -> R.string.stream_send_to_watch_no_reply
        is SendStreamToWatchUseCase.Outcome.Error -> R.string.stream_send_to_watch_failed
        SendStreamToWatchUseCase.Outcome.Opened -> R.string.stream_open_on_watch_opened
        SendStreamToWatchUseCase.Outcome.WatchAppNotOpen -> R.string.stream_open_on_watch_app_closed
        SendStreamToWatchUseCase.Outcome.Unplayable -> R.string.stream_send_to_watch_unplayable
        SendStreamToWatchUseCase.Outcome.NoReplyToBroadcast -> R.string.stream_send_to_watch_no_reply_update
    }

    /** Every lens the phone offers, not a front/back flip (S3050 owner requirement). */
    private fun showLensPicker(activity: AppCompatActivity) {
        activity.lifecycleScope.launch {
            val choice = listLenses.listOptions()
            if (choice.options.isEmpty()) {
                Toast.makeText(activity, R.string.broadcast_lens_none, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val activeLensId = (controller.state.value as? BroadcastState.Live)?.activeLensId ?: choice.initialLensId
            val labels = BroadcastLensLabelFormatter.labels(choice.options) { facing ->
                activity.getString(BroadcastLensLabelFormatter.facingNameRes(facing))
            }
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.broadcast_control_lens_dialog_title)
                .setSingleChoiceItems(
                    labels.toTypedArray(),
                    choice.options.indexOfFirst { it.id == activeLensId },
                ) { dialog, which ->
                    val lensId = choice.options[which].id
                    controller.selectLens(lensId)
                    // S3237: the live picker changes the same choice the idle chips show, so it is stored
                    // by the same rule - otherwise stopping the session restores the lens before it.
                    selectedLensId = lensId
                    persistLensId(activity, lensId)
                    dialog.dismiss()
                }
                .showBoundTo(activity)
        }
    }

    private fun renderState(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding,
        state: BroadcastState
    ) {
        sourceEntries.render(controller, binding, state)
        when (state) {
            is BroadcastState.Live -> {
                val switching = switchTargetMode?.let { it.name != state.descriptor.mode } == true
                if (!switching) switchTargetMode = null
                preStreamPreview.stop()
                binding.btnStartBroadcast.visibility = View.GONE
                binding.layoutLiveControls.visibility = View.VISIBLE
                binding.layoutSharePanel.visibility = View.VISIBLE
                renderScreenRealEstate(binding, state)
                setModeControlsEnabled(binding, !switching)
                if (!switching) {
                    checkModeChip(binding, state.descriptor.mode)
                    checkLensChip(binding, state.activeLensId)
                }
                renderModeControls(binding, state.descriptor.mode, state.source != null)
                renderToggles(binding, state)
                renderSendToWatch(binding, state)
                sharePanel.render(activity, binding)
                previewBinder.attach(binding.previewContainer)
            }
            // The transient idle between the two services of an audio-video switch is not the end of the session.
            is BroadcastState.Idle -> if (switchTargetMode == null) renderPreStream(activity, binding, state)
            is BroadcastState.Failed -> {
                switchTargetMode = null
                renderPreStream(activity, binding, state)
                showFailure(binding, state)
            }
        }
    }

    /** Keeps the mode chips on the mode on air: a relaunch or a refused switch would show another one. */
    private fun checkModeChip(binding: ActivityBroadcastControlBinding, modeName: String) {
        val chipId = when (modeName) {
            BroadcastMode.VIDEO_AUDIO.name -> R.id.chipModeVideoAudio
            BroadcastMode.VIDEO_ONLY.name -> R.id.chipModeVideoOnly
            else -> R.id.chipModeAudioOnly
        }
        if (binding.cgBroadcastMode.checkedChipId != chipId) binding.cgBroadcastMode.check(chipId)
    }

    /** The lens dialog switches the lens too, so the chips follow the lens on air, not the last chip tap. */
    private fun checkLensChip(binding: ActivityBroadcastControlBinding, lensId: String?) {
        if (lensId == null) return
        for (i in 0 until binding.cgLensSelection.childCount) {
            val chip = binding.cgLensSelection.getChildAt(i) as? Chip ?: continue
            chip.isChecked = chip.tag == lensId
        }
    }

    private fun renderPreStream(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding,
        state: BroadcastState
    ) {
        previewBinder.detach()
        sharePanel.dismissQr()
        binding.btnStartBroadcast.visibility = View.VISIBLE
        binding.layoutLiveControls.visibility = View.GONE
        binding.layoutSharePanel.visibility = View.GONE
        renderScreenRealEstate(binding, state)
        setModeControlsEnabled(binding, true)
        refreshPreStreamPreview(activity, binding)
    }

    /**
     * S3235: the two blocks that belong to one half of the screen's life only. Keeping both on screen
     * at all times pushed the live controls and the sharing block below the fold in both orientations.
     */
    private fun renderScreenRealEstate(
        binding: ActivityBroadcastControlBinding,
        state: BroadcastState
    ) {
        binding.cardBroadcastSettings.visibility =
            if (BroadcastEntryUi.showsSettingsCard(state)) View.VISIBLE else View.GONE
        binding.tvListenerCount.visibility =
            if (BroadcastEntryUi.showsListenerCount(state)) View.VISIBLE else View.GONE
    }

    private fun setModeControlsEnabled(binding: ActivityBroadcastControlBinding, enabled: Boolean) {
        binding.cgBroadcastMode.isEnabled = enabled
        for (i in 0 until binding.cgBroadcastMode.childCount) {
            binding.cgBroadcastMode.getChildAt(i).isEnabled = enabled
        }
        binding.cgLensSelection.isEnabled = enabled
        for (i in 0 until binding.cgLensSelection.childCount) {
            binding.cgLensSelection.getChildAt(i).isEnabled = enabled
        }
    }

    /**
     * Every entry surface that starts a broadcast from this screen - tile, widget, panel, shortcut -
     * used to return to the idle layout with no word of the failure, because only the main screen
     * reported it (S3054).
     */
    private fun showFailure(
        binding: ActivityBroadcastControlBinding,
        state: BroadcastState.Failed,
    ) {
        Timber.w("Broadcast failed: %s (%s)", state.failure, state.detail)
        val message = binding.root.context.getString(BroadcastFailureMessage.resFor(state.failure))
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        // The state is static and its service is already gone, so nothing else retires it - without this
        // a rotation would replay the same message and the next start would begin from a failed state.
        controller.acknowledgeFailure()
    }

    private fun renderSendToWatch(
        binding: ActivityBroadcastControlBinding,
        state: BroadcastState,
    ) {
        // The watch plays a protected broadcast from the account's directory with the own pair (S4229).
        val isVisible = state is BroadcastState.Live && wearSendAvailable
        binding.btnSendToWatch.visibility = if (isVisible) View.VISIBLE else View.GONE
        binding.btnSendToWatch.isEnabled = isVisible && !wearSendInProgress
    }

    /**
     * A file or screen session has no camera or microphone of its own (S4234, S4233): the capture
     * toggles would do nothing, and blanking the app would blank the screen being shown.
     */
    private fun renderModeControls(
        binding: ActivityBroadcastControlBinding,
        modeName: String,
        isSourceSession: Boolean = false,
    ) {
        val hasCamera = !isSourceSession && modeName != BroadcastMode.AUDIO_ONLY.name
        val cameraVisibility = if (hasCamera) View.VISIBLE else View.GONE
        binding.previewContainer.visibility = cameraVisibility
        binding.btnToggleCamera.visibility = cameraVisibility
        binding.btnSwitchCamera.visibility = cameraVisibility
        binding.btnToggleMic.visibility =
            if (!isSourceSession && modeName == BroadcastMode.VIDEO_AUDIO.name) View.VISIBLE else View.GONE
        binding.btnScreenOff.visibility = if (isSourceSession) View.GONE else View.VISIBLE
        renderScreenOffLabel(binding)
    }

    /** The action blanks the app in store builds and truly sleeps the display where the camera survives it. */
    private fun renderScreenOffLabel(binding: ActivityBroadcastControlBinding) {
        val blanks = blankScreenManager.blanksInsteadOfSleeping()
        binding.btnScreenOff.setText(
            if (blanks) R.string.broadcast_control_blank_screen else R.string.broadcast_control_screen_off
        )
        binding.btnScreenOff.contentDescription = binding.root.context.getString(
            if (blanks) R.string.broadcast_control_blank_screen_cd else R.string.broadcast_control_screen_off_cd
        )
    }

    private fun renderToggles(binding: ActivityBroadcastControlBinding, state: BroadcastState.Live) {
        binding.btnToggleCamera.setText(
            if (state.cameraEnabled) R.string.broadcast_control_camera_on else R.string.broadcast_control_camera_off
        )
        binding.btnToggleCamera.setIconResource(R.drawable.ic_camera_capture)
        if (state.microphoneEnabled) {
            binding.btnToggleMic.setText(R.string.broadcast_control_mic_on)
            binding.btnToggleMic.setIconResource(R.drawable.ic_microphone)
        } else {
            binding.btnToggleMic.setText(R.string.broadcast_control_mic_off)
            binding.btnToggleMic.setIconResource(R.drawable.ic_microphone_off)
        }
    }

    private fun renderListenerCount(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding,
        count: Int
    ) {
        binding.tvListenerCount.text = activity.getString(R.string.broadcast_control_listeners, count)
        binding.tvListenerCount.contentDescription = activity.getString(R.string.broadcast_control_listeners_cd, count)
    }

    /**
     * S3349: names the acoustic feedback loop as the reason the sound dipped. Without it a guard doing
     * its job is indistinguishable from a microphone that stopped working.
     */
    private fun renderFeedbackWarning(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding,
        suppressed: Boolean
    ) {
        binding.tvFeedbackWarning.visibility = if (suppressed) View.VISIBLE else View.GONE
        binding.tvFeedbackWarning.contentDescription =
            activity.getString(R.string.broadcast_control_feedback_warning_cd)
    }

    /**
     * The singleton keeps the pending permission across a rotation but not across process death, and
     * the permission dialog survives both: a denial answered after a process death found nothing to
     * explain and stayed silent.
     */
    private fun keepPendingStartPermission(activity: AppCompatActivity) {
        val registry = activity.savedStateRegistry
        if (registry.isRestored && pendingStartPermission == null) {
            pendingStartPermission = registry.consumeRestoredStateForKey(START_PERMISSION_STATE_KEY)
                ?.getString(KEY_PENDING_START_PERMISSION)
        }
        registry.registerSavedStateProvider(START_PERMISSION_STATE_KEY) {
            bundleOf(KEY_PENDING_START_PERMISSION to pendingStartPermission)
        }
    }

    fun onDetach() {
        preStreamPreview.stop()
        previewBinder.detach()
        blankScreenManager.detach()
        sourceEntries.onDetach()
        // setup() re-registers every launcher on the next attach, so dropping them here loses nothing.
        sharePanel.onDetach()
        cameraPermissionLauncher = null
        startPermissionLauncher = null
    }

    companion object {
        private const val START_PERMISSION_STATE_KEY = "broadcast_control_pending_start_permission"
        private const val KEY_PENDING_START_PERMISSION = "permission"
    }
}
