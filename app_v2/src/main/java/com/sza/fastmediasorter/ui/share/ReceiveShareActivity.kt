package com.sza.fastmediasorter.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.ui.dialog.FileOperationDestinationDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Transparent Activity that intercepts ACTION_SEND / ACTION_SEND_MULTIPLE intents
 * from the system Share sheet and presents FileOperationDestinationDialog so the user
 * can pick a registered destination (or any folder) to save the content.
 *
 * Enabled/disabled at runtime via DefaultPlayerManager.applyShareReceiverState().
 */
@AndroidEntryPoint
class ReceiveShareActivity : AppCompatActivity() {

    @Inject lateinit var fileOperationUseCase: FileOperationUseCase
    @Inject lateinit var getDestinationsUseCase: GetDestinationsUseCase

    private val tempDir: File by lazy {
        cacheDir.resolve("temp_share").also { it.mkdirs() }
    }

    private var cachedFiles: List<File> = emptyList()
    private var isFinishTriggered = false
    private var folderPickerActive = false

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        folderPickerActive = false
        if (uri != null) {
            copyToSafFolder(uri)
        } else {
            // Picker cancelled — re-show destination dialog so user can choose a registered destination
            if (cachedFiles.isNotEmpty()) showDestinationDialog()
            else cleanupAndFinish()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent?.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) {
            Timber.w("ReceiveShareActivity: unexpected action=$action, finishing")
            finish()
            return
        }

        processIntent(intent)
    }

    // ── Intent processing ────────────────────────────────────────────────────

    private fun processIntent(intent: Intent) {
        val loadingDialog = showLoadingDialog()
        lifecycleScope.launch {
            try {
                val files = withContext(Dispatchers.IO) { extractAndCacheFiles(intent) }
                loadingDialog.dismiss()
                if (files.isEmpty()) {
                    Toast.makeText(this@ReceiveShareActivity, R.string.receive_share_no_content, Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }
                cachedFiles = files
                showDestinationDialog()
            } catch (e: Exception) {
                loadingDialog.dismiss()
                Timber.e(e, "ReceiveShareActivity: failed to process share intent")
                Toast.makeText(
                    this@ReceiveShareActivity,
                    getString(R.string.receive_share_cache_failed, e.message),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun extractAndCacheFiles(intent: Intent): List<File> {
        val streams = extractStreams(intent)
        return if (streams.isNotEmpty()) {
            cacheStreams(streams)
        } else {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) listOf(createTextFile(intent, text)) else emptyList()
        }
    }

    @Suppress("DEPRECATION") // getParcelableExtra deprecated in API 33; minSdk 26 needs the old path
    private fun extractStreams(intent: Intent): List<Uri> {
        return when {
            intent.action == Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
            }
            intent.hasExtra(Intent.EXTRA_STREAM) -> {
                listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM))
            }
            else -> emptyList()
        }
    }

    private fun cacheStreams(uris: List<Uri>): List<File> = uris.mapNotNull { uri ->
        runCatching {
            val name = resolveFileName(uri)
            val dest = tempDir.resolve(name)
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            if (dest.exists() && dest.length() > 0) dest else null
        }.onFailure { Timber.e(it, "ReceiveShareActivity: failed to cache $uri") }.getOrNull()
    }

    private fun resolveFileName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (col >= 0) return cursor.getString(col)
            }
        }
        return "shared_${System.currentTimeMillis()}"
    }

    private fun createTextFile(intent: Intent, text: String): File {
        val rawSender = referrer?.host?.replace(".", "_")
            ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)?.take(20)
            ?: "SharedText"
        val sender = rawSender.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(30)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = tempDir.resolve("${sender}_${timestamp}.txt")
        file.writeText(text)
        return file
    }

    // ── Destination dialog ───────────────────────────────────────────────────

    private fun showDestinationDialog() {
        FileOperationDestinationDialog(
            context = this,
            operationType = FileOperationType.COPY,
            sourceFiles = cachedFiles,
            sourceFolderName = getString(R.string.receive_share_source_name),
            currentResourceId = -1L,
            currentBrowsePath = null,
            sourceCredentialsId = null,
            fileOperationUseCase = fileOperationUseCase,
            getDestinationsUseCase = getDestinationsUseCase,
            overwriteFiles = false,
            showDetailedErrors = BuildConfig.DEBUG,
            onComplete = { cleanupAndFinish() },
            onSelectFolderClicked = { _, _, _ ->
                folderPickerActive = true
                folderPickerLauncher.launch(null)
            }
        ).apply {
            setOnDismissListener {
                // Guard: when "Select Folder" was clicked, the picker is active — skip cleanup here
                if (!folderPickerActive) cleanupAndFinish()
            }
            show()
        }
    }

    // ── SAF folder copy ──────────────────────────────────────────────────────

    private fun copyToSafFolder(treeUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val rootDoc = DocumentFile.fromTreeUri(this@ReceiveShareActivity, treeUri)
                    ?: throw IllegalStateException("Cannot access selected folder")

                cachedFiles.forEach { file ->
                    val mime = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(file.extension.lowercase())
                        ?: "application/octet-stream"
                    val newDoc = rootDoc.createFile(mime, file.name)
                        ?: throw IllegalStateException("Cannot create ${file.name} in selected folder")
                    contentResolver.openOutputStream(newDoc.uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ReceiveShareActivity, R.string.receive_share_copied_to_folder, Toast.LENGTH_SHORT).show()
                    cleanupAndFinish()
                }
            } catch (e: Exception) {
                Timber.e(e, "ReceiveShareActivity: SAF copy failed")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ReceiveShareActivity,
                        getString(R.string.receive_share_copy_to_folder_failed, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                    cleanupAndFinish()
                }
            }
        }
    }

    // ── Lifecycle helpers ────────────────────────────────────────────────────

    private fun showLoadingDialog(): AlertDialog {
        return AlertDialog.Builder(this)
            .setMessage(R.string.receive_share_preparing)
            .setCancelable(false)
            .show()
    }

    private fun cleanupAndFinish() {
        if (isFinishTriggered) return
        isFinishTriggered = true
        cachedFiles.forEach { it.delete() }
        tempDir.delete()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Best-effort cleanup if Activity is killed before cleanupAndFinish() runs
        if (!isFinishTriggered) {
            cachedFiles.forEach { it.delete() }
            tempDir.delete()
        }
    }
}
