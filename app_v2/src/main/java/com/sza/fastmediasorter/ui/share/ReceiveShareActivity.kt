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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.sza.fastmediasorter.worker.LinkDownloadProgressCodec
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
import com.sza.fastmediasorter.data.browser.CctAvailabilityChecker
import com.sza.fastmediasorter.data.browser.CctUnavailableException
import com.sza.fastmediasorter.data.browser.GoogleDomainBrowserLauncher
import com.sza.fastmediasorter.ui.share.auth.WebViewAuthDialogFragment
import com.sza.fastmediasorter.worker.LinkDownloadWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    @Inject lateinit var resultPresenter: LinkAutoDownloadResultPresenter
    @Inject lateinit var googleDomainBrowserLauncher: GoogleDomainBrowserLauncher
    @Inject lateinit var cctChecker: CctAvailabilityChecker

    private lateinit var accountSelectionManager: AccountSelectionManager

    companion object {
        /**
         * S0161: when set on an incoming Intent, the Activity skips the normal share-intent
         * flow and immediately runs [maybeOfferAuthThenDownload] for the given URL.
         * Used by [LinkDownloadWorker] to re-open this Activity from a "Sign in" notification
         * action after a background download returned [SocialPreviewOnly].
         */
        const val EXTRA_REAUTH_URL = "extra_reauth_url"

        /**
         * S0202: dedup key for [WorkManager.enqueueUniqueWork]. The KEEP policy ignores a
         * second share of the same URL ONLY while the prior work is still RUNNING/ENQUEUED;
         * once it finishes (any terminal state), a re-share starts a fresh worker.
         *
         * The key is derived from canonicalized host+path so tracking parameters
         * (utm_*, fbclid, etc.) do not split a single user share into multiple workers.
         */
        private fun uniqueWorkNameFor(url: String): String {
            val u = android.net.Uri.parse(url)
            val key = "${u.host.orEmpty()}${u.path.orEmpty()}"
            return "share_dl_" + Math.abs(key.hashCode()).toString(16)
        }
    }

    private val tempDir: File by lazy {
        cacheDir.resolve("temp_share").also { it.mkdirs() }
    }

    private var cachedFiles: List<File> = emptyList()
    private var isFinishTriggered = false
    private var folderPickerActive = false

    // S0170 BUG-1: the unknown-host auth offer must fire at most once per Activity instance.
    // Without this guard a host whose extraction keeps returning NoMediaFound (e.g. Facebook)
    // loops: offer → login → NoMediaFound → offer → … until the user escapes via Back.
    private var authOfferShown = false

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

        // S0161: re-auth flow initiated from the worker's "Sign in" notification action.
        // The user tapped "Sign in" after a download failed with SocialPreviewOnly.
        // Must be checked BEFORE the action guard below, which would reject this Intent.
        val reAuthUrl = intent?.getStringExtra(EXTRA_REAUTH_URL)
        if (reAuthUrl != null) {
            handleReAuthFromNotification(reAuthUrl)
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
                            routeSingleLinkAutoDownload(urls.first())
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
                Timber.i("[S0166] rejection record found for host, skipping auth dialog: host=%s", host)
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
                    Timber.i("[S0166] no stored auth for host: host=%s", host)
                    offerAuthThenDownload(url, host, resource)
                },
                onCancelled = {
                    cleanupAndFinish()
                },
            )
        }
    }

    /**
     * S0166 routes known social hosts through the explicit auth/account decision tree.
     * Unknown hosts keep the standard pipeline and only escalate later if extraction proves it is needed.
     */
    private fun routeSingleLinkAutoDownload(url: String) {
        val host = Uri.parse(url).host.orEmpty()
        val resource = KnownAuthResources.matchHost(host)
        if (resource != null) {
            Timber.i("[S0166] known social: host=%s", host)
            maybeOfferAuthThenDownload(url)
        } else {
            if (host.isNotBlank()) {
                Timber.i("[S0166] unknown host, standard pipeline: host=%s", host)
            }
            enqueueLinkDownloadSilent(url)
        }
    }

    /**
     * S0144/S0157: 3-button auth offer when no accounts are saved for [host].
     * Add → WebView login; Skip (neutral) → proceed without auth, no dismissal;
     * Don't ask (negative) → mark dismissed, proceed without auth.
     * [resource] is non-null for known platforms, null for unknown hosts.
     */
    private fun offerAuthThenDownload(
        url: String,
        host: String,
        resource: KnownAuthResource?,
        dialogType: String = "initial",
    ) {
        val displayLabel = resource?.displayName ?: host
        val loginUrl = resource?.loginUrl ?: url
        // S0170 BUG-1: once shown, never re-escalate for this Activity instance regardless of outcome.
        authOfferShown = true
        // S0211: resolve the existing account for [host] async, then build the dialog
        // with a named-account title when found. Falls back to the generic offer wording
        // when no record exists for the host.
        lifecycleScope.launch {
            val existing = runCatching {
                authSessionRepository.listAccountsForHost(host)
                    .filter { !it.isDismissed && it.cookieCount > 0 }
                    .maxByOrNull { it.lastUsedAt ?: java.time.Instant.MIN }
            }.getOrNull()
            val resolvedName = existing?.displayName?.trim()?.takeIf { it.isNotBlank() }
            Timber.d(
                "S0211: ReceiveShareActivity.offerAuthThenDownload resolvedName=%s host=%s",
                resolvedName ?: "<none>", host,
            )
            Timber.i(
                "[S0166] auth dialog shown: type=%s account=%s host=%s",
                dialogType, resolvedName ?: "none", host,
            )
            val title: String
            val message: String
            val positiveLabelRes: Int
            if (resolvedName != null) {
                title = getString(R.string.s0155_reauth_title, resolvedName)
                message = getString(R.string.s0155_reauth_message, resolvedName)
                positiveLabelRes = R.string.s0155_reauth_positive
            } else {
                title = getString(R.string.auth_offer_dialog_title, displayLabel)
                message = getString(R.string.auth_offer_dialog_message, displayLabel)
                positiveLabelRes = R.string.auth_offer_dialog_add
            }
            MaterialAlertDialogBuilder(this@ReceiveShareActivity)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(positiveLabelRes) { _, _ ->
                    Timber.i("[S0166] auth accepted: type=%s host=%s", dialogType, host)
                    supportFragmentManager.setFragmentResultListener(
                        WebViewAuthDialogFragment.RESULT_KEY,
                        this@ReceiveShareActivity,
                    ) { _, bundle ->
                        supportFragmentManager.clearFragmentResultListener(WebViewAuthDialogFragment.RESULT_KEY)
                        // S0155: use the accountId the fragment just created — avoids passing
                        // null when accounts went from 0→1 (root cause of the bug in on-device test).
                        val savedAccountId = bundle.getString(WebViewAuthDialogFragment.RESULT_ACCOUNT_ID)
                        Timber.d("S0155: offerAuthThenDownload resuming with accountId=%s", savedAccountId)
                        // S0170 BUG-1: mark as auth-retry so the escalation block (and the presenter's
                        // re-auth dialog) do not fire again on the post-login NoMediaFound / SocialPreviewOnly.
                        processLinkAutoDownload(url, accountId = savedAccountId, isAuthRetry = true)
                    }
                    openAuthFlow(loginUrl, "s0157_webview_auth_offer")
                }
                .setNeutralButton(R.string.auth_offer_dialog_skip) { _, _ ->
                    Timber.i("[S0166] auth dismissed (no record created): type=%s host=%s", dialogType, host)
                    // Skip for now — no dismissal recorded; offer will appear again next time the link is shared.
                    // S0170 BUG-1: isAuthRetry=true — do not re-escalate within this share session.
                    processLinkAutoDownload(url, accountId = null, isAuthRetry = true)
                }
                .setNegativeButton(R.string.s0157_auth_offer_dismiss_always) { _, _ ->
                    Timber.i("[S0166] auth dismissed with rejection record created: type=%s host=%s", dialogType, host)
                    // S0170 BUG-1: await markDismissed before re-running the pipeline — otherwise the
                    // escalation block can read a stale isDismissedForHost() and loop once.
                    lifecycleScope.launch {
                        authSessionRepository.markDismissed(host)
                        processLinkAutoDownload(url, accountId = null, isAuthRetry = true)
                    }
                }
                .show()
        }
    }

    /**
     * S0161: silently pick the best saved account for [url]'s host (last-used first;
     * null if none) then run a blocking download with progress dialog. No auth picker
     * is shown upfront — auth is offered ONLY if the download returns SocialPreviewOnly.
     */
    private fun enqueueLinkDownloadSilent(url: String) {
        val host = Uri.parse(url).host.orEmpty()
        lifecycleScope.launch {
            val accountId = if (host.isNotBlank()) {
                runCatching {
                    authSessionRepository.listAccountsForHost(host)
                        .filter { !it.isDismissed }
                        .maxByOrNull { it.lastUsedAt ?: java.time.Instant.MIN }
                        ?.accountId
                }.getOrNull()
            } else null
            processLinkAutoDownload(url, accountId)
        }
    }

    /**
     * S0161: entry point when the user taps "Sign in" on a worker result notification.
     *
     * If the host is already dismissed (“don’t ask” was previously chosen), skip the
     * auth dialog and re-enqueue the download as-is. Otherwise show the 3-button auth
     * offer immediately — bypassing the account-picker / silent-account path so the
     * user always gets the chance to add or refresh credentials.
     */
    private fun handleReAuthFromNotification(url: String) {
        val host = Uri.parse(url).host.orEmpty()
        lifecycleScope.launch {
            val dismissed = host.isNotBlank() && authSessionRepository.isDismissedForHost(host)
            if (dismissed) {
                // User previously said “don’t ask” — respect that, just re-enqueue.
                processLinkAutoDownload(url, accountId = null)
            } else {
                val resource = if (host.isNotBlank()) KnownAuthResources.matchHost(host) else null
                offerAuthThenDownload(url, host, resource, dialogType = "reauth")
            }
        }
    }
    /**
     * S0202: backgrounding-survival entry point. Shows [LinkAutoDownloadProgressDialog]
     * while a [LinkDownloadWorker] performs the download, then dismisses the activity
     * either when the worker finishes (within the 4-second watchdog) or when the watchdog
     * fires (whichever comes first). After watchdog the foreground notification owns the
     * UX; the activity is gone and the user can return to the source app.
     *
     * Cancel from the dialog routes through `WorkManager.cancelUniqueWork` so the worker
     * tears down its foreground notification and aborts at the next cancellation
     * checkpoint inside the coordinator (Phase 03 of S0202).
     *
     * S0166 §2 Step 0 unknown-host NoMediaFound escalation is preserved best-effort via
     * [handleNoMediaFoundEscalation] when the worker reports SUCCEEDED with the matching
     * result_kind before the watchdog fires.
     *
     * S0155: [accountId] identifies the account whose cookies the worker should use;
     * null means no specific account (coordinator falls back to the store default).
     */
    private fun processLinkAutoDownload(url: String, accountId: String?, isAuthRetry: Boolean = false) {
        Timber.i("ReceiveShareActivity: enqueue worker url=%s accountId=%s retry=%s", url, accountId, isAuthRetry)

        val progressDialog = LinkAutoDownloadProgressDialog(this@ReceiveShareActivity) {
            // Cancel — propagate to WorkManager so the worker tears down its foreground notification
            // and aborts in-flight extraction at the next ensureActive() checkpoint (Phase 03).
            WorkManager.getInstance(this@ReceiveShareActivity)
                .cancelUniqueWork(uniqueWorkNameFor(url))
            cleanupAndFinish()
        }
        progressDialog.show()

        val request = OneTimeWorkRequestBuilder<LinkDownloadWorker>()
            .setInputData(
                workDataOf(
                    LinkDownloadWorker.KEY_URL to url,
                    LinkDownloadWorker.KEY_ACCOUNT_ID to accountId,
                    LinkDownloadWorker.KEY_IS_AUTH_RETRY to isAuthRetry,
                ),
            )
            .build()
        val workName = uniqueWorkNameFor(url)
        WorkManager.getInstance(this)
            .enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)

        val workId = request.id
        WorkManager.getInstance(this)
            .getWorkInfoByIdLiveData(workId)
            .observe(this) { info ->
                if (info == null) return@observe
                when (info.state) {
                    WorkInfo.State.RUNNING -> {
                        val state = LinkDownloadProgressCodec.decode(info.progress)
                        if (state != null) progressDialog.update(state)
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        progressDialog.dismiss()
                        val resultKind = info.outputData.getString(LinkDownloadWorker.KEY_RESULT_KIND)
                        if (resultKind == "NoMediaFound") {
                            handleNoMediaFoundEscalation(url, accountId, isAuthRetry)
                        } else {
                            cleanupAndFinish()
                        }
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        progressDialog.dismiss()
                        cleanupAndFinish()
                    }
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> Unit
                }
            }

        // Watchdog: if the worker has not finished within 4 seconds, dismiss the dialog
        // and let the foreground notification take over the UX. Activity is dismissed so
        // the user can return to the source app immediately.
        lifecycleScope.launch {
            delay(4_000)
            if (!isFinishTriggered) {
                progressDialog.dismiss()
                cleanupAndFinish()
            }
        }
    }

    /**
     * S0166 §2 Step 0 (extracted by S0202): unknown-host NoMediaFound escalation. Invoked
     * from the WorkInfo observer when the worker reports SUCCEEDED with result_kind
     * "NoMediaFound". Best-effort — if the watchdog fired first, the activity is gone and
     * the foreground notification's "Sign in" path is the user's remaining route.
     *
     * S0170 BUG-1 invariants preserved: fires at most once per Activity instance
     * (authOfferShown), skips on isAuthRetry, skips when accountId != null, skips when
     * the host is dismissed.
     */
    private fun handleNoMediaFoundEscalation(url: String, accountId: String?, isAuthRetry: Boolean) {
        if (isAuthRetry || authOfferShown || accountId != null) {
            cleanupAndFinish()
            return
        }
        val hostForEscalation = Uri.parse(url).host.orEmpty()
        if (hostForEscalation.isBlank() || KnownAuthResources.matchHost(hostForEscalation) != null) {
            cleanupAndFinish()
            return
        }
        lifecycleScope.launch {
            val dismissed = runCatching { authSessionRepository.isDismissedForHost(hostForEscalation) }.getOrDefault(false)
            val hasActiveSession = runCatching {
                authSessionRepository.listAccountsForHost(hostForEscalation).any { !it.isDismissed }
            }.getOrDefault(false)
            if (!dismissed && !hasActiveSession) {
                Timber.i("[S0166] unknown host NoMediaFound, escalating to auth offer: host=%s", hostForEscalation)
                offerAuthThenDownload(url, hostForEscalation, resource = null, dialogType = "initial")
            } else {
                cleanupAndFinish()
            }
        }
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

    /**
     * S0200 — host-aware router. Google-domain URLs go to Chrome Custom Tabs; everything else
     * keeps the legacy WebView flow. CCT-unavailable triggers the refusal dialog.
     */
    private fun openAuthFlow(url: String, tag: String) {
        try {
            googleDomainBrowserLauncher.routeAuthUrl(this, url) { fallbackUrl ->
                WebViewAuthDialogFragment.newInstance(fallbackUrl).show(supportFragmentManager, tag)
            }
        } catch (e: CctUnavailableException) {
            Timber.w(e, "ReceiveShareActivity: CCT unavailable for url=%s", url)
            showCctUnavailableDialog { openAuthFlow(url, tag) }
        }
    }

    private fun showCctUnavailableDialog(onRetry: () -> Unit) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.s0200_cct_unavailable_title)
            .setMessage(R.string.s0200_cct_unavailable_message)
            .setPositiveButton(R.string.s0200_cct_unavailable_retry) { _, _ ->
                if (cctChecker.isAvailable()) onRetry() else showCctUnavailableDialog(onRetry)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
