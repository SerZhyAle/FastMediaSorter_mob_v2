package com.sza.fastmediasorter.ui.broadcast.helpers

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.WriterException
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.broadcast.BroadcastLensChoice
import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.broadcast.BroadcastPreviewBinder
import com.sza.fastmediasorter.broadcast.BroadcastSourceController
import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.broadcast.ListBroadcastCameraLensesUseCase
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.share.SharePayload
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import com.sza.fastmediasorter.databinding.ActivityBroadcastControlBinding
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.SendStreamToWatchUseCase
import com.sza.fastmediasorter.ui.common.support.SupportIntentFactory
import com.sza.fastmediasorter.ui.companionimport.qr.QrCodeEncoder
import com.sza.fastmediasorter.util.CaptureFileNamer
import com.sza.fastmediasorter.util.showBoundTo
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class BroadcastControlManager @Inject constructor(
    private val controller: BroadcastSourceController,
    private val previewBinder: BroadcastPreviewBinder,
    private val listLenses: ListBroadcastCameraLensesUseCase,
    private val shareManager: BroadcastShareManager,
    private val blankScreenManager: BroadcastBlankScreenManager,
    private val settingsRepository: SettingsRepository,
    private val mediaCapabilities: MediaCapabilities,
    private val sendStreamToWatch: Lazy<SendStreamToWatchUseCase>,
    private val settingsPanelManager: BroadcastSettingsPanelManager,
) {
    /** Takes no dependencies of its own, so it is constructed here instead of going through Hilt. */
    private val preStreamPreview = BroadcastPreStreamPreviewManager()

    private var selectedMode: BroadcastMode = BroadcastMode.AUDIO_ONLY
    private var selectedLensId: String? = null
    private var wearSendAvailable = false
    private var wearSendInProgress = false
    private lateinit var exportFileLauncher: ActivityResultLauncher<String>
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var startPermissionLauncher: ActivityResultLauncher<String>

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
                startSession(activity)
            } else if (permission != null) {
                showStartPermissionDenied(binding, permission)
            }
        }
        setupPreStreamControls(activity, binding)
        setupLiveControls(activity, binding)
        setupSharePanel(activity, binding)

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
                    startSession(activity)
                }
            }
        }

        settingsPanelManager.bind(activity, binding.layoutBroadcastSettings)
        updateLensSelectionVisibility(activity, binding)
    }

    /**
     * S3267: the screen used to start the service with no permission pre-flight at all, and a session
     * the service cannot honour is killed by the platform together with the process - so the grants the
     * chosen mode needs are collected here first, exactly as the main screen collects them.
     */
    private fun startSession(activity: AppCompatActivity) {
        val missing = missingStartPermission(activity)
        if (missing != null) {
            pendingStartPermission = missing
            startPermissionLauncher.launch(missing)
            return
        }
        controller.start(selectedMode, selectedLensId)
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
                selectedLensId = resolveLensId(choice)
                val lensChips = BroadcastLensChipsRenderer(
                    label = binding.tvLensHeader,
                    group = binding.cgLensSelection,
                    onLensSelected = { lensId ->
                        selectedLensId = lensId
                        persistLensId(activity, lensId)
                        refreshPreStreamPreview(activity, binding)
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
            controller.state.value is BroadcastState.Live -> Unit
            !wantsCamera -> {
                preStreamPreview.stop()
                binding.previewContainer.visibility = View.GONE
            }
            !granted -> {
                binding.previewContainer.visibility = View.GONE
                if (!cameraPermissionAsked) {
                    cameraPermissionAsked = true
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
        renderSendToWatch(binding, liveState)
        activity.lifecycleScope.launch {
            try {
                val descriptor = liveState.descriptor
                val outcome = sendStreamToWatch.get()(
                    title = descriptor.title ?: descriptor.url,
                    url = descriptor.url,
                    mediaKind = descriptor.mode,
                    openNow = true,
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
    }

    private fun setupSharePanel(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        exportFileLauncher = activity.registerForActivityResult(
            ActivityResultContracts.CreateDocument(BROADCAST_DESCRIPTOR_MIME_TYPE)
        ) { uri ->
            if (uri != null) writeBroadcastDescriptor(activity, uri)
        }

        binding.btnBroadcastHelp.setOnClickListener { openViewerGuide(activity) }
    }

    /** S3175: the guide the broadcaster forwards to whoever receives the link, QR or file. */
    private fun openViewerGuide(activity: AppCompatActivity) {
        val url = SupportIntentFactory.broadcastGuideUrl(activity)
        try {
            activity.startActivity(SupportIntentFactory.openUrl(url))
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No browser to open the broadcast viewer guide")
            Toast.makeText(activity, R.string.settings_no_browser_for_docs, Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun renderShareData(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        val liveState = controller.state.value as? BroadcastState.Live ?: return
        val descriptor = liveState.descriptor
        val url = descriptor.url

        binding.tvShareUrl.text = url

        binding.btnCopyUrl.setOnClickListener {
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Broadcast URL", url)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(activity, R.string.broadcast_share_saved, Toast.LENGTH_SHORT).show()
        }

        binding.btnShareSend.setOnClickListener {
            val link = shareManager.generateShareLink(liveState)
            SystemShareInvoker.invoke(
                activity,
                SharePayload.Text(link),
                chooserTitle = activity.getString(R.string.broadcast_share_send_chooser_title)
            )
        }

        binding.btnExportFile.setOnClickListener {
            // The stream URL is stable across exports, so a URL-derived name made every save collide
            // with the previous file and the system silently appended " (1)" (S3240).
            val descriptorName = CaptureFileNamer.shared.allocate(
                CaptureFileNamer.CaptureKind.BROADCAST,
                BROADCAST_DESCRIPTOR_EXTENSION
            )
            exportFileLauncher.launch(descriptorName)
        }

        val payload = shareManager.generateQrPayload(liveState)
        val metrics = activity.resources.displayMetrics
        val size = (min(metrics.widthPixels, metrics.heightPixels) * QR_SIZE_FRACTION)
            .toInt()
            .coerceIn(QR_SIZE_MIN_PX, QR_SIZE_MAX_PX)
        try {
            binding.ivShareQr.setImageBitmap(QrCodeEncoder.encode(payload, size))
        } catch (e: WriterException) {
            Timber.w(e, "Failed to encode QR in BroadcastControlManager")
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Illegal argument when encoding QR in BroadcastControlManager")
        }
    }

    private fun writeBroadcastDescriptor(activity: AppCompatActivity, uri: Uri) {
        val liveState = controller.state.value as? BroadcastState.Live ?: return
        val descriptor = liveState.descriptor
        val json = shareManager.generateJsonPayload(liveState)
        activity.lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    activity.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { writer ->
                        writer.write(json)
                    } ?: return@runCatching false
                    true
                }.onFailure { error ->
                    Timber.e(error, "Writing broadcast descriptor failed")
                }.getOrDefault(false)
            }
            Toast.makeText(
                activity,
                if (saved) R.string.broadcast_share_saved else R.string.broadcast_share_save_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
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
        when (state) {
            is BroadcastState.Live -> {
                preStreamPreview.stop()
                binding.btnStartBroadcast.visibility = View.GONE
                binding.layoutLiveControls.visibility = View.VISIBLE
                binding.layoutSharePanel.visibility = View.VISIBLE
                renderScreenRealEstate(binding, state)
                setModeControlsEnabled(binding, false)
                renderModeControls(binding, state.descriptor.mode)
                renderToggles(binding, state)
                renderSendToWatch(binding, state)
                renderShareData(activity, binding)
                previewBinder.attach(binding.previewContainer)
            }
            is BroadcastState.Idle -> renderPreStream(activity, binding, state)
            is BroadcastState.Failed -> {
                renderPreStream(activity, binding, state)
                showFailure(binding, state)
            }
        }
    }

    private fun renderPreStream(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding,
        state: BroadcastState
    ) {
        previewBinder.detach()
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
        val isVisible = state is BroadcastState.Live && wearSendAvailable
        binding.btnSendToWatch.visibility = if (isVisible) View.VISIBLE else View.GONE
        binding.btnSendToWatch.isEnabled = isVisible && !wearSendInProgress
    }

    private fun renderModeControls(binding: ActivityBroadcastControlBinding, modeName: String) {
        val hasCamera = modeName != BroadcastMode.AUDIO_ONLY.name
        val cameraVisibility = if (hasCamera) View.VISIBLE else View.GONE
        binding.previewContainer.visibility = cameraVisibility
        binding.btnToggleCamera.visibility = cameraVisibility
        binding.btnSwitchCamera.visibility = cameraVisibility
        binding.btnToggleMic.visibility =
            if (modeName == BroadcastMode.VIDEO_AUDIO.name) View.VISIBLE else View.GONE
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
        Timber.d("S3429: broadcast toggles render, camera toggle draws ic_camera_capture")
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

    fun onDetach() {
        preStreamPreview.stop()
        previewBinder.detach()
        blankScreenManager.detach()
    }

    companion object {
        private const val QR_SIZE_FRACTION = 0.45
        private const val QR_SIZE_MIN_PX = 200
        private const val QR_SIZE_MAX_PX = 500
        private const val BROADCAST_DESCRIPTOR_MIME_TYPE = "application/vnd.fms.bcast+json"
        private const val BROADCAST_DESCRIPTOR_EXTENSION = ".fmsbcast"
    }
}
