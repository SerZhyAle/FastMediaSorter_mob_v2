package com.sza.fastmediasorter.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.data.link.auth.KnownAuthResource
import com.sza.fastmediasorter.data.link.auth.KnownAuthResources
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.share.helpers.AccountSelectionManager
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.ui.dialog.FileOperationDestinationDialog
import com.sza.fastmediasorter.ui.share.auth.WebViewAuthDialogFragment
import com.sza.fastmediasorter.worker.LinkDownloadWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var authSessionRepository: AuthSessionRepository

    private lateinit var accountSelectionManager: AccountSelectionManager

    companion object {
        /**
         * S0161: when set on an incoming Intent, the Activity skips the normal share-intent
         * flow and immediately runs [maybeOfferAuthThenDownload] for the given URL.
         * Used by [LinkDownloadWorker] to re-open this Activity from a "Sign in" notification
         * action after a background download returned [SocialPreviewOnly].
         */
        const val EXTRA_REAUTH_URL = "extra_reauth_url"
    }

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
        accountSelectionManager = AccountSelectionManager(authSessionRepository)

        // S0161: re-auth flow initiated from a background download result notification.
        // The URL was passed via a PendingIntent from LinkDownloadWorker when the worker
        // received a SocialPreviewOnly result and could not show dialogs itself.
        // Must be checked BEFORE the action guard below, which would reject this Intent.
        val reAuthUrl = intent?.getStringExtra(EXTRA_REAUTH_URL)
        if (reAuthUrl != null) {
            maybeOfferAuthThenDownload(reAuthUrl)
            return
        }

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
                // S0003: when no stream is attached, inspect EXTRA_TEXT for an http(s) URL
                // and route through the auto-download channel if the user opted in.
                val streams = withContext(Dispatchers.IO) { extractStreams(intent) }
                if (streams.isEmpty()) {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    val urls = UrlInTextDetector.httpUrls(text)
                    val settings = settingsRepository.getSettings().first()
                    if (urls.isNotEmpty() && settings.linkAutoDownloadEnabled) {
                        loadingDialog.dismiss()
                        if (urls.size == 1) {
                            maybeOfferAuthThenDownload(urls.first())
                        } else {
                            processLinkAutoDownloadBatch(urls)
                        }
                        return@launch
                    }
                }
                val files = withContext(Dispatchers.IO) { extractAndCacheFiles(intent, streams) }
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
                    getString(R.string.receive_share_cache_failed),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun extractAndCacheFiles(intent: Intent, streams: List<Uri>): List<File> {
        return if (streams.isNotEmpty()) {
            cacheStreams(streams)
        } else {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) listOf(createTextFile(intent, text)) else emptyList()
        }
    }

    /**
     * S0144/S0155/S0157: for any http(s) URL, determine which account to use
     * (0 and not dismissed → offer auth; 0 but dismissed → proceed without auth;
     * 1 → use silently; ≥2 → show picker) then start the download.
     */
    private fun maybeOfferAuthThenDownload(url: String) {
        val host = Uri.parse(url).host.orEmpty()
        if (host.isBlank()) {
            processLinkAutoDownload(url, accountId = null)
            return
        }
        val resource = KnownAuthResources.matchHost(host)
        lifecycleScope.launch {
            if (authSessionRepository.isDismissedForHost(host)) {
                processLinkAutoDownload(url, accountId = null)
                return@launch
            }
            accountSelectionManager.selectAccount(
                host = host,
                activity = this@ReceiveShareActivity,
                onSelected = { account ->
                    processLinkAutoDownload(url, accountId = account.accountId)
                },
                onNoneAvailable = {
                    // No accounts saved for this host — offer auth.
                    offerAuthThenDownload(url, host, resource)
                },
                onCancelled = {
                    cleanupAndFinish()
                },
            )
        }
    }

    /**
     * S0144/S0157: 3-button auth offer when no accounts are saved for [host].
     * Add → WebView login; Skip (neutral) → proceed without auth, no dismissal;
     * Don't ask (negative) → mark dismissed, proceed without auth.
     * [resource] is non-null for known platforms, null for unknown hosts.
     */
    private fun offerAuthThenDownload(url: String, host: String, resource: KnownAuthResource?) {
        val displayLabel = resource?.displayName ?: host
        val loginUrl = resource?.loginUrl ?: url
        MaterialAlertDialogBuilder(this@ReceiveShareActivity)
            .setTitle(getString(R.string.auth_offer_dialog_title, displayLabel))
            .setMessage(getString(R.string.auth_offer_dialog_message, displayLabel))
            .setCancelable(false)
            .setPositiveButton(R.string.auth_offer_dialog_add) { _, _ ->
                supportFragmentManager.setFragmentResultListener(
                    WebViewAuthDialogFragment.RESULT_KEY,
                    this@ReceiveShareActivity,
                ) { _, bundle ->
                    supportFragmentManager.clearFragmentResultListener(WebViewAuthDialogFragment.RESULT_KEY)
                    // S0155: use the accountId the fragment just created — avoids passing
                    // null when accounts went from 0→1 (root cause of the bug in on-device test).
                    val savedAccountId = bundle.getString(WebViewAuthDialogFragment.RESULT_ACCOUNT_ID)
                    processLinkAutoDownload(url, accountId = savedAccountId)
                }
                WebViewAuthDialogFragment.newInstance(loginUrl)
                    .show(supportFragmentManager, "s0157_webview_auth_offer")
            }
            .setNeutralButton(R.string.auth_offer_dialog_skip) { _, _ ->
                // Skip for now — no dismissal recorded; offer will appear again next time.
                processLinkAutoDownload(url, accountId = null)
            }
            .setNegativeButton(R.string.s0157_auth_offer_dismiss_always) { _, _ ->
                lifecycleScope.launch { authSessionRepository.markDismissed(host) }
                processLinkAutoDownload(url, accountId = null)
            }
            .show()
    }

    /**
     * S0161: enqueue [url] as a background [LinkDownloadWorker] job and immediately
     * finish the Activity so the user can return to the source app (Instagram, browser…).
     *
     * Auth dialogs (WebView, account picker) always complete *before* this is called —
     * the worker only performs the HTTP download, not any interactive auth.
     *
     * S0155: [accountId] identifies the account whose cookies the coordinator should use;
     * null means no specific account (falls back to store default).
     */
    private fun processLinkAutoDownload(url: String, accountId: String?) {
        Timber.i("ReceiveShareActivity: enqueue link download url=%s accountId=%s", url, accountId)
        val request = OneTimeWorkRequestBuilder<LinkDownloadWorker>()
            .setInputData(
                workDataOf(
                    LinkDownloadWorker.KEY_URL to url,
                    LinkDownloadWorker.KEY_ACCOUNT_ID to accountId,
                ),
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        // REPLACE: if the user shares the same URL again before the previous job finishes,
        // restart with fresh auth credentials rather than silently skipping the new share.
        WorkManager.getInstance(this)
            .enqueueUniqueWork("link_dl_${url.hashCode()}", ExistingWorkPolicy.REPLACE, request)
        cleanupAndFinish()
    }

    /**
     * S0161: batch variant — enqueues all [urls] in a single [LinkDownloadWorker] job
     * (coordinator.handleBatch handles ordering) and returns the user to the source app.
     */
    private fun processLinkAutoDownloadBatch(urls: List<String>) {
        Timber.i("ReceiveShareActivity: enqueue batch download count=%d", urls.size)
        val request = OneTimeWorkRequestBuilder<LinkDownloadWorker>()
            .setInputData(
                workDataOf(LinkDownloadWorker.KEY_URLS to urls.toTypedArray()),
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(this).enqueue(request)
        cleanupAndFinish()
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
                        getString(R.string.receive_share_copy_to_folder_failed),
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) { finish(); return true }
        return super.onKeyDown(keyCode, event)
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
