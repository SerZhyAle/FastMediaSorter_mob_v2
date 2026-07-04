package com.sza.fastmediasorter.ui.streams

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuItemCompat
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.databinding.ActivityStreamsBinding
import com.sza.fastmediasorter.databinding.DialogAddStreamBinding
import com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior
import com.sza.fastmediasorter.domain.model.SyntheticResourceIds
import com.sza.fastmediasorter.domain.usecase.streams.PinnedStreamMove
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.helpers.AudioExitAction
import com.sza.fastmediasorter.ui.player.helpers.AudioExitBehaviorResolver
import com.sza.fastmediasorter.ui.player.helpers.AudioServiceController
import com.sza.fastmediasorter.ui.player.helpers.BackgroundAudioExitDialog
import com.sza.fastmediasorter.ui.streams.helpers.StreamFrameSnapshotManager
import com.sza.fastmediasorter.ui.streams.helpers.StreamGridModeManager
import com.sza.fastmediasorter.ui.streams.helpers.StreamHealthProbeManager
import com.sza.fastmediasorter.ui.streams.helpers.StreamInlineAudioManager
import com.sza.fastmediasorter.ui.streams.helpers.StreamScrollButtonManager
import com.sza.fastmediasorter.ui.streams.helpers.StreamShortcutPinManager
import com.sza.fastmediasorter.ui.streams.helpers.StreamsControlsPlacementManager
import com.sza.fastmediasorter.ui.streams.helpers.StreamsFilterDialogManager
import com.sza.fastmediasorter.data.repository.streams.FaviconAtlasStore
import com.sza.fastmediasorter.data.repository.streams.StreamFrameCache
import com.sza.fastmediasorter.data.repository.streams.StreamFramePersistentStore
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * The "Трансляции" list screen - the single destination every entry-point opens. Tapping an AUDIO
 * source plays it inline via [StreamInlineAudioManager] (the list stays visible); a VIDEO/RTSP source
 * opens the existing fullscreen player. All logic lives in the ViewModel/managers (Rule 3, Rule 5).
 *
 * Extends [BaseActivity] rather than a bare AppCompatActivity so the screen inherits the project's
 * locale wrapping (otherwise the in-app language never reaches its strings), custom color theme
 * overlay, edge-to-edge, keep-screen-on and TV/D-pad/mouse plumbing - matching every other
 * full-screen host (e.g. StatisticsActivity).
 */
@UnstableApi
@AndroidEntryPoint
class StreamsActivity : BaseActivity<ActivityStreamsBinding>() {

    private val viewModel: StreamsViewModel by viewModels()

    // S0668: favicon sprite-atlas sidecar (atlas PNG + url->index map) persisted by the catalog import.
    @Inject
    lateinit var faviconAtlasStore: FaviconAtlasStore

    // S0675: in-memory TTL cache of captured live-stream frames, shared between the snapshot engine
    // (writer) and the grid adapter (reader).
    @Inject
    lateinit var streamFrameCache: StreamFrameCache

    // S0712: on-disk last-frame thumbnails, written by the snapshot engine and pre-warmed into the
    // in-memory cache by the grid mode manager so known channels show their last frame on next launch.
    @Inject
    lateinit var streamFramePersistentStore: StreamFramePersistentStore

    // S0668: decodes a tile index into a 32 px bitmap, re-reading the atlas file on each (re)decode so
    // invalidate() after an import picks up the new atlas. Lazy so it is built after Hilt field injection.
    private val faviconSlicer by lazy { FaviconAtlasSlicer { faviconAtlasStore.atlasFile() } }

    // S0668: the loaded url->index map; read on the lambda thread at bind time. Volatile so a reload
    // after import is visible to the bind callbacks without further synchronisation.
    @Volatile
    private var faviconCoords: Map<String, Int> = emptyMap()

