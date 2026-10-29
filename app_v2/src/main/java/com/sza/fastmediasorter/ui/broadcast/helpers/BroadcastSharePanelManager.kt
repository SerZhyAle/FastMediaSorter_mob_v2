package com.sza.fastmediasorter.ui.broadcast.helpers

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.WriterException
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.broadcast.BroadcastSourceController
import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.share.SharePayload
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import com.sza.fastmediasorter.databinding.ActivityBroadcastControlBinding
import com.sza.fastmediasorter.domain.usecase.broadcast.ManageBroadcastGuestsUseCase
import com.sza.fastmediasorter.ui.common.support.MediaExchangePortalLinks
import com.sza.fastmediasorter.ui.common.support.SupportIntentFactory
import com.sza.fastmediasorter.ui.companionimport.qr.QrCodeEncoder
import com.sza.fastmediasorter.util.CaptureFileNamer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.min

/**
 * The live broadcast's share panel of [BroadcastControlManager]: link, QR code, descriptor file and, for a protected
 * broadcast, the link password with its renewal (S4258). Owned by that singleton, so it holds no activity past
 * [onDetach]; it owns the full-screen QR code as well, which the idle layout dismisses through [dismissQr].
 */
class BroadcastSharePanelManager @Inject constructor(
    private val controller: BroadcastSourceController,
    private val shareManager: BroadcastShareManager,
    private val guests: ManageBroadcastGuestsUseCase,
    private val linkPasswordDialog: BroadcastLinkPasswordDialogManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    /** Takes no dependencies of its own, so it is constructed here instead of going through Hilt. */
    private val qrFullscreen = BroadcastQrFullscreenManager()

    private val guestsDialog = BroadcastGuestsDialogManager(guests)

    // Registered on the activity's own ActivityResultRegistry; its callback captures that activity.
    private var exportFileLauncher: ActivityResultLauncher<String>? = null

    fun setup(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        exportFileLauncher = activity.registerForActivityResult(
            ActivityResultContracts.CreateDocument(BROADCAST_DESCRIPTOR_MIME_TYPE)
        ) { uri ->
            if (uri != null) writeBroadcastDescriptor(activity, uri)
        }

        binding.btnBroadcastHelp.setOnClickListener { openViewerGuide(activity) }
        binding.btnBroadcastPortal.setOnClickListener {
            MediaExchangePortalLinks.open(activity, MediaExchangePortalLinks.Page.LIVE_BROADCAST)
        }
        // Outside the share panel: the way to start is shown before there is anything to share.
        binding.btnBroadcastPortalStart.setOnClickListener {
            MediaExchangePortalLinks.open(activity, MediaExchangePortalLinks.Page.BROADCAST_START)
        }
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
    fun render(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding
    ) {
        val liveState = controller.state.value as? BroadcastState.Live ?: return
        val descriptor = liveState.descriptor
        val url = descriptor.url

        binding.tvShareUrl.text = url
        renderSharePassword(activity, binding, liveState)
        // Strategic S4272 3.4: only a broadcast announced through an exchange server by a member account. S4405:
        // "announced", not "has a password" - an open link is announced too.
        binding.btnShareGuests.isVisible = isAnnounced(liveState) && guests.available
        binding.btnShareGuests.setOnClickListener { guestsDialog.show(activity) }

        binding.btnCopyUrl.setOnClickListener {
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            // A bare URL would strip `auth` and the relay's sealing key, and the receiver could not open it (item M).
            val copied = if (isAnnounced(liveState)) shareManager.generateShareLink(liveState) else url
            val clip = ClipData.newPlainText("Broadcast URL", copied)
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
            exportFileLauncher?.launch(descriptorName)
        }

        val payload = shareManager.generateQrPayload(liveState)
        binding.ivShareQr.setOnClickListener { qrFullscreen.show(activity, payload) }
        qrFullscreen.update(activity, payload)
        val metrics = activity.resources.displayMetrics
        val size = (min(metrics.widthPixels, metrics.heightPixels) * QR_SIZE_FRACTION)
            .toInt()
            .coerceIn(QR_SIZE_MIN_PX, QR_SIZE_MAX_PX)
        try {
            binding.ivShareQr.setImageBitmap(QrCodeEncoder.encode(payload, size))
        } catch (e: WriterException) {
            Timber.w(e, "Failed to encode QR in BroadcastSharePanelManager")
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Illegal argument when encoding QR in BroadcastSharePanelManager")
        }
    }

    private fun isAnnounced(live: BroadcastState.Live): Boolean = shareManager.isAnnounced(live)

    /**
     * The link password is told apart from the link: neither the link, the file nor the QR code carries it. Shown
     * while the broadcast is announced, "none" included (S4405).
     */
    private fun renderSharePassword(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding,
        live: BroadcastState.Live,
    ) {
        val announced = isAnnounced(live)
        binding.tvSharePassword.isVisible = announced
        binding.btnShareChangePassword.isVisible = announced
        if (!announced) return
        val password = shareManager.linkPassword(live)
        binding.tvSharePassword.text = if (password != null) {
            activity.getString(R.string.broadcast_link_password_row, password)
        } else {
            activity.getString(R.string.broadcast_link_password_none_row)
        }
        binding.btnShareChangePassword.setOnClickListener {
            linkPasswordDialog.show(activity, isLive = true) { messageRes ->
                render(activity, binding)
                Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun writeBroadcastDescriptor(activity: AppCompatActivity, uri: Uri) {
        val liveState = controller.state.value as? BroadcastState.Live ?: return
        val json = shareManager.generateJsonPayload(liveState)
        activity.lifecycleScope.launch {
            val saved = withContext(ioDispatcher) {
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

    val shareChanges: Flow<*> get() = shareManager.shareChanges

    fun dismissQr() = qrFullscreen.dismiss()

    fun onDetach() {
        qrFullscreen.dismiss()
        exportFileLauncher = null
    }

    private companion object {
        const val QR_SIZE_FRACTION = 0.45
        const val QR_SIZE_MIN_PX = 200
        const val QR_SIZE_MAX_PX = 500
        const val BROADCAST_DESCRIPTOR_MIME_TYPE = "application/vnd.fms.bcast+json"
        const val BROADCAST_DESCRIPTOR_EXTENSION = ".fmsbcast"
    }
}
