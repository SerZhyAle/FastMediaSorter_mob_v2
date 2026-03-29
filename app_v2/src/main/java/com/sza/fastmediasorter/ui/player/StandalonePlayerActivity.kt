package com.sza.fastmediasorter.ui.player

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.network.FtpFileOperationHandler
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.SmbFileOperationHandler
import com.sza.fastmediasorter.data.network.SftpFileOperationHandler
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.player.helpers.StandaloneViewManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Standalone Activity for playing/viewing media opened from external sources (Intent.ACTION_VIEW
 * and Intent.ACTION_SEND). Detached from the main resource/database tree — no resource system,
 * no playlists, no history.
 *
 * All viewer routing is delegated to StandaloneViewManager.
 */
@AndroidEntryPoint
class StandalonePlayerActivity : BaseActivity<ActivityPlayerUnifiedBinding>() {

    companion object {
        private val DEFAULT_PLAYER_COMPONENT_SUFFIXES = listOf(
            ".StandaloneAudioPlayer",
            ".StandaloneVideoPlayer",
            ".StandaloneImagePlayer",
            ".StandaloneDocsPlayer",
            ".StandaloneAudioSender",
            ".StandaloneVideoSender",
            ".StandaloneImageSender"
        )
    }

    private val viewModel: StandalonePlayerViewModel by viewModels()

