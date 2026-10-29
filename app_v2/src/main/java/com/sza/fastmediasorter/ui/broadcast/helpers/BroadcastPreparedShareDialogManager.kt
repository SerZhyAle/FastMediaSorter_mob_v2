package com.sza.fastmediasorter.ui.broadcast.helpers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.WriterException
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.share.SharePayload
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import com.sza.fastmediasorter.databinding.DialogBroadcastPreparedShareBinding
import com.sza.fastmediasorter.domain.model.PreparedBroadcastLink
import com.sza.fastmediasorter.domain.usecase.broadcast.EncodeBroadcastDescriptorUseCase
import com.sza.fastmediasorter.domain.usecase.broadcast.ResolvePreparedBroadcastShareUseCase
import com.sza.fastmediasorter.ui.companionimport.qr.QrCodeEncoder
import com.sza.fastmediasorter.util.CaptureFileNamer
import com.sza.fastmediasorter.util.showBoundTo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.min

/**
 * S4247: hands out a link prepared before the start through the live share panel's three channels - the QR code,
 * the link and the file - with the password told apart from them (items BF, DK). The payload opens the broadcast by
 * itself once it starts, so it is never logged. Holds no activity past [onDetach].
 */
class BroadcastPreparedShareDialogManager @Inject constructor(
    private val resolveShare: ResolvePreparedBroadcastShareUseCase,
    private val encode: EncodeBroadcastDescriptorUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    // Registered on the activity's own ActivityResultRegistry; its callback captures that activity.
    private var exportFileLauncher: ActivityResultLauncher<String>? = null

    // The link whose file the open document picker will receive.
    private var exporting: PreparedBroadcastLink? = null

    fun setup(activity: AppCompatActivity) {
        exportFileLauncher = activity.registerForActivityResult(
            ActivityResultContracts.CreateDocument(BROADCAST_DESCRIPTOR_MIME_TYPE)
        ) { uri ->
            val link = exporting
            exporting = null
            if (uri != null && link != null) writeDescriptor(activity, uri, link)
        }
    }

    fun show(activity: AppCompatActivity, link: PreparedBroadcastLink) {
        Timber.d("S4247: prepared link share dialog opened $link")
        val descriptor = resolveShare(link)
        val payload = encode.compress(descriptor)
        val shareLink = BroadcastShareLinkFactory.create(payload)
        val view = DialogBroadcastPreparedShareBinding.inflate(LayoutInflater.from(activity))
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.broadcast_prepared_share_title)
            .setView(view.root)
            .setNegativeButton(R.string.close, null)
            .showBoundTo(activity) ?: return
        renderQr(activity, view, payload)
        val password = resolveShare.linkPassword(link)
        view.tvPreparedSharePassword.isVisible = password != null
        password?.let {
            view.tvPreparedSharePassword.text = activity.getString(R.string.broadcast_link_password_row, it)
        }
        view.btnPreparedShareCopy.setOnClickListener {
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Broadcast URL", shareLink))
            Toast.makeText(activity, R.string.broadcast_share_saved, Toast.LENGTH_SHORT).show()
        }
        view.btnPreparedShareSend.setOnClickListener {
            SystemShareInvoker.invoke(
                activity,
                SharePayload.Text(shareLink),
                chooserTitle = activity.getString(R.string.broadcast_share_send_chooser_title),
            )
        }
        view.btnPreparedShareFile.setOnClickListener {
            exporting = link
            val name = CaptureFileNamer.shared.allocate(
                CaptureFileNamer.CaptureKind.BROADCAST,
                BROADCAST_DESCRIPTOR_EXTENSION,
            )
            exportFileLauncher?.launch(name)
        }
    }

    private fun renderQr(activity: AppCompatActivity, view: DialogBroadcastPreparedShareBinding, payload: String) {
        val metrics = activity.resources.displayMetrics
        val size = (min(metrics.widthPixels, metrics.heightPixels) * QR_SIZE_FRACTION)
            .toInt()
            .coerceIn(QR_SIZE_MIN_PX, QR_SIZE_MAX_PX)
        try {
            view.ivPreparedShareQr.setImageBitmap(QrCodeEncoder.encode(payload, size))
        } catch (e: WriterException) {
            Timber.w(e, "Failed to encode the prepared broadcast QR code")
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Illegal argument when encoding the prepared broadcast QR code")
        }
    }

    private fun writeDescriptor(activity: AppCompatActivity, uri: Uri, link: PreparedBroadcastLink) {
        val json = encode.serializeJson(resolveShare(link))
        activity.lifecycleScope.launch {
            val saved = withContext(ioDispatcher) {
                runCatching {
                    activity.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { writer ->
                        writer.write(json)
                    } ?: return@runCatching false
                    true
                }.onFailure { error ->
                    Timber.e(error, "Writing the prepared broadcast descriptor failed")
                }.getOrDefault(false)
            }
            Toast.makeText(
                activity,
                if (saved) R.string.broadcast_share_saved else R.string.broadcast_share_save_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun onDetach() {
        exportFileLauncher = null
        exporting = null
    }

    private companion object {
        const val QR_SIZE_FRACTION = 0.6
        const val QR_SIZE_MIN_PX = 200
        const val QR_SIZE_MAX_PX = 600
        const val BROADCAST_DESCRIPTOR_MIME_TYPE = "application/vnd.fms.bcast+json"
        const val BROADCAST_DESCRIPTOR_EXTENSION = ".fmsbcast"
    }
}
