package com.sza.fastmediasorter.ui.player

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Standalone Activity for playing/viewing media opened from external sources (Intent.ACTION_VIEW).
 * Detached from the main resource/database tree — no resource system, no playlists, no history.
 */
@AndroidEntryPoint
class StandalonePlayerActivity : BaseActivity<ActivityPlayerUnifiedBinding>() {

    private val viewModel: StandalonePlayerViewModel by viewModels()

    override fun getViewBinding(): ActivityPlayerUnifiedBinding {
        return ActivityPlayerUnifiedBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        setupCloseButton()
        setupBackPressHandler()
        hidePlaylistControls()
        parseIncomingIntent()
    }

    override fun observeData() {
        observeViewModelState()
        observeViewModelEvents()
    }

    // ── Intent Parsing (Step 1.3) ──────────────────────────────────────────

    private fun parseIncomingIntent() {
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
            else -> intent?.data
        }

        if (uri == null) {
            Timber.w("StandalonePlayer: no URI in intent, finishing")
            Toast.makeText(this, R.string.error_opening_file_simple, Toast.LENGTH_SHORT).show()
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

    // ── Close Button & Back Navigation (Step 1.4) ─────────────────────────

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

    // ── Media Type Routing (Step 1.5) ─────────────────────────────────────

    private fun observeViewModelState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    if (state.isLoading) {
                        // TODO: show loading indicator
                        return@collect
                    }

                    state.errorMessage?.let { error ->
                        Timber.w("StandalonePlayer: error state — $error")
                        Toast.makeText(this@StandalonePlayerActivity, error, Toast.LENGTH_SHORT).show()
                        finish()
                        return@collect
                    }

                    val file = state.mediaFile ?: return@collect

                    when (state.mediaType) {
                        MediaType.IMAGE -> {
                            Timber.d("StandalonePlayer: routing to IMAGE viewer for ${file.name}")
                            // TODO: wire ImageLoadingManager
                        }
                        MediaType.GIF -> {
                            Timber.d("StandalonePlayer: routing to GIF viewer for ${file.name}")
                            // TODO: wire ImageLoadingManager (GIF mode)
                        }
                        MediaType.VIDEO -> {
                            Timber.d("StandalonePlayer: routing to VIDEO player for ${file.name}")
                            // TODO: wire VideoPlayerManager (single file, no playlist)
                        }
                        MediaType.AUDIO -> {
                            Timber.d("StandalonePlayer: routing to AUDIO player for ${file.name}")
                            // TODO: wire AudioPlaybackService (single file, no playlist)
                        }
                        MediaType.PDF -> {
                            Timber.d("StandalonePlayer: routing to PDF viewer for ${file.name}")
                            // TODO: wire PdfViewerManager
                        }
                        MediaType.EPUB -> {
                            Timber.d("StandalonePlayer: routing to EPUB viewer for ${file.name}")
                            // TODO: wire EpubViewerManager
                        }
                        MediaType.TEXT -> {
                            Timber.d("StandalonePlayer: routing to TEXT viewer for ${file.name}")
                            // TODO: wire TextViewerManager
                        }
                        else -> {
                            Timber.w("StandalonePlayer: unsupported type ${state.mediaType} for ${file.name}")
                            Toast.makeText(this@StandalonePlayerActivity, R.string.unsupported_format_use_external_player, Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
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