    // ── Delete permission launchers ───────────────────────────────────────
    // API 30+: MediaStore.createDeleteRequest auto-deletes after user grants permission
    private val batchDeleteLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val name = pendingDeleteFileName ?: return@registerForActivityResult
        if (result.resultCode == RESULT_OK) {
            onDeleteSuccess(name)
        } else {
            Timber.w("StandalonePlayer: batch delete denied by user for $name")
            Toast.makeText(this, getString(R.string.delete_failed, name), Toast.LENGTH_SHORT).show()
        }
        pendingDeleteFileName = null
    }

    // API 29: RecoverableSecurityException — user grants, then we retry delete
    private val recoverableDeleteLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val name = pendingDeleteFileName ?: return@registerForActivityResult
        if (result.resultCode == RESULT_OK) {
            val uri = pendingDeleteUri ?: return@registerForActivityResult
            retryDeleteAfterPermission(uri, name)
        } else {
            Timber.w("StandalonePlayer: recoverable delete denied for $name")
            Toast.makeText(this, getString(R.string.delete_failed, name), Toast.LENGTH_SHORT).show()
        }
        pendingDeleteFileName = null
        pendingDeleteUri = null
    }

    private var pendingDeleteFileName: String? = null
    private var pendingDeleteUri: Uri? = null

    // Injected network/cloud clients — needed to construct StandaloneViewManager's NetworkFileManager.
    // Not exercised for content:// URIs from external intents but required by the constructor.
    @Inject lateinit var smbClient: SmbClient
    @Inject lateinit var sftpClient: SftpClient
    @Inject lateinit var ftpClient: FtpClient
    @Inject lateinit var googleDriveClient: GoogleDriveRestClient
    @Inject lateinit var dropboxClient: DropboxClient
    @Inject lateinit var oneDriveClient: OneDriveRestClient
    @Inject lateinit var credentialsRepository: NetworkCredentialsRepository
    @Inject lateinit var smbFileOperationHandler: SmbFileOperationHandler
    @Inject lateinit var sftpFileOperationHandler: SftpFileOperationHandler
    @Inject lateinit var ftpFileOperationHandler: FtpFileOperationHandler
    @Inject lateinit var cloudFileOperationHandler: CloudFileOperationHandler
    @Inject lateinit var unifiedCache: UnifiedFileCache
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var playbackPositionRepository: PlaybackPositionRepository

    private lateinit var viewManager: StandaloneViewManager

    override fun getViewBinding(): ActivityPlayerUnifiedBinding {
        return ActivityPlayerUnifiedBinding.inflate(layoutInflater)
    }

    // Player layout has its own immersive insets handling — skip global edge-to-edge
    override fun shouldEnableEdgeToEdge(): Boolean = false

    override fun setupViews() {
        val t0 = if (BuildConfig.DEBUG) SystemClock.uptimeMillis() else 0L

        // DEBUG: detect probe URI BEFORE StandaloneViewManager construction to understand
        // whether WebView/ExoPlayer init happens needlessly for probe-only launches.
        if (BuildConfig.DEBUG) {
            val probeUri = when (intent?.action) {
                Intent.ACTION_VIEW -> intent?.data
                Intent.ACTION_SEND -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent?.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent?.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                }
                else -> intent?.data
            }
            val isProbe = probeUri?.toString()?.contains("default_player_probe") == true
            Timber.d("StandalonePlayer[debug]: setupViews START — isProbe=$isProbe uri=$probeUri")
        }

        val viewManagerT0 = if (BuildConfig.DEBUG) SystemClock.uptimeMillis() else 0L
        viewManager = StandaloneViewManager(
            activity = this,
            binding = binding,
            lifecycleScope = lifecycleScope,
            smbClient = smbClient,
            sftpClient = sftpClient,
            ftpClient = ftpClient,
            googleDriveClient = googleDriveClient,
            dropboxClient = dropboxClient,
            oneDriveClient = oneDriveClient,
            credentialsRepository = credentialsRepository,
            smbFileOperationHandler = smbFileOperationHandler,
            sftpFileOperationHandler = sftpFileOperationHandler,
            ftpFileOperationHandler = ftpFileOperationHandler,
            cloudFileOperationHandler = cloudFileOperationHandler,
            unifiedCache = unifiedCache,
            settingsRepository = settingsRepository,
            playbackPositionRepository = playbackPositionRepository
        )
        if (BuildConfig.DEBUG) Timber.d("StandalonePlayer[debug]: StandaloneViewManager() constructor done in ${SystemClock.uptimeMillis() - viewManagerT0}ms")

        setupCloseButton()
        setupBackPressHandler()
        hidePlaylistControls()
        setupFileOperationButtons()

        if (BuildConfig.DEBUG) Timber.d("StandalonePlayer[debug]: pre-parseIncomingIntent total=${SystemClock.uptimeMillis() - t0}ms")
        parseIncomingIntent()
        if (BuildConfig.DEBUG) Timber.d("StandalonePlayer[debug]: setupViews DONE total=${SystemClock.uptimeMillis() - t0}ms")
    }

    override fun observeData() {
        observeViewModelState()
        observeViewModelEvents()
        observeFavoriteState()
    }

    override fun onDestroy() {
        viewManager.release()
        super.onDestroy()
    }

    // ── Intent Parsing ────────────────────────────────────────────────────

    private fun parseIncomingIntent() {
        debugLogLaunchConditions(intent)

        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                // Open the first file from the list; multi-file playlist not supported standalone.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.firstOrNull()
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()
                }
            }
            else -> intent?.data
        }

        if (uri == null) {
            Timber.w("StandalonePlayer: no URI in intent, finishing")
            Toast.makeText(this, R.string.error_opening_file_simple, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Probe files created by DefaultPlayerHelper for the "set as default" flow must not be
        // played — they are 1-byte stubs and will crash viewers. Silently finish.
        if (uri.toString().contains("default_player_probe")) {
            Timber.d("StandalonePlayer: ignoring default-player probe URI, finishing")
            finish()
            return
        }

        val mimeType = intent.type

        val displayName = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        } catch (e: Exception) {
            Timber.w(e, "StandalonePlayer: failed to query display name")
            null
        } ?: uri.lastPathSegment

        Timber.d("StandalonePlayer: incoming uri=$uri mime=$mimeType name=$displayName")
        viewModel.loadFromUri(uri, mimeType, displayName)
    }

    private fun debugLogLaunchConditions(incomingIntent: Intent?) {
        if (!BuildConfig.DEBUG) return

        if (incomingIntent == null) {
            Timber.d("StandalonePlayer[debug]: launch intent is null")
            return
        }

        val categories = incomingIntent.categories?.joinToString(",") ?: "(none)"
        val clipCount = incomingIntent.clipData?.itemCount ?: 0
        val streamCount = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                incomingIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.size ?: 0
            } else {
                @Suppress("DEPRECATION")
                incomingIntent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.size ?: 0
            }
        }.getOrDefault(0)

        val resolvedUri = when (incomingIntent.action) {
            Intent.ACTION_VIEW -> incomingIntent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    incomingIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    incomingIntent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    incomingIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.firstOrNull()
                } else {
                    @Suppress("DEPRECATION")
                    incomingIntent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()
                }
            }
            else -> incomingIntent.data
        }

        val persistedGrant = resolvedUri?.let { uri ->
            contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        } ?: false

        val runtimeReadGrant = resolvedUri?.let { uri ->
            checkUriPermission(
                uri,
                android.os.Process.myPid(),
                android.os.Process.myUid(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
        } ?: false

        val aliasStates = DEFAULT_PLAYER_COMPONENT_SUFFIXES.joinToString(", ") { suffix ->
            val componentName = "$packageName$suffix"
            val stateLabel = try {
                when (packageManager.getComponentEnabledSetting(android.content.ComponentName(packageName, componentName))) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> "ENABLED"
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> "DISABLED"
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> "DEFAULT"
                    else -> "UNKNOWN"
                }
            } catch (e: Exception) {
                "ERROR:${e.javaClass.simpleName}"
            }
            "$suffix=$stateLabel"
        }

        Timber.i(
            "StandalonePlayer[debug]: launch action=%s component=%s categories=%s flags=0x%s type=%s hasData=%s clipItems=%d extraStreams=%d caller=%s referrer=%s",
            incomingIntent.action,
            incomingIntent.component?.className,
            categories,
            Integer.toHexString(incomingIntent.flags),
            incomingIntent.type,
            incomingIntent.data != null,
            clipCount,
            streamCount,
            callingActivity?.flattenToShortString(),
            referrer?.toString()
        )
        Timber.i(
            "StandalonePlayer[debug]: uri=%s scheme=%s authority=%s readGrant=%s persistedReadGrant=%s",
            resolvedUri,
            resolvedUri?.scheme,
            resolvedUri?.authority,
            runtimeReadGrant,
            persistedGrant
        )
        Timber.i(
            "StandalonePlayer[debug]: build debug=%s type=%s flavor=%s supportsDefaultPlayer=%s support(video=%s,audio=%s,images=%s,docs=%s,cloud=%s)",
            BuildConfig.DEBUG,
            BuildConfig.BUILD_TYPE,
            BuildConfig.FLAVOR,
            BuildConfig.SUPPORTS_DEFAULT_PLAYER,
            BuildConfig.SUPPORT_VIDEO,
            BuildConfig.SUPPORT_AUDIO,
            BuildConfig.SUPPORT_IMAGES,
            BuildConfig.SUPPORT_DOCUMENTS,
            BuildConfig.SUPPORT_CLOUD
        )
        Timber.i("StandalonePlayer[debug]: default-player components: %s", aliasStates)
    }

    // ── Close Button & Back Navigation ────────────────────────────────────

    private fun setupCloseButton() {
        binding.btnBack.setImageResource(R.drawable.ic_clear)
        binding.btnBack.setOnClickListener { finish() }
        binding.topCommandPanel.isVisible = true
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun hidePlaylistControls() {
        binding.btnPreviousCmd.isVisible = false
        binding.btnNextCmd.isVisible = false
    }

    // ── File Operation Buttons ───────────────────────────────────────────

    private fun setupFileOperationButtons() {
        // Delete
        binding.btnDeleteCmd.visibility = View.VISIBLE
        binding.btnDeleteCmd.setOnClickListener { deleteCurrentFile() }

        // Share
        binding.btnShareCmd.visibility = View.VISIBLE
        binding.btnShareCmd.setOnClickListener { shareCurrentFile() }

        // Favorite
        binding.btnFavorite.visibility = View.VISIBLE
        binding.btnFavorite.setOnClickListener { viewModel.toggleFavorite() }

        // Open in FMS (repurpose info button)
        binding.btnInfoCmd.visibility = View.VISIBLE
        binding.btnInfoCmd.setImageResource(R.drawable.ic_open_in_browse)
        binding.btnInfoCmd.contentDescription = getString(R.string.open_in_fms)
        binding.btnInfoCmd.setOnClickListener { openInFms() }

    }

    // ── Delete ────────────────────────────────────────────────────────────

    private fun deleteCurrentFile() {
        val file = viewModel.state.value.mediaFile ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_delete_title)
            .setMessage(getString(R.string.confirm_delete_standalone, file.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                performDelete(Uri.parse(file.contentUri ?: file.path), file.name)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performDelete(uri: Uri, fileName: String) {
        lifecycleScope.launch {
            try {
                when {
                    uri.scheme == "file" -> {
                        val deleted = File(uri.path!!).delete()
                        if (deleted) onDeleteSuccess(fileName)
                        else Toast.makeText(this@StandalonePlayerActivity,
                            getString(R.string.delete_failed, fileName), Toast.LENGTH_SHORT).show()
                    }

                    uri.scheme == "content" && DocumentsContract.isDocumentUri(this@StandalonePlayerActivity, uri) -> {
                        val deleted = DocumentsContract.deleteDocument(contentResolver, uri)
                        if (deleted) onDeleteSuccess(fileName)
                        else Toast.makeText(this@StandalonePlayerActivity,
                            getString(R.string.delete_failed, fileName), Toast.LENGTH_SHORT).show()
                    }

                    uri.scheme == "content" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                        // API 30+: system dialog auto-deletes after user grants
                        val pendingIntent = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                        pendingDeleteFileName = fileName
                        batchDeleteLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    }

                    uri.scheme == "content" -> {
                        // API 26–29: direct delete; on API 29 catch RecoverableSecurityException
                        try {
                            val rows = contentResolver.delete(uri, null, null)
                            if (rows > 0) onDeleteSuccess(fileName)
                            else Toast.makeText(this@StandalonePlayerActivity,
                                getString(R.string.delete_failed, fileName), Toast.LENGTH_SHORT).show()
                        } catch (se: SecurityException) {
                            @Suppress("NewApi")
                            val rse = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                                se as? android.app.RecoverableSecurityException else null
                            if (rse != null) {
                                pendingDeleteFileName = fileName
                                pendingDeleteUri = uri
                                recoverableDeleteLauncher.launch(
                                    androidx.activity.result.IntentSenderRequest.Builder(
                                        rse.userAction.actionIntent.intentSender
                                    ).build()
                                )
                            } else {
                                throw se
                            }
                        }
                    }

                    else -> {
                        Timber.w("StandalonePlayer: delete not supported for scheme=${uri.scheme}")
                        Toast.makeText(this@StandalonePlayerActivity,
                            getString(R.string.delete_failed, fileName), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: SecurityException) {
                Timber.w(e, "StandalonePlayer: non-recoverable delete permission denied for $fileName")
                binding.btnDeleteCmd.isVisible = false
                Toast.makeText(this@StandalonePlayerActivity,
                    R.string.delete_permission_denied, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Timber.e(e, "StandalonePlayer: delete failed for $fileName")
                Toast.makeText(this@StandalonePlayerActivity,
                    getString(R.string.delete_failed, e.message ?: fileName), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun onDeleteSuccess(fileName: String) {
        Toast.makeText(this, getString(R.string.file_deleted, fileName), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun retryDeleteAfterPermission(uri: Uri, fileName: String) {
        // Called after RecoverableSecurityException recovery on API 29 — permission now granted
        lifecycleScope.launch {
            try {
                val rows = contentResolver.delete(uri, null, null)
                if (rows > 0) onDeleteSuccess(fileName)
                else Toast.makeText(this@StandalonePlayerActivity,
                    getString(R.string.delete_failed, fileName), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Timber.e(e, "StandalonePlayer: retry delete failed for $fileName")
                Toast.makeText(this@StandalonePlayerActivity,
                    getString(R.string.delete_failed, e.message ?: fileName), Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Share ─────────────────────────────────────────────────────────────

    private fun shareCurrentFile() {
        val file = viewModel.state.value.mediaFile ?: return
        val uri = Uri.parse(file.contentUri ?: file.path)
        val mimeType = contentResolver.getType(uri) ?: "*/*"

        val shareUri = if (uri.scheme == "file") {
            try {
                FileProvider.getUriForFile(this, "$packageName.fileprovider", File(uri.path!!))
            } catch (e: Exception) {
                Timber.w(e, "StandalonePlayer: FileProvider failed, using original URI")
                uri
            }
        } else {
            uri
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)))
    }

    // ── Open in FMS ──────────────────────────────────────────────────────

    private fun openInFms() {
        val file = viewModel.state.value.mediaFile ?: return
        val uri = Uri.parse(file.contentUri ?: file.path)
        val localPath = resolveToLocalPath(uri)

        if (localPath != null) {
            val parentDir = File(localPath).parent
            lifecycleScope.launch {
                val resourceId = viewModel.findResourceForPath(parentDir)
                if (resourceId != null) {
                    startActivity(BrowseActivity.createIntent(
                        this@StandalonePlayerActivity,
                        resourceId = resourceId,
                        initialFilePath = localPath
                    ))
                } else {
                    launchMainActivity()
                }
                finish()
            }
        } else {
            launchMainActivity()
            finish()
        }
    }

    private fun launchMainActivity() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        })
    }

    @Suppress("DEPRECATION")
    private fun resolveToLocalPath(uri: Uri): String? {
        return when (uri.scheme) {
            "file" -> uri.path
            "content" -> {
                try {
                    contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getString(0) else null }
                } catch (e: Exception) {
                    Timber.d(e, "StandalonePlayer: could not resolve content URI to local path")
                    null
                }
            }
            else -> null
        }
    }

    // ── Favorite State Observation ───────────────────────────────────────

    private fun observeFavoriteState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isFavorite.collect { isFav ->
                    binding.btnFavorite.setImageResource(
                        if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star_outline
                    )
                    binding.btnFavorite.contentDescription = getString(
                        if (isFav) R.string.cd_remove_from_favorites else R.string.cd_add_to_favorites
                    )
                }
            }
        }
    }

    // ── Media Type Routing ────────────────────────────────────────────────

    private fun observeViewModelState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.progressBar.isVisible = state.isLoading

                    if (state.isLoading) return@collect

                    state.errorMessage?.let { error ->
                        Timber.w("StandalonePlayer: error state — $error")
                        Toast.makeText(this@StandalonePlayerActivity, error, Toast.LENGTH_SHORT).show()
                        finish()
                        return@collect
                    }

                    val file = state.mediaFile ?: return@collect
                    val type = state.mediaType ?: return@collect

                    if (type == MediaType.BINARY_ARCHIVE || type == MediaType.BINARY_DISK ||
                        type == MediaType.BINARY_EXECUTABLE || type == MediaType.BINARY_OTHER) {
                        Timber.w("StandalonePlayer: unsupported binary type $type for ${file.name}")
                        Toast.makeText(
                            this@StandalonePlayerActivity,
                            R.string.unsupported_format_use_external_player,
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                        return@collect
                    }

                    viewManager.show(file, type)
                }
            }
        }
    }

    private fun observeViewModelEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is StandalonePlayerViewModel.StandalonePlayerEvent.ShowError -> {
                            Toast.makeText(this@StandalonePlayerActivity, event.message, Toast.LENGTH_SHORT).show()
                        }
                        is StandalonePlayerViewModel.StandalonePlayerEvent.FinishActivity -> {
                            finish()
                        }
                    }
                }
            }
        }
    }
}
