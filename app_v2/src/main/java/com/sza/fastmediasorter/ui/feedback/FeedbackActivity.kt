package com.sza.fastmediasorter.ui.feedback

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.theme.ColorThemePrefs
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.databinding.ActivityFeedbackBinding
import com.sza.fastmediasorter.ui.feedback.helpers.FeedbackFolderPanelManager
import com.sza.fastmediasorter.ui.feedback.helpers.FeedbackImageInputManager
import com.sza.fastmediasorter.ui.feedback.helpers.FeedbackSendToManager
import com.sza.fastmediasorter.ui.settings.ticket.TicketRow
import com.sza.fastmediasorter.ui.settings.ticket.TicketRowAdapter
import com.sza.fastmediasorter.ui.settings.ticket.helpers.TicketAttachmentPickerManager
import com.sza.fastmediasorter.ui.settings.ticket.helpers.TicketVoiceRecordManager
import com.sza.fastmediasorter.ui.share.SendToMenuManager
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * S4573: host of the Send feedback sub-program - text, pictures and a voice note in one form. The
 * state lives in [FeedbackComposerViewModel] and the picture sources in [FeedbackImageInputManager];
 * this class only wires views and activity results.
 */
@AndroidEntryPoint
class FeedbackActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    @Inject
    lateinit var sendToMenuManager: SendToMenuManager

    private val viewModel: FeedbackComposerViewModel by viewModels()
    private lateinit var binding: ActivityFeedbackBinding
    private lateinit var sendTo: FeedbackSendToManager
    private lateinit var images: FeedbackImageInputManager
    private lateinit var voice: TicketVoiceRecordManager
    private var pendingPhoto: File? = null

    private val pickImages = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) lifecycleScope.launch { viewModel.addAttachments(images.fromPicker(uris)) }
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pendingPhoto
        pendingPhoto = null
        val source = file?.takeIf { saved }?.let(images::fromPhoto)
        if (source != null) viewModel.addAttachments(listOf(source))
    }

    private val askMicrophone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && voice.start()) renderRecording(true) else viewModel.notify(FeedbackNotice.VoiceFailed)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ColorThemePrefs.applyThemeOverlay(this)
        binding = ActivityFeedbackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsetPadding()
        images = FeedbackImageInputManager(this)
        voice = TicketVoiceRecordManager(this)

        val inputs = binding.feedbackInputs
        val attachments = TicketRowAdapter { row -> viewModel.removeAttachment(row.id) }
        inputs.feedbackAttachmentList.layoutManager = LinearLayoutManager(this)
        inputs.feedbackAttachmentList.adapter = attachments
        binding.feedbackToolbar.setNavigationOnClickListener { finish() }
        inputs.feedbackPasteButton.setOnClickListener { pasteImage() }
        inputs.feedbackPickButton.setOnClickListener {
            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        inputs.feedbackPhotoButton.setOnClickListener { startPhoto() }
        inputs.feedbackVoiceButton.isVisible = voice.isAvailable()
        inputs.feedbackVoiceButton.setOnClickListener { toggleVoice() }
        sendTo = FeedbackSendToManager(this, sendToMenuManager)
        val folders = FeedbackFolderPanelManager(inputs, viewModel::chooseFolder)
        inputs.feedbackSendButton.setOnClickListener {
            viewModel.send(binding.feedbackTextInput.text?.toString().orEmpty())
        }
        collectOnLifecycle(viewModel.folders, block = folders::render)

        collectOnLifecycle(viewModel.state) { state ->
            attachments.submitList(
                state.attachments.map { source ->
                    TicketRow(
                        id = source.uri,
                        title = source.displayName,
                        subtitle = null,
                        removeLabel = getString(R.string.feedback_remove_attachment, source.displayName),
                    )
                },
            )
            inputs.feedbackSendButton.isEnabled = !state.busy
            state.notice?.let { notice ->
                showNotice(notice)
                viewModel.consumeNotice()
            }
            state.handoff?.let { handoff ->
                viewModel.consumeHandoff()
                if (handoff.clearForm) binding.feedbackTextInput.text = null
                sendTo.show(handoff)
            }
        }
    }

    override fun onDestroy() {
        if (::voice.isInitialized) voice.release()
        super.onDestroy()
    }

    private fun pasteImage() {
        lifecycleScope.launch {
            val source = images.fromClipboard()
            if (source == null) {
                viewModel.notify(FeedbackNotice.NoImageInClipboard)
            } else {
                viewModel.addAttachments(listOf(source))
            }
        }
    }

    private fun startPhoto() {
        lifecycleScope.launch {
            val (file, uri) = images.newPhotoTarget()
            pendingPhoto = file
            takePhoto.launch(uri)
        }
    }

    private fun toggleVoice() {
        if (!voice.isRecording) {
            askMicrophone.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val file = voice.stop()
        renderRecording(false)
        if (file == null) {
            viewModel.notify(FeedbackNotice.VoiceFailed)
        } else {
            viewModel.addAttachments(listOf(TicketAttachmentPickerManager(this).voiceNote(file)))
        }
    }

    private fun renderRecording(recording: Boolean) {
        val inputs = binding.feedbackInputs
        inputs.feedbackRecordingText.isVisible = recording
        val label = getString(if (recording) R.string.feedback_stop_voice else R.string.feedback_record_voice)
        inputs.feedbackVoiceButton.contentDescription = label
        TooltipCompat.setTooltipText(inputs.feedbackVoiceButton, label)
    }

    private fun showNotice(notice: FeedbackNotice) {
        val message = when (notice) {
            FeedbackNotice.NoImageInClipboard -> getString(R.string.feedback_no_image_in_clipboard)
            FeedbackNotice.VoiceFailed -> getString(R.string.feedback_voice_failed)
            FeedbackNotice.Empty -> getString(R.string.feedback_empty)
            FeedbackNotice.Queued -> getString(R.string.feedback_queued)
            is FeedbackNotice.StageFailed -> getString(R.string.feedback_stage_failed, notice.reason)
        }
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, FeedbackActivity::class.java)
    }
}