    private val adapter = StreamSourceAdapter(
        onPlay = ::onPlay,
        onPin = { viewModel.onPin(it) },
        onRemove = ::confirmRemove,
        onMoveUp = { viewModel.onMovePinned(it, PinnedStreamMove.UP) },
        onMoveDown = { viewModel.onMovePinned(it, PinnedStreamMove.DOWN) },
        onMoveToTop = { viewModel.onMovePinned(it, PinnedStreamMove.TO_TOP) },
        onAddShortcut = ::onAddShortcut,
        onEdit = ::showEditDialog,
        onShareLink = ::onShareLink,
        onToggleFavorite = { viewModel.toggleStreamFavorite(it) },
        favoritesEnabled = { viewModel.settings.value.enableFavorites },
        isFavorite = { viewModel.favoriteStreamUrls.value.contains(it.url) },
        faviconResolver = { url -> faviconCoords[url] },
        faviconTileLoader = { index -> faviconSlicer.tileFor(index) },
        faviconScope = lifecycleScope,
    )

    // S0675: short-lived snapshot engine for grid mode; built lazily so Hilt field injection (the cache)
    // has run. Uses applicationContext so a config-change does not leak the Activity into a capture.
    private val snapshotManager by lazy {
        // S0933: the ExoPlayer/decoder still uses applicationContext (no Activity leak), but the capture
        // TextureView needs a window-attached host - the off-screen streamCaptureHost in this layout.
        StreamFrameSnapshotManager(
            applicationContext,
            streamFrameCache,
            lifecycleScope,
            streamFramePersistentStore,
            hostProvider = { binding.streamCaptureHost },
        )
    }

    // S0675: grid-mode adapter mirroring the list adapter's favicon plumbing; the cached frame is the
    // primary content, with the favicon/placeholder as the no-frame fallback.
    private val gridAdapter by lazy {
        StreamGridAdapter(
            onPlay = ::onPlay,
            onPin = { viewModel.onPin(it) },
            onRemove = ::confirmRemove,
            onMoveUp = { viewModel.onMovePinned(it, PinnedStreamMove.UP) },
            onMoveDown = { viewModel.onMovePinned(it, PinnedStreamMove.DOWN) },
            onMoveToTop = { viewModel.onMovePinned(it, PinnedStreamMove.TO_TOP) },
            onAddShortcut = ::onAddShortcut,
            onEdit = ::showEditDialog,
            onShareLink = ::onShareLink,
            onToggleFavorite = { viewModel.toggleStreamFavorite(it) },
            favoritesEnabled = { viewModel.settings.value.enableFavorites },
            isFavorite = { viewModel.favoriteStreamUrls.value.contains(it.url) },
            frameProvider = streamFrameCache::get,
            requestCapture = snapshotManager::request,
            faviconResolver = { url -> faviconCoords[url] },
            faviconTileLoader = { index -> faviconSlicer.tileFor(index) },
            faviconScope = lifecycleScope,
        )
    }

    private lateinit var gridModeManager: StreamGridModeManager

    private lateinit var controlsPlacement: StreamsControlsPlacementManager

    private lateinit var inlineAudio: StreamInlineAudioManager

    // S0587: file-browser-style scroll-navigation buttons (top / page-up / page-down / bottom).
    private lateinit var scrollButtons: StreamScrollButtonManager

    /** Last rendered state, kept so the filter dialog can populate its facet choices on demand. */
    private var latestState = StreamsViewModel.StreamsUiState()

    // S0675: the display mode last applied to the RecyclerView; null until the first state arrives, so the
    // initial restored mode always triggers an applyMode (the manager swaps adapter/layout exactly once).
    private var appliedDisplayMode: DisplayMode? = null

    // S0699: the saved list position to restore (from RestoreScroll); applied once the row exists.
    private var pendingScrollTarget: Int? = null
    private var scrollRestored = false

    private val filterDialogManager by lazy { StreamsFilterDialogManager(this) }

    // S0700: reachability sweep over the visible rows on the refresh action. applicationContext so a
    // config-change never leaks the Activity into a probe's short-lived ExoPlayer.
    private val healthProbe by lazy {
        StreamHealthProbeManager(
            context = applicationContext,
            scope = lifecycleScope,
            // S0700: a reachability probe updates the status bullet (green/amber, never red) and is not a play.
            onStatus = { id, ok -> viewModel.recordStreamProbeOutcome(id, ok) },
        )
    }

    /** S0577: set when the user chose to keep a background stream playing on exit (skip teardown). */
    private var keepBackgroundService = false

