package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.util.findLifecycleOwner
import com.sza.fastmediasorter.util.showBoundToHost
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.nio.CharBuffer
import kotlin.coroutines.resume

internal class TextDocumentSaveManager(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) {
    enum class Destination { ORIGINAL, COPY }

    private var pendingCopy: CancellableContinuation<Uri?>? = null
    private val owner = context.findLifecycleOwner()
    private val copyPicker = (owner as? ActivityResultRegistryOwner)
        ?.activityResultRegistry
        ?.register(COPY_PICKER_KEY, ActivityResultContracts.StartActivityForResult()) { result ->
            pendingCopy?.takeIf { it.isActive }?.resume(
                result.data?.data?.takeIf { result.resultCode == Activity.RESULT_OK }
            )
        }

    init {
        owner?.lifecycle?.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                copyPicker?.unregister()
                owner.lifecycle.removeObserver(this)
            }
        })
    }

    suspend fun save(
        source: MediaFile,
        content: String,
        pager: TextFilePager?,
        canOverwrite: Boolean,
        allowCopy: Boolean,
        isCurrent: () -> Boolean,
        showError: (String) -> Unit,
    ): Destination? {
        Timber.d("S4839: SAF text commit requested, overwrite allowed=%s", canOverwrite)
        val failure = if (canOverwrite) {
            writeOriginal(Uri.parse(source.path), content, pager)
        } else {
            R.string.error_reason_read_only
        }
        return when {
            failure == null -> Destination.ORIGINAL
            !isCurrent() -> null
            allowCopy && copyPicker != null && offerCopy(failure) && isCurrent() ->
                saveCopy(source.name, content, isCurrent, showError)
            else -> {
                if (!allowCopy || copyPicker == null) showError(context.getString(failure))
                null
            }
        }
    }

    private suspend fun saveCopy(
        name: String,
        content: String,
        isCurrent: () -> Boolean,
        showError: (String) -> Unit,
    ): Destination? {
        val uri = try {
            pickCopy(name)
        } catch (error: ActivityNotFoundException) {
            Timber.w(error, "No document picker available for text copy")
            if (isCurrent()) showError(context.getString(R.string.text_file_save_failed))
            null
        }
        if (uri == null || !isCurrent()) return null
        val failure = withContext(ioDispatcher) { write(uri, content.toByteArray(Charsets.UTF_8)) }
        return if (failure == null) {
            Destination.COPY
        } else {
            if (isCurrent()) showError(context.getString(failure))
            null
        }
    }

    private suspend fun writeOriginal(uri: Uri, content: String, pager: TextFilePager?): Int? =
        withContext(ioDispatcher) {
            val bytes = try {
                val encoded = (pager?.charset ?: Charsets.UTF_8).newEncoder().encode(CharBuffer.wrap(content))
                ByteArray(encoded.remaining()).also { encoded.get(it) }
            } catch (error: IOException) {
                Timber.w(error, "Could not encode text for the original document")
                return@withContext R.string.text_file_save_failed
            }
            val failure = write(uri, bytes)
            if (failure == null) {
                try {
                    pager?.replaceCachedContents(bytes)
                } catch (error: IOException) {
                    Timber.w(error, "Original document was saved but its reader cache could not be refreshed")
                }
            }
            failure
        }

    private fun write(uri: Uri, bytes: ByteArray): Int? = try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: throw IOException("Document provider did not open a write stream")
        null
    } catch (error: SecurityException) {
        Timber.w(error, "Document write permission denied")
        R.string.error_reason_read_only
    } catch (error: IOException) {
        Timber.w(error, "Document write failed")
        R.string.text_file_save_failed
    }

    private suspend fun offerCopy(message: Int): Boolean = suspendCancellableCoroutine { choice ->
        val dialog = MaterialAlertDialogBuilder(context)
            .setMessage(message)
            .setPositiveButton(R.string.btn_save_as_copy) { _, _ -> choice.resume(true) }
            .setNegativeButton(R.string.cancel) { _, _ -> choice.resume(false) }
            .setOnCancelListener { choice.resume(false) }
            .showBoundToHost(context)
        if (dialog == null) choice.resume(false)
    }

    private suspend fun pickCopy(name: String): Uri? = try {
        suspendCancellableCoroutine { result ->
            pendingCopy = result
            val extension = name.substringAfterLast('.', "").lowercase()
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream")
                .putExtra(Intent.EXTRA_TITLE, name)
            checkNotNull(copyPicker).launch(intent)
        }
    } finally {
        pendingCopy = null
    }

    private companion object {
        const val COPY_PICKER_KEY = "player_text_document_save_copy"
    }
}
