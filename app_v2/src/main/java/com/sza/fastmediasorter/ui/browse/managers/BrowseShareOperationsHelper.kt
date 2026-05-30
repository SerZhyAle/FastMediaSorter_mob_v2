package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import com.sza.fastmediasorter.core.share.TelegramShareTargets
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.ui.player.helpers.FileCopyProgressDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

internal class BrowseShareOperationsHelper(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val fileOperationUseCase: FileOperationUseCase,
    private val callbacks: BrowseFileOperationsManager.FileOperationCallbacks,
    private val showFailureError: (Int, FileOperationResult.Failure) -> Unit,
    private val showUnexpectedError: (Int) -> Unit
) {
    fun shareSelectedFiles(
        selectedFiles: List<MediaFile>,
        resource: MediaResource
    ) {
        if (selectedFiles.isEmpty()) {
            Toast.makeText(context, R.string.no_files_selected, Toast.LENGTH_SHORT).show()
            return
        }

        coroutineScope.launch {
            try {
                Toast.makeText(context, R.string.please_wait, Toast.LENGTH_SHORT).show()

                val uris = mutableListOf<Uri>()

                for (mediaFile in selectedFiles) {
                    val fileToShare = when (resource.type) {
                        ResourceType.LOCAL -> File(mediaFile.path)
                        ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP, ResourceType.CLOUD -> {
                            downloadNetworkFileToCacheWithProgress(mediaFile, resource)
                        }
                    }

                    if (fileToShare != null && fileToShare.exists()) {
                        uris.add(
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                fileToShare
                            )
                        )
                    }
                }

                if (uris.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val shareIntent = android.content.Intent().apply {
                    action = if (uris.size == 1) {
                        android.content.Intent.ACTION_SEND
                    } else {
                        android.content.Intent.ACTION_SEND_MULTIPLE
                    }

                    if (uris.size == 1) {
                        putExtra(android.content.Intent.EXTRA_STREAM, uris[0])
                    } else {
                        putParcelableArrayListExtra(
                            android.content.Intent.EXTRA_STREAM,
                            ArrayList(uris)
                        )
                    }

                    type = "*/*"
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    context.startActivity(
                        android.content.Intent.createChooser(shareIntent, context.getString(R.string.share))
                    )
                }
            } catch (_: CancellationException) {
                Timber.i("Share operation cancelled by user")
                return@launch
            } catch (e: Exception) {
                Timber.e(e, "Failed to share files")
                withContext(Dispatchers.Main) {
                    showUnexpectedError(R.string.error_share_failed)
                }
            }
        }
    }

    // S0303: send selected file(s) to an installed Telegram client. Mirrors [shareSelectedFiles]
    // for URI staging, but targets the resolved Telegram package via [SystemShareInvoker.invokeFiles]
    // (which falls back to the system chooser if the client is unavailable).
    fun sendSelectedFilesToTelegram(
        selectedFiles: List<MediaFile>,
        resource: MediaResource
    ) {
        Timber.d("S0303: browse send-to-Telegram action invoked for ${selectedFiles.size} file(s)")
        if (selectedFiles.isEmpty()) {
            Toast.makeText(context, R.string.no_files_selected, Toast.LENGTH_SHORT).show()
            return
        }

        coroutineScope.launch {
            try {
                Toast.makeText(context, R.string.please_wait, Toast.LENGTH_SHORT).show()

                val uris = mutableListOf<Uri>()
                for (mediaFile in selectedFiles) {
                    val fileToShare = when (resource.type) {
                        ResourceType.LOCAL -> File(mediaFile.path)
                        ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP, ResourceType.CLOUD -> {
                            downloadNetworkFileToCacheWithProgress(mediaFile, resource)
                        }
                    }
                    if (fileToShare != null && fileToShare.exists()) {
                        uris.add(
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                fileToShare
                            )
                        )
                    }
                }

                if (uris.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    val telegramPackage = TelegramShareTargets.firstInstalledPackage(context.packageManager)
                    val launched = SystemShareInvoker.invokeFiles(
                        context = context,
                        uris = uris,
                        mime = "*/*",
                        preferredPackage = telegramPackage,
                        chooserTitle = context.getString(R.string.share_to_telegram),
                    )
                    if (!launched) {
                        Toast.makeText(context, R.string.share_to_telegram_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: CancellationException) {
                Timber.i("Send to Telegram cancelled by user")
                return@launch
            } catch (e: Exception) {
                Timber.e(e, "Failed to send files to Telegram")
                withContext(Dispatchers.Main) {
                    showUnexpectedError(R.string.share_to_telegram_failed)
                }
            }
        }
    }

    private suspend fun downloadNetworkFileToCacheWithProgress(
        mediaFile: MediaFile,
        resource: MediaResource
    ): File? {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, R.string.msg_download_share, Toast.LENGTH_SHORT).show()
        }

        val cacheRoot = callbacks.getExternalCacheDir() ?: callbacks.getCacheDir() ?: return null
        val shareTempDir = File(cacheRoot, "share_temp")
        withContext(Dispatchers.IO) {
            if (!shareTempDir.exists()) {
                shareTempDir.mkdirs()
            }
            cleanupOldShareTempFiles(shareTempDir)
        }

        val tempFile = File(shareTempDir, mediaFile.name)
        withContext(Dispatchers.IO) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }

        val operation = FileOperation.Copy(
            sources = listOf(createNetworkAwareFile(mediaFile.path, mediaFile.name, mediaFile.size)),
            destination = shareTempDir,
            overwrite = true,
            sourceCredentialsId = resource.credentialsId
        )

        val totalBytes = mediaFile.size.coerceAtLeast(0L)
        val copyDeferred = coroutineScope.async(Dispatchers.IO) {
            fileOperationUseCase.execute(operation)
        }

        val progressDialog = if (context is Activity && !context.isFinishing && !context.isDestroyed) {
            FileCopyProgressDialog(
                context = context,
                fileName = mediaFile.name,
                onCancelRequested = {
                    copyDeferred.cancel(CancellationException("User cancelled network share copy"))
                }
            )
        } else {
            null
        }

        val monitorJob = coroutineScope.launch(Dispatchers.Main) {
            var lastTime = System.currentTimeMillis()
            var lastBytes = 0L

            progressDialog?.show()
            progressDialog?.showIndeterminate()

            while (copyDeferred.isActive) {
                val copiedBytes = tempFile.length().coerceAtLeast(0L)
                val now = System.currentTimeMillis()
                val elapsedMs = (now - lastTime).coerceAtLeast(1L)
                val bytesDelta = (copiedBytes - lastBytes).coerceAtLeast(0L)
                val speedBytesPerSec = (bytesDelta * 1000L) / elapsedMs

                progressDialog?.updateProgress(copiedBytes, totalBytes, speedBytesPerSec)

                lastTime = now
                lastBytes = copiedBytes
                delay(200)
            }
        }

        return try {
            when (val result = copyDeferred.await()) {
                is FileOperationResult.Success -> {
                    if (tempFile.exists()) {
                        tempFile
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
                        }
                        null
                    }
                }
                is FileOperationResult.Failure -> {
                    withContext(Dispatchers.Main) {
                        showFailureError(R.string.error_share_download_failed, result)
                    }
                    null
                }
                is FileOperationResult.AuthenticationRequired -> {
                    callbacks.onAuthRequest(result.provider)
                    null
                }
                else -> {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.error_share_unexpected, Toast.LENGTH_SHORT).show()
                    }
                    null
                }
            }
        } catch (_: CancellationException) {
            withContext(Dispatchers.IO) {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.toast_copy_cancelled, Toast.LENGTH_SHORT).show()
            }
            throw CancellationException("Share copy cancelled after cleanup")
        } finally {
            monitorJob.cancel()
            withContext(Dispatchers.Main) {
                progressDialog?.dismiss()
            }
        }
    }

    private fun cleanupOldShareTempFiles(cacheDir: File) {
        val now = System.currentTimeMillis()
        cacheDir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            if (now - file.lastModified() > 60 * 60 * 1000L) {
                file.delete()
            }
        }
    }

    // FileOperation uses File handles end-to-end, so remote sources keep their original protocol path here.
    private fun createNetworkAwareFile(path: String, name: String?, size: Long): File {
        return if (path.startsWith("smb://") ||
            path.startsWith("sftp://") ||
            path.startsWith("ftp://") ||
            path.startsWith("cloud://")
        ) {
            object : File(path) {
                override fun getAbsolutePath(): String = path
                override fun getPath(): String = path
                override fun getName(): String = name ?: super.getName()
                override fun length(): Long = size
            }
        } else {
            File(path)
        }
    }
}