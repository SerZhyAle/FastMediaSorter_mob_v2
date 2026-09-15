package com.sza.fastmediasorter.ui.broadcast.helpers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.WriterException
import com.sza.fastmediasorter.R
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
import com.sza.fastmediasorter.ui.companionimport.qr.QrCodeEncoder
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.util.showBoundTo
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
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
) {
    private var selectedMode: BroadcastMode = BroadcastMode.AUDIO_ONLY
    private var selectedLensId: String? = null
    private var wearSendAvailable = false
    private var wearSendInProgress = false
    private lateinit var exportFileLauncher: ActivityResultLauncher<String>

    fun setup(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        blankScreenManager.attach(activity, binding.root)
        setupPreStreamControls(activity, binding)
        setupLiveControls(activity, binding)
        setupSharePanel(activity, binding)

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    controller.state.collect { state ->
                        renderState(binding, state)
                    }
                }
                launch {
                    controller.listenerCount.collect { count ->
                        renderListenerCount(activity, binding, count)
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

        binding.btnStartBroadcast.setOnClickListener {
            controller.start(selectedMode, selectedLensId)
        }

        binding.btnBroadcastSettings.setOnClickListener {
            val intent = Intent(activity, SettingsActivity::class.java)
            activity.startActivity(intent)
        }

        updateLensSelectionVisibility(activity, binding)
    }

    private fun updateLensSelectionVisibility(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        val hasCamera = selectedMode != BroadcastMode.AUDIO_ONLY
        if (hasCamera) {
            activity.lifecycleScope.launch {
                val choice = listLenses.listOptions()
                if (selectedLensId == null) {
                    selectedLensId = choice.initialLensId
                }
                val lensChips = BroadcastLensChipsRenderer(
                    label = binding.tvLensHeader,
                    group = binding.cgLensSelection,
                    onLensSelected = { lensId -> selectedLensId = lensId }
                )
                lensChips.render(
                    BroadcastEntryManager.LensUi(
                        options = choice.options,
                        selectedLensId = selectedLensId,
                        visible = choice.options.isNotEmpty()
                    )
                )
            }
        } else {
            binding.tvLensHeader.visibility = View.GONE
            binding.cgLensSelection.visibility = View.GONE
            selectedLensId = null
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

        binding.btnShareLink.setOnClickListener {
            val isVisible = binding.layoutSharePanel.visibility == View.VISIBLE
            binding.layoutSharePanel.visibility = if (isVisible) View.GONE else View.VISIBLE
            if (!isVisible) {
                renderShareData(activity, binding)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun renderShareData(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        val liveState = controller.state.value as? BroadcastState.Live ?: return
        val url = liveState.descriptor.url
        val title = liveState.descriptor.title
        val mode = liveState.descriptor.mode

        binding.tvShareUrl.text = url

        binding.btnCopyUrl.setOnClickListener {
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Broadcast URL", url)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(activity, R.string.broadcast_share_saved, Toast.LENGTH_SHORT).show()
        }

        binding.btnShareSend.setOnClickListener {
            val link = shareManager.generateShareLink(url, title, mode)
            SystemShareInvoker.invoke(
                activity,
                SharePayload.Text(link),
                chooserTitle = activity.getString(R.string.broadcast_share_send_chooser_title)
            )
        }

        binding.btnExportFile.setOnClickListener {
            exportFileLauncher.launch("broadcast_${url.hashCode()}$BROADCAST_DESCRIPTOR_EXTENSION")
        }

        val payload = shareManager.generateQrPayload(url, title, mode)
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
        val json = shareManager.generateJsonPayload(
            descriptor.url,
            descriptor.title,
            descriptor.mode,
        )
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
                    controller.selectLens(choice.options[which].id)
                    dialog.dismiss()
                }
                .showBoundTo(activity)
        }
    }

    private fun renderState(
        binding: ActivityBroadcastControlBinding,
        state: BroadcastState
    ) {
        when (state) {
            is BroadcastState.Live -> {
                binding.layoutPreStream.visibility = View.GONE
                binding.layoutLiveControls.visibility = View.VISIBLE
                renderModeControls(binding, state.descriptor.mode)
                renderToggles(binding, state)
                renderSendToWatch(binding, state)
                previewBinder.attach(binding.previewContainer)
            }
            is BroadcastState.Idle, is BroadcastState.Failed -> {
                previewBinder.detach()
                binding.layoutPreStream.visibility = View.VISIBLE
                binding.layoutLiveControls.visibility = View.GONE
                binding.previewContainer.visibility = View.GONE
                binding.layoutSharePanel.visibility = View.GONE
            }
        }
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
        binding.btnToggleCamera.setText(
            if (state.cameraEnabled) R.string.broadcast_control_camera_on else R.string.broadcast_control_camera_off
        )
        binding.btnToggleCamera.setIconResource(R.drawable.ic_display)
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

    fun onDetach() {
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
