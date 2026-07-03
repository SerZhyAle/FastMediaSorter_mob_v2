package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.data.capture.SaveResult
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.SaveCapturedMediaUseCase
import com.sza.fastmediasorter.ui.player.dispatch.StandalonePlayerDispatcherActivity
import com.sza.fastmediasorter.ui.share.SendToMenuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.Locale

/**
 * S0844: owns the multi-capture "last saved result" (send-to menu, gallery thumbnail, open-last-
 * capture) for [com.sza.fastmediasorter.ui.cameracapture.CameraCaptureActivity]. Extracted from the
 * Activity to shed its detekt `LargeClass`/`TooManyFunctions` findings - the capture/recording state
 * machine itself stays on the Activity (S0844 non-goal); this class only reacts to a finished save.
 */
class CameraCaptureResultManager(
    private val activity: FragmentActivity,
    private val lifecycleScope: CoroutineScope,
    private val sessionManager: CameraCaptureSessionManager,
    private val settingsRepository: SettingsRepository,
    private val saveCapturedMedia: SaveCapturedMediaUseCase,
    private val sendToMenuManager: SendToMenuManager,
    private val galleryThumbnail: ShapeableImageView,
    private val sendToButton: View,
    private val onError: (Int) -> Unit,
) {

    var lastSavedPath: String? = null
        private set
    var lastSavedMediaType: MediaType? = null
        private set

    /**
     * S0566/ADR-2: persist one capture of a stay-open session to its public folder (photo ->
     * DCIM/Camera, video -> Movies) through the shared use-case, then surface the result as the
     * gallery thumbnail. The saver deletes the scratch file; the camera is never finished here.
     */
    fun persistMultiCapture(file: File, isVideo: Boolean) {
        val name = file.name
        lifecycleScope.launch {
            val result = saveCapturedMedia(file, isVideo)
            if (activity.isFinishing || activity.isDestroyed) return@launch
            when (result) {
                is SaveResult.Success -> {
                    lastSavedPath = result.savedPath
                    lastSavedMediaType = if (isVideo) MediaType.VIDEO else MediaType.IMAGE
                    showGalleryThumbnail(result.savedPath)
                    updateSendToVisibility()
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.camera_capture_saved, name),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                else -> {
                    // The saver only deletes the scratch file on success, so drop the failed shot's
                    // scratch copy here to avoid leaving CAP_<stamp>_<seq> orphans in the session dir.
                    file.delete()
                    onError(R.string.camera_capture_error_save_generic)
                }
            }
        }
    }

    private fun showGalleryThumbnail(path: String) {
        galleryThumbnail.visibility = View.VISIBLE
        Glide.with(activity).load(File(path)).centerCrop().into(galleryThumbnail)
    }

    fun updateSendToVisibility() {
        sendToButton.visibility = if (!sessionManager.isRecording() && lastSavedPath != null) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    fun openSendToMenu() {
        val path = lastSavedPath ?: return
        lifecycleScope.launch {
            val file = File(path)
            if (!file.exists()) return@launch
            val uri = runCatching {
                FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
            }.getOrNull() ?: return@launch
            val settings = settingsRepository.getSettings().first()
            val mediaType = lastSavedMediaType ?: inferMediaType(file)
            val content = ShareableContent(
                uris = listOf(uri),
                mime = ShareableContent.mimeForMediaType(file.name, mediaType),
                mediaType = mediaType,
                displayName = file.name,
            )
            sendToMenuManager.show(activity, content, settings)
        }
    }

    private fun inferMediaType(file: File): MediaType {
        val ext = file.extension.lowercase(Locale.US)
        return when (ext) {
            "mp4", "mkv", "webm" -> MediaType.VIDEO
            else -> MediaType.IMAGE
        }
    }

    /**
     * S0566/§6.7: opens the most recent capture in the in-app player. Routes through
     * [StandalonePlayerDispatcherActivity] (resolves the media family from the URI and forwards to the
     * matching standalone host) instead of an implicit ACTION_VIEW the OS would hand to an external
     * gallery, keeping the user inside the app. A missing/unviewable file is logged, not fatal.
     */
    fun openLastCapture() {
        val file = lastSavedPath?.let(::File)?.takeIf { it.exists() } ?: return
        val uri = runCatching {
            FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        }.getOrNull() ?: return
        val intent = Intent(activity, StandalonePlayerDispatcherActivity::class.java)
            .setData(uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { activity.startActivity(intent) }
            .onFailure { Timber.w(it, "CameraCaptureResultManager: failed to open last capture in player") }
    }
}