    override fun getViewBinding(): ActivityStreamsBinding =
        ActivityStreamsBinding.inflate(layoutInflater)

    override fun getMouseScrollTargetView(): View = binding.rvStreams

    override fun getInitialFocusView(): View = binding.toolbar

    override fun setupViews() {
        inlineAudio = StreamInlineAudioManager(
            lifecycleOwner = this,
            miniControl = binding.streamMiniControl,
            titleView = binding.tvMiniTitle,
            playStopButton = binding.btnMiniPlayStop,
            audioController = AudioServiceController(this),
            onPlayingChanged = adapter::setPlayingId,
            onError = ::showStreamUnavailable,
            onSuccess = { viewModel.recordStreamOutcome(it.id, ok = true) },
        )
        // S0778: keep the bottom mini-control above the navigation bar / side cutout under edge-to-edge.
        inlineAudio.applyWindowInsets()

        binding.rvStreams.layoutManager = LinearLayoutManager(this)
        binding.rvStreams.adapter = adapter

        scrollButtons = StreamScrollButtonManager(
            recyclerView = binding.rvStreams,
            fabScrollToTop = binding.fabStreamsScrollToTop,
            fabPageUp = binding.fabStreamsPageUp,
            fabPageDown = binding.fabStreamsPageDown,
            fabScrollToBottom = binding.fabStreamsScrollToBottom,
        )
        scrollButtons.attach()

        gridModeManager = StreamGridModeManager(
            recyclerView = binding.rvStreams,
            swipeRefresh = binding.swipeStreams,
            listAdapter = adapter,
            gridAdapter = gridAdapter,
            snapshotManager = snapshotManager,
            cache = streamFrameCache,
            persistentStore = streamFramePersistentStore,
            lifecycleOwner = this,
            resources = resources,
            onToggleIconChanged = ::updateDisplayToggleIcon,
            // S0700: grid frame capture reports reachability (green/amber, never red; not a play).
            onStreamOutcome = { id, ok -> viewModel.recordStreamProbeOutcome(id, ok) },
        )

        binding.toolbar.setNavigationOnClickListener { exitStreamsWithAudioCheck() }
        onBackPressedDispatcher.addCallback(this) { exitStreamsWithAudioCheck() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_stream_add -> { showSourceDialog(isImport = false); true }
                R.id.action_stream_import -> { showImportChooser(); true }
                R.id.action_stream_display_toggle -> { cancelHealthProbe(); viewModel.onToggleDisplayMode(); true }
                R.id.action_stream_refresh -> { startHealthProbe(); true }
                else -> false
            }
        }
        tintToolbarMenuIcons()

        binding.etSearch.doAfterTextChanged {
            cancelHealthProbe()
            viewModel.onQueryChanged(it?.toString().orEmpty())
        }
        binding.btnFilter.setOnClickListener { cancelHealthProbe(); showFilterDialog() }
        binding.btnSort.setOnClickListener { cancelHealthProbe(); showSortDialog() }

        // S0940: in landscape the search/filter/sort group moves into the toolbar header to free
        // vertical space for the list/grid; place it for the launch orientation here, then keep it
        // in sync from onConfigurationChanged (this window does not recreate on rotation, S0692).
        controlsPlacement = StreamsControlsPlacementManager(
            controls = binding.streamControls,
            headerHost = binding.headerControlsHost,
        )
        val launchLandscape =
            resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        controlsPlacement.applyForOrientation(launchLandscape)

        // S0700: a user drag of the list aborts an in-flight reachability sweep (programmatic scrolls,
        // e.g. the S0699 position restore, settle without DRAGGING so they do not cancel it).
        binding.rvStreams.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) cancelHealthProbe()
            }
        })

        // S0673: empty-state actions reuse the toolbar handlers so the recovery path is one tap.
        binding.btnEmptyAddUrl.setOnClickListener { showSourceDialog(isImport = false) }
        binding.btnEmptyImport.setOnClickListener { showImportChooser() }

        // S0637: a home-screen shortcut may have launched this screen to play a specific stream.
        handlePlayIntent(intent)

        // S0659: apply the catalog-refresh policy once the managers are wired. The ViewModel keeps this
        // idempotent across config-change recreation, so calling it from every setupViews is safe.
        viewModel.onScreenOpened()

        // S0668: load the persisted favicon coords so already-imported rows can render their logo on
        // first paint. Off the UI thread (coords() is a suspend file read); the volatile field publishes
        // the result to the bind callbacks.
        loadFaviconCoords()
    }

    /** S0668: (re)load the url->index map and repaint visible rows so their favicon slots refresh. */
    private fun loadFaviconCoords() {
        lifecycleScope.launch {
            faviconCoords = faviconAtlasStore.coords()
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }
    }

    /**
     * S0668: a completed catalog import rewrote the atlas + coords sidecar - drop the cached atlas and
     * reload the coords so the new favicons appear without restarting the screen.
     */
    private fun onCatalogRefreshed() {
        lifecycleScope.launch {
            faviconSlicer.invalidate()
            faviconCoords = faviconAtlasStore.coords()
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }
    }

    /**
     * MaterialToolbar does not tint menu icons, and ic_add/ic_refresh ship a white fill ("tinted at
     * usage site"). Without an explicit tint they render white-on-light and look missing (only the
     * pre-tinted ic_import shows). The toolbar now uses a colorPrimary background (app header color
     * scheme), so tint to colorOnPrimary for contrast. android:iconTint on a menu item is API 26+,
     * but legacy minSdk is 23, so apply via MenuItemCompat in code.
     */
    private fun tintToolbarMenuIcons() {
        val tint = ColorStateList.valueOf(
            MaterialColors.getColor(binding.toolbar, com.google.android.material.R.attr.colorOnPrimary)
        )
        binding.toolbar.menu.forEach { item -> MenuItemCompat.setIconTintList(item, tint) }
    }

    /**
     * S0675: the toggle shows the icon/label of the mode it switches TO - grid glyph while in list,
     * list glyph while in grid (same convention as BrowseRecyclerViewManager.updateDisplayMode). Re-tint
     * so the swapped icon keeps the colorOnPrimary contrast.
     */
    private fun updateDisplayToggleIcon(mode: DisplayMode) {
        val item = binding.toolbar.menu.findItem(R.id.action_stream_display_toggle) ?: return
        when (mode) {
            DisplayMode.LIST -> {
                item.setIcon(R.drawable.ic_view_grid)
                item.setTitle(R.string.streams_view_grid)
            }
            DisplayMode.GRID -> {
                item.setIcon(R.drawable.ic_view_list)
                item.setTitle(R.string.streams_view_list)
            }
        }
        tintToolbarMenuIcons()
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.state) { state ->
            // S0675: a mode change swaps adapter + layout once; otherwise just keep the active adapter's
            // list current. The list adapter keeps its scroll-button callback in LIST mode.
            if (state.displayMode != appliedDisplayMode) {
                appliedDisplayMode = state.displayMode
                gridModeManager.applyMode(state.displayMode, state.sources)
                // The manager's swap re-submits the list without the scroll-button callback; refresh once.
                if (state.displayMode == DisplayMode.LIST && ::scrollButtons.isInitialized) {
                    binding.rvStreams.post { scrollButtons.updateVisibility() }
                }
            } else if (state.displayMode == DisplayMode.LIST) {
                adapter.submitList(state.sources) {
                    // S0587: recompute scroll-button visibility once the new list is laid out
                    // (filter/sort/search change the row count).
                    if (::scrollButtons.isInitialized) scrollButtons.updateVisibility()
                }
            } else {
                gridModeManager.submitCurrentList(state.sources)
            }
            binding.emptyStateView.isVisible = state.isEmpty
            latestState = state
            updateFilterIndicator(state.filter)
            // S0699: once the list carries the saved row, land on it (once per screen open).
            tryRestoreScroll(state.sources.size)
        }
        collectOnLifecycle(viewModel.events) { event ->
            when (event) {
                is StreamsViewModel.StreamsEvent.Message ->
                    Toast.makeText(this, event.messageResId, Toast.LENGTH_LONG).show()
                is StreamsViewModel.StreamsEvent.ImportFinished -> {
                    Toast.makeText(
                        this,
                        getString(R.string.streams_import_done, event.inserted),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                is StreamsViewModel.StreamsEvent.CatalogUpdated -> {
                    // S0668: the catalog import just rewrote the favicon atlas + coords - refresh them.
                    onCatalogRefreshed()
                    Toast.makeText(
                        this,
                        getString(
                            R.string.streams_catalog_updated,
                            event.added,
                            event.updated,
                            event.removed,
                        ),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                is StreamsViewModel.StreamsEvent.PlayRequested -> onPlay(event.source)
                is StreamsViewModel.StreamsEvent.RestoreScroll -> {
                    pendingScrollTarget = event.position
                    tryRestoreScroll(latestState.sources.size)
                }
                StreamsViewModel.StreamsEvent.SuggestCatalogRefresh -> showCatalogRefreshSuggestion()
            }
        }
    }

    /**
     * S0659: ON_OPEN refresh policy - offer a dismissible catalog update, never auto-download. The action
     * triggers the same curated-catalog import the toolbar uses; swiping/timeout dismisses it harmlessly.
     */
    private fun showCatalogRefreshSuggestion() {
        Snackbar.make(binding.rvStreams, R.string.streams_catalog_refresh_suggestion, Snackbar.LENGTH_LONG)
            .setAction(R.string.streams_catalog_refresh_action) { viewModel.onImportCatalog() }
            .show()
    }

    /**
     * S0700: the refresh action probes each currently-visible stream for reachability (green/red status)
     * and, in grid mode, re-captures the visible tiles' thumbnails. The sweep aborts on any later user
     * interaction (see [cancelHealthProbe]); the amber "unknown" stays the not-yet-probed default.
     */
    private fun startHealthProbe() {
        val visible = visibleSources()
        if (visible.isEmpty()) return
        Toast.makeText(this, R.string.streams_refresh_probing, Toast.LENGTH_SHORT).show()
        if (latestState.displayMode == DisplayMode.GRID && ::gridModeManager.isInitialized) {
            // Grid: the frame capture both renders the thumbnail and reports the VIDEO tile's status, so only
            // AUDIO tiles need the lightweight surfaceless probe - avoids two decoders racing per video tile.
            gridModeManager.refreshVisibleFrames()
            healthProbe.start(visible.filter { it.mediaKind == "AUDIO" })
        } else {
            healthProbe.start(visible)
        }
    }

    /** S0700: the rows/tiles currently on screen - the probe scope - read from the active layout manager. */
    private fun visibleSources(): List<StreamSourceEntity> {
        val layoutManager = binding.rvStreams.layoutManager as? LinearLayoutManager ?: return emptyList()
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        if (first < 0 || last < 0) return emptyList()
        val sources = latestState.sources
        return (first..last).mapNotNull { sources.getOrNull(it) }
    }

    /** S0700: stop the reachability sweep so it never fights a user interaction or runs after the user moved on. */
    private fun cancelHealthProbe() {
        healthProbe.cancel()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePlayIntent(intent)
    }

    /** S0637: resolve a home-screen shortcut's stream URL and play it; unknown URL shows a message. */
    private fun handlePlayIntent(intent: Intent?) {
        if (intent?.action != ACTION_PLAY_STREAM) return
        val url = intent.getStringExtra(EXTRA_STREAM_URL)?.takeIf { it.isNotBlank() } ?: return
        viewModel.playByUrl(url)
    }

    /** S0637: build a home-screen shortcut for the chosen channel; report if the launcher refuses. */
    private fun onAddShortcut(source: StreamSourceEntity) {
        val requested = StreamShortcutPinManager(this).requestPin(source)
        val message =
            if (requested) R.string.streams_shortcut_created else R.string.streams_shortcut_unsupported
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun onPlay(source: StreamSourceEntity) {
        // S0700: selecting a stream aborts an in-flight reachability sweep.
        cancelHealthProbe()
        // S0690: tapping the row/tile that is already playing inline toggles it off (stop), so the
        // same gesture both starts and stops the inline radio without hunting for the mini-control.
        // Stopping needs no network, so this toggle runs before the S0711 reachability gate below.
        if (source.mediaKind == "AUDIO" && inlineAudio.playingId == source.id) {
            inlineAudio.stop()
            return
        }
        // S0711: starting any stream needs at least one active transport. Refuse fast (no spinner, no
        // connection-timeout) when fully offline. Covers tile taps and the playByUrl shortcut path,
        // both of which funnel through onPlay.
        if (!viewModel.hasNetworkForStream()) {
            Toast.makeText(this, R.string.streams_error_no_network, Toast.LENGTH_SHORT).show()
            return
        }
        if (source.mediaKind == "AUDIO") {
            inlineAudio.play(source, useBackgroundService = isBackgroundAudioEnabled())
            return
        }
        // VIDEO / RTSP: open the existing fullscreen player. The stream URL is carried as the initial
        // path against the synthetic single-item resource id; the player classifies the scheme to a
        // stream ResourceType and routes it to the stream playback helper (S0565 Phase 04).
        Timber.i("StreamsActivity: launching fullscreen stream - %s", source.url)
        startActivity(
            PlayerActivity.createIntent(
                context = this,
                resourceId = SyntheticResourceIds.STREAM,
                initialFilePath = source.url,
                skipAvailabilityCheck = true,
                // S0694: a video stream opens straight into immersive fullscreen.
                enterFullscreen = true,
            )
        )
    }

    /**
     * S0577: leave the streams screen honoring the background-audio exit preference. Mirrors
     * PlayerLifecycleManager.exitPlayerWithAudioCheck via the shared resolver + dialog. Only ON-mode
     * (service) playback can continue in the background; OFF-mode audio is already torn down by onStop.
     */
    private fun exitStreamsWithAudioCheck() {
        when (
            AudioExitBehaviorResolver.resolve(
                serviceAudioActive = inlineAudio.isServiceAudioActive,
                player = inlineAudio.activeServicePlayer,
                behavior = viewModel.settings.value.backgroundAudioExitBehavior,
            )
        ) {
            AudioExitAction.FINISH -> {
                keepBackgroundService = inlineAudio.isServiceAudioActive
                finish()
            }
            AudioExitAction.STOP_AND_FINISH -> {
                inlineAudio.stop()
                finish()
            }
            AudioExitAction.ASK -> BackgroundAudioExitDialog.show(
                context = this,
                onStopThisTime = { inlineAudio.stop(); finish() },
                onContinueThisTime = { keepBackgroundService = true; finish() },
                onAlwaysStop = {
                    viewModel.updateExitBehavior(BackgroundAudioExitBehavior.ALWAYS_STOP)
                    inlineAudio.stop()
                    finish()
                },
                onAlwaysContinue = {
                    viewModel.updateExitBehavior(BackgroundAudioExitBehavior.ALWAYS_CONTINUE)
                    keepBackgroundService = true
                    finish()
                },
            )
        }
    }

    /**
     * S0581: a stream that did not respond. Offer to retry the same source or remove it from the
     * local list (every listed stream is a persisted DB row, so removal is always meaningful).
     */
    private fun showStreamUnavailable(source: StreamSourceEntity) {
        // S0593: the inline audio attempt failed -> record the red status for this source.
        viewModel.recordStreamOutcome(source.id, ok = false)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.streams_unavailable_title)
            .setMessage(getString(R.string.streams_unavailable_message, source.title))
            .setPositiveButton(R.string.retry) { _, _ ->
                inlineAudio.play(source, useBackgroundService = isBackgroundAudioEnabled())
            }
            .setNeutralButton(R.string.streams_remove) { _, _ -> viewModel.onRemove(source) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        DialogKeyboardDelegate.applyTo(dialog) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
        }
        dialog.show()
    }

    private fun confirmRemove(source: StreamSourceEntity) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.streams_remove)
            .setMessage(source.title)
            .setPositiveButton(R.string.streams_remove) { _, _ -> viewModel.onRemove(source) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        DialogKeyboardDelegate.applyTo(dialog) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
        }
        dialog.show()
    }

    private fun showSourceDialog(isImport: Boolean) {
        val dialogBinding = DialogAddStreamBinding.inflate(layoutInflater)
        dialogBinding.tilUrl.hint = getString(
            if (isImport) R.string.streams_import_url_hint else R.string.streams_add_url_hint
        )
        dialogBinding.tilTitle.isVisible = !isImport
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (isImport) R.string.streams_import else R.string.streams_add)
            .setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = dialogBinding.etUrl.text?.toString().orEmpty().trim()
                if (isImport) {
                    viewModel.onImport(url)
                } else {
                    viewModel.onAdd(url, dialogBinding.etTitle.text?.toString())
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        DialogKeyboardDelegate.applyTo(dialog) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
        }
        dialog.show()
        dialogBinding.etUrl.requestFocus()
    }

    /**
     * S0660: edit a manual channel in place. Reuses the add-stream dialog pre-filled with the current
     * url/title; the ViewModel preserves pin/sort/origin and only surfaces an invalid-url message.
     */
    private fun showEditDialog(source: StreamSourceEntity) {
        val dialogBinding = DialogAddStreamBinding.inflate(layoutInflater)
        dialogBinding.tilUrl.hint = getString(R.string.streams_add_url_hint)
        dialogBinding.tilTitle.isVisible = true
        dialogBinding.etUrl.setText(source.url)
        dialogBinding.etTitle.setText(source.title)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.streams_edit_dialog_title)
            .setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.onEdit(
                    source,
                    dialogBinding.etUrl.text?.toString().orEmpty().trim(),
                    dialogBinding.etTitle.text?.toString(),
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        DialogKeyboardDelegate.applyTo(dialog) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
        }
        dialog.show()
        dialogBinding.etUrl.requestFocus()
    }

    /** S0660: share the channel URL via the Android sharesheet (send-as-link, strategic §6.5). */
    private fun onShareLink(source: StreamSourceEntity) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, source.url)
        }
        startActivity(Intent.createChooser(sendIntent, getString(R.string.streams_share_chooser_title)))
    }

    /**
     * Two-way "Import list" chooser: one-tap curated-catalog update vs. the existing manual URL
     * import dialog. The Activity only forwards the choice - the catalog download lives in the
     * ViewModel/use case (Rule 3).
     */
    private fun showImportChooser() {
        val items = arrayOf(
            getString(R.string.streams_import_catalog),
            getString(R.string.streams_import_from_url),
        )
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.streams_import_choose_title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> viewModel.onImportCatalog()
                    1 -> showSourceDialog(isImport = true)
                }
            }
            .create()
        // Item-list dialog has no positive button; Escape-dismiss is the only added contract.
        DialogKeyboardDelegate.applyTo(dialog) {}
        dialog.show()
    }

    /**
     * Opens the category + language + country + media-kind filter dialog; the manager marshals
     * selections to the ViewModel.
     */
    private fun showFilterDialog() {
        filterDialogManager.show(latestState) { category, language, country, mediaKind, pinnedOnly ->
            viewModel.onFilter(
                category = category,
                language = language,
                country = country,
                mediaKind = mediaKind,
                pinnedOnly = pinnedOnly,
            )
        }
    }

    /** Marks an active filter on the filter button by a dot glyph (shape, not color alone). */
    private fun updateFilterIndicator(filter: StreamsViewModel.StreamsFilter) {
        val active = filter.category != null ||
            filter.language != null ||
            filter.country != null ||
            filter.mediaKind != StreamsViewModel.MediaKindFilter.ALL ||
            filter.pinnedOnly
        binding.btnFilter.setImageResource(if (active) R.drawable.ic_tune_active else R.drawable.ic_tune)
        binding.btnFilter.contentDescription =
            getString(if (active) R.string.streams_filter_active else R.string.streams_filter)
    }

    /**
     * S0699: scroll to the remembered position once the list actually contains that row, exactly once per
     * screen open. Clamped to the current bounds so a shorter list (filter/catalog change) still lands
     * cleanly. A GridLayoutManager is a LinearLayoutManager, so list + grid + multi-column all work.
     */
    private fun tryRestoreScroll(itemCount: Int) {
        if (scrollRestored) return
        val target = pendingScrollTarget ?: return
        if (itemCount <= 0) return
        val clamped = target.coerceIn(0, itemCount - 1)
        scrollRestored = true
        pendingScrollTarget = null
        binding.rvStreams.post {
            (binding.rvStreams.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(clamped, 0)
                ?: binding.rvStreams.scrollToPosition(clamped)
        }
    }

    /** Sort-mode picker mapping each label to a [StreamsViewModel.SortMode]. */
    private fun showSortDialog() {
        val modes = StreamsViewModel.SortMode.entries.toTypedArray()
        val labels = modes.map { getString(sortLabel(it)) }.toTypedArray()
        val checked = modes.indexOf(latestState.filter.sort).coerceAtLeast(0)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.streams_sort)
            .setSingleChoiceItems(labels, checked) { d, which ->
                viewModel.onSort(modes[which])
                d.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        // Single-choice list dismisses itself on pick; Escape-dismiss is the only added contract.
        DialogKeyboardDelegate.applyTo(dialog) {}
        dialog.show()
    }

    private fun sortLabel(mode: StreamsViewModel.SortMode): Int = when (mode) {
        StreamsViewModel.SortMode.NAME -> R.string.streams_sort_name
        StreamsViewModel.SortMode.TOPIC -> R.string.streams_sort_topic
        StreamsViewModel.SortMode.LANGUAGE -> R.string.streams_sort_language
        StreamsViewModel.SortMode.COUNTRY -> R.string.streams_sort_country
        StreamsViewModel.SortMode.RECENT -> R.string.streams_sort_recent
    }

    /** S0577: background streaming uses the foreground service only when the user enabled it and the flavor supports it. */
    private fun isBackgroundAudioEnabled(): Boolean =
        viewModel.settings.value.enablePersistentAudioPlayback && BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // S0692: this Activity handles orientation config changes itself (manifest configChanges), so the
        // list/grid column span must be recomputed here rather than via an Activity recreate.
        if (::gridModeManager.isInitialized) gridModeManager.onConfigurationChanged()
        // S0940: relocate the search/filter/sort group between header (landscape) and the below-toolbar
        // bar (portrait) live on rotation, since the window is not recreated here.
        if (::controlsPlacement.isInitialized) {
            val landscape = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            controlsPlacement.applyForOrientation(landscape)
        }
    }

    override fun onStop() {
        // S0700: a backgrounded screen aborts any in-flight reachability sweep.
        cancelHealthProbe()
        // S0699: remember where the user left the list so the next open lands on the same channel.
        (binding.rvStreams.layoutManager as? LinearLayoutManager)
            ?.findFirstVisibleItemPosition()
            ?.takeIf { it >= 0 }
            ?.let { viewModel.onScrollPositionChanged(it) }
        // S0577: OFF-mode (in-app) stream audio must not survive the screen going to background -
        // mirrors local audio. Service-mode (ON) playback is owned by AudioPlaybackService and left alone.
        if (::inlineAudio.isInitialized && inlineAudio.isLocalPlaybackActive) {
            inlineAudio.stop()
        }
        // S0675: never run frame captures while backgrounded - cancel in-flight snapshots + the timer.
        if (::gridModeManager.isInitialized) {
            gridModeManager.stop()
            snapshotManager.cancelAll()
        }
        super.onStop()
    }

    override fun onDestroy() {
        // setupViews() is deferred to a post{}; guard against destroy before it ran.
        if (::inlineAudio.isInitialized) {
            // S0577: on a background-continue exit, detach without stopping the service stream.
            if (keepBackgroundService) inlineAudio.releaseKeepingBackgroundService()
            else inlineAudio.release()
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY_STREAM = "com.sza.fastmediasorter.action.PLAY_STREAM"
        const val EXTRA_STREAM_URL = "extra_stream_url"

        /** Intent a pinned home-screen shortcut (S0637) carries to play one stream by its URL. */
        fun createPlayShortcutIntent(context: Context, url: String): Intent =
            Intent(context, StreamsActivity::class.java).apply {
                action = ACTION_PLAY_STREAM
                putExtra(EXTRA_STREAM_URL, url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        /**
         * S0756: in-app navigation that opens this screen and plays one stream by its URL (reusing the
         * same [handlePlayIntent] path as the shortcut, but without the launcher task flags so Back
         * returns to the caller). Used by the main-window streams panel's channel taps.
         */
        fun createPlayIntent(context: Context, url: String): Intent =
            Intent(context, StreamsActivity::class.java).apply {
                action = ACTION_PLAY_STREAM
                putExtra(EXTRA_STREAM_URL, url)
            }
    }
}
