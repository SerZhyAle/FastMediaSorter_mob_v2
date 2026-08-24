package com.sza.fastmediasorter.ui.streams

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.MenuItemCompat
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.streams.ChannelPreviewAtlasStore
import com.sza.fastmediasorter.data.repository.streams.FaviconAtlasStore
import com.sza.fastmediasorter.data.repository.streams.StreamFrameCache
import com.sza.fastmediasorter.data.repository.streams.StreamFramePersistentStore
import com.sza.fastmediasorter.data.repository.streams.StreamLogoAtlasStore
import com.sza.fastmediasorter.databinding.ActivityStreamsBinding
import com.sza.fastmediasorter.databinding.DialogAddStreamBinding
import com.sza.fastmediasorter.domain.delivery.DeliverableInventory
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.SyntheticResourceIds
import com.sza.fastmediasorter.domain.streams.StreamFrameIngestor
import com.sza.fastmediasorter.domain.usecase.streams.PinnedStreamMove
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.helpers.AudioExitAction
import com.sza.fastmediasorter.ui.player.helpers.AudioExitBehaviorResolver
import com.sza.fastmediasorter.ui.player.helpers.AudioServiceController
import com.sza.fastmediasorter.ui.player.helpers.BackgroundAudioExitDialog
import com.sza.fastmediasorter.ui.streams.helpers.StreamAtlasPromptManager
import com.sza.fastmediasorter.ui.streams.helpers.StreamFrameSnapshotManager
import com.sza.fastmediasorter.ui.streams.helpers.StreamGridModeManager
import com.sza.fastmediasorter.ui.streams.helpers.StreamHealthProbeManager
import com.sza.fastmediasorter.ui.streams.helpers.StreamInfoDialogManager
import com.sza.fastmediasorter.ui.streams.helpers.StreamInlineAudioCallbacks
import com.sza.fastmediasorter.ui.streams.helpers.StreamInlineAudioManager
import com.sza.fastmediasorter.ui.streams.helpers.StreamInlineAudioViews
import com.sza.fastmediasorter.ui.streams.helpers.StreamScrollButtonManager
import com.sza.fastmediasorter.ui.streams.helpers.StreamShortcutPinManager
import com.sza.fastmediasorter.ui.streams.helpers.StreamsCommandLabelManager
import com.sza.fastmediasorter.ui.streams.helpers.StreamsControlsPlacementManager
import com.sza.fastmediasorter.ui.streams.helpers.StreamsFilterDialogManager
import com.sza.fastmediasorter.ui.streams.helpers.StreamsMediaKindTriggerManager
import com.sza.fastmediasorter.ui.streams.helpers.StreamsSectionsManager
import com.sza.fastmediasorter.util.showBoundToHost
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
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

    // S1154: on-demand channel-preview atlas store (sheet + url->index sidecar under the delivery dir).
    @Inject
    lateinit var channelPreviewAtlasStore: ChannelPreviewAtlasStore

    // S1201: on-demand station-logo atlas store (sheet + url->index sidecar under the delivery dir).
    @Inject
    lateinit var streamLogoAtlasStore: StreamLogoAtlasStore

    // S1154: routes the post-import atlas-download offer through the real WorkManager delivery path.
    @Inject
    lateinit var deliverableInventory: DeliverableInventory

    // S1483: supplies the artwork payload size the offer dialog states before an 8-11 MB download.
    @Inject
    lateinit var artworkManifest: com.sza.fastmediasorter.domain.delivery.ArtworkManifestSource

    // S1373: the compile-time "does this build ship background audio" axis has one sanctioned reader
    // (CLAUDE.md Rule 14). Reading the build flag here instead is what the rule forbids in shared code.
    @Inject
    lateinit var capabilityAvailability: com.sza.fastmediasorter.core.capability.CapabilityAvailability

    // S0675: in-memory TTL cache of captured live-stream frames, shared between the snapshot engine
    // (writer) and the grid adapter (reader).
    @Inject
    lateinit var streamFrameCache: StreamFrameCache

    // S0712: on-disk last-frame thumbnails, written by the snapshot engine and pre-warmed into the
    // in-memory cache by the grid mode manager so known channels show their last frame on next launch.
    @Inject
    lateinit var streamFramePersistentStore: StreamFramePersistentStore

    @Inject
    lateinit var streamFrameIngestor: StreamFrameIngestor

    // S1469: synchronous connectivity snapshot, asked before each snapshot request so a device that
    // opens this screen before its network is up does not sweep the whole visible list into timeouts.
    @Inject
    lateinit var networkContextAnalyzer: com.sza.fastmediasorter.core.network.NetworkContextAnalyzer

    // S0668: decodes a tile index into a 32 px bitmap, re-reading the atlas file on each (re)decode so
    // invalidate() after an import picks up the new atlas. Lazy so it is built after Hilt field injection.
    private val faviconSlicer by lazy { FaviconAtlasSlicer { faviconAtlasStore.atlasFile() } }

    // S0668: the loaded url->index map; read on the lambda thread at bind time. Volatile so a reload
    // after import is visible to the bind callbacks without further synchronisation.
    @Volatile
    private var faviconCoords: Map<String, Int> = emptyMap()

    // S1154: per-tile region-decode slicer for the channel-preview atlas (never decodes the full sheet).
    // S1445: it prefers the tile pack when one is installed - the sheet path is the fallback.
    private val atlasSlicer by lazy {
        ChannelPreviewAtlasSlicer(
            { channelPreviewAtlasStore.atlasFile() },
            StreamTilePackReader(
                { channelPreviewAtlasStore.tilePackFile() },
                StreamTilePackReader.budgetBytes(this),
            ),
        )
    }

    // S1154: url->tile-index map for the atlas preview; volatile for the same reason as faviconCoords.
    @Volatile
    private var atlasPreviewCoords: Map<String, Int> = emptyMap()

    // S1201: per-tile region-decode slicer for the logo atlas - same shape as the preview slicer, its
    // own geometry (square tiles, 59 columns).
    private val logoSlicer by lazy {
        StreamLogoAtlasSlicer(
            { streamLogoAtlasStore.atlasFile() },
            StreamTilePackReader(
                { streamLogoAtlasStore.tilePackFile() },
                StreamTilePackReader.budgetBytes(this),
            ),
        )
    }

    // S1201: url->tile-index map for the logo tier; volatile for the same reason as faviconCoords.
    @Volatile
    private var logoAtlasCoords: Map<String, Int> = emptyMap()

    // S1154: post-import "download the preview atlas?" offer. Lazy so it is built after Hilt injection.
    private val streamAtlasPromptManager by lazy {
        StreamAtlasPromptManager(
            deliverableInventory,
            lifecycleScope,
            DeliverableSet.CHANNEL_PREVIEW_ATLAS,
            R.string.streams_atlas_prompt_message,
            artworkManifest,
            ::reloadAtlasPreviews,
        )
    }

    // S1201: the same offer for the station-logo atlas. Two separate payloads, so two separate offers -
    // a user who only wants video previews should not be made to download logos to get them.
    private val streamLogoPromptManager by lazy {
        StreamAtlasPromptManager(
            deliverableInventory,
            lifecycleScope,
            DeliverableSet.STREAM_LOGO_ATLAS,
            R.string.streams_logo_prompt_message,
            artworkManifest,
            ::reloadLogoTiles,
        )
    }

    private val streamPlayerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@registerForActivityResult
        val url = result.data
            ?.getStringExtra(PlayerActivity.EXTRA_STREAM_THUMBNAIL_URL)
            ?: return@registerForActivityResult
        gridAdapter.repaintUrl(url)
    }

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
        onAboutChannel = { streamInfoDialogManager.show(it) },
        onToggleFavorite = { viewModel.toggleStreamFavorite(it) },
        favoritesEnabled = { viewModel.settings.value.enableFavorites },
        isFavorite = { viewModel.isFavoriteChannel(it) },
        onSendToWatch = { viewModel.sendStreamToWatch(it) },
        onOpenOnWatch = { viewModel.sendStreamToWatch(it, openNow = true) },
        wearSendAvailable = { viewModel.isWearSendAvailable },
        faviconResolver = { url -> faviconCoords[url] },
        faviconTileLoader = { index -> faviconSlicer.tileFor(index) },
        faviconScope = lifecycleScope,
    )

    // S1141: pinned-section list adapter - identical wiring to [adapter]. A RecyclerView adapter cannot be
    // attached to two RecyclerViews, so the top section needs its own instance.
    private val pinnedAdapter = StreamSourceAdapter(
        onPlay = ::onPlay,
        onPin = { viewModel.onPin(it) },
        onRemove = ::confirmRemove,
        onMoveUp = { viewModel.onMovePinned(it, PinnedStreamMove.UP) },
        onMoveDown = { viewModel.onMovePinned(it, PinnedStreamMove.DOWN) },
        onMoveToTop = { viewModel.onMovePinned(it, PinnedStreamMove.TO_TOP) },
        onAddShortcut = ::onAddShortcut,
        onEdit = ::showEditDialog,
        onShareLink = ::onShareLink,
        onAboutChannel = { streamInfoDialogManager.show(it) },
        onToggleFavorite = { viewModel.toggleStreamFavorite(it) },
        favoritesEnabled = { viewModel.settings.value.enableFavorites },
        isFavorite = { viewModel.isFavoriteChannel(it) },
        onSendToWatch = { viewModel.sendStreamToWatch(it) },
        onOpenOnWatch = { viewModel.sendStreamToWatch(it, openNow = true) },
        wearSendAvailable = { viewModel.isWearSendAvailable },
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
            streamFrameIngestor,
            hostProvider = { binding.streamCaptureHost },
            hasNetwork = { networkContextAnalyzer.hasAnyNetwork() },
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
            onAboutChannel = { streamInfoDialogManager.show(it) },
            onToggleFavorite = { viewModel.toggleStreamFavorite(it) },
            favoritesEnabled = { viewModel.settings.value.enableFavorites },
            isFavorite = { viewModel.isFavoriteChannel(it) },
            onSendToWatch = { viewModel.sendStreamToWatch(it) },
            onOpenOnWatch = { viewModel.sendStreamToWatch(it, openNow = true) },
            wearSendAvailable = { viewModel.isWearSendAvailable },
            frameProvider = streamFrameCache::get,
            requestCapture = snapshotManager::request,
            faviconResolver = { url -> faviconCoords[url] },
            faviconTileLoader = { index -> faviconSlicer.tileFor(index) },
            atlasPreviewLoader = { url -> atlasPreviewCoords[url]?.let { atlasSlicer.tileFor(it) } },
            logoTileLoader = { url -> logoAtlasCoords[url]?.let { logoSlicer.tileFor(it) } },
            faviconScope = lifecycleScope,
        )
    }

    // S1141: pinned-section snapshot engine + grid adapter, mirroring the main-section pair but bound to
    // the second off-screen capture host (binding.streamCaptureHostPinned) so the two engines never share
    // one TextureView. The frame cache is keyed by url and a channel is pinned XOR unpinned, so the two
    // sections' cache entries never collide.
    private val pinnedSnapshotManager by lazy {
        StreamFrameSnapshotManager(
            applicationContext,
            streamFrameCache,
            lifecycleScope,
            streamFrameIngestor,
            hostProvider = { binding.streamCaptureHostPinned },
            hasNetwork = { networkContextAnalyzer.hasAnyNetwork() },
        )
    }

    private val pinnedGridAdapter by lazy {
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
            onAboutChannel = { streamInfoDialogManager.show(it) },
            onToggleFavorite = { viewModel.toggleStreamFavorite(it) },
            favoritesEnabled = { viewModel.settings.value.enableFavorites },
            isFavorite = { viewModel.isFavoriteChannel(it) },
            onSendToWatch = { viewModel.sendStreamToWatch(it) },
            onOpenOnWatch = { viewModel.sendStreamToWatch(it, openNow = true) },
            wearSendAvailable = { viewModel.isWearSendAvailable },
            frameProvider = streamFrameCache::get,
            requestCapture = pinnedSnapshotManager::request,
            faviconResolver = { url -> faviconCoords[url] },
            faviconTileLoader = { index -> faviconSlicer.tileFor(index) },
            atlasPreviewLoader = { url -> atlasPreviewCoords[url]?.let { atlasSlicer.tileFor(it) } },
            logoTileLoader = { url -> logoAtlasCoords[url]?.let { logoSlicer.tileFor(it) } },
            faviconScope = lifecycleScope,
        )
    }

    private lateinit var gridModeManager: StreamGridModeManager

    private lateinit var pinnedGridModeManager: StreamGridModeManager

    // S1141: splits the ordered sources into pinned/unpinned, drives both section grid managers, and
    // auto-hides the pinned section when nothing is pinned.
    private lateinit var sectionsManager: StreamsSectionsManager

    private lateinit var controlsPlacement: StreamsControlsPlacementManager

    private lateinit var commandLabels: StreamsCommandLabelManager

    private lateinit var mediaKindTrigger: StreamsMediaKindTriggerManager

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

    // S1823: the inflated catalog-offer banner. Held because a ViewStub can only be inflated once, so
    // the second offer of a session has to reuse this view rather than ask the stub again.
    private var catalogBanner: View? = null
    private var scrollRestored = false

    private val streamScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) cancelHealthProbe()
        }
    }

    private val filterDialogManager by lazy { StreamsFilterDialogManager(this) }

    // S1474: the "about this channel" window. Both collaborators are passed as accessors rather than as
    // instances - `healthProbe` is lazy and `inlineAudio` is a lateinit created in onCreate, so reading
    // either at construction time would build one too early or throw.
    private val streamInfoDialogManager by lazy {
        StreamInfoDialogManager(
            context = this,
            healthProbe = { healthProbe },
            inlineAudio = { if (::inlineAudio.isInitialized) inlineAudio else null },
            playOutcome = { viewModel.playOutcomes.value[it.id] },
        )
    }

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

    // S1198: setupViews was a single 190-line method (LongMethod, threshold 80). Split into the
    // cohesive wiring blocks below; order matters - sections need adapters, placement needs the
    // media-kind trigger, and the entry actions run only after every manager is constructed.
    override fun setupViews() {
        setupInlineAudio()
        setupStreamSections()
        setupToolbarCommands()
        setupSearchAndFilters()
        setupOrientationPlacement()
        setupEntryActions()
    }

    private fun setupInlineAudio() {
        inlineAudio = StreamInlineAudioManager(
            lifecycleOwner = this,
            views = StreamInlineAudioViews(
                miniControl = binding.streamMiniControl,
                titleView = binding.tvMiniTitle,
                playStopButton = binding.btnMiniPlayStop,
            ),
            audioController = AudioServiceController(this),
            callbacks = StreamInlineAudioCallbacks(
                // S1141: a channel is pinned XOR unpinned, so the now-playing note must be pushed to both
                // list adapters - only the section holding it repaints, the other no-ops. S1142: the grid
                // adapters now also carry the active tile's now-playing track, so a new play/stop resets
                // it there too.
                onPlayingChanged = { id ->
                    adapter.setPlayingId(id)
                    pinnedAdapter.setPlayingId(id)
                    gridAdapter.setNowPlaying(id, null)
                    pinnedGridAdapter.setNowPlaying(id, null)
                },
                onPlaybackStateChanged = { source, isPlaying ->
                    if (isPlaying) source?.let(::persistStreamResume) else clearStreamResume()
                },
                onError = ::showStreamUnavailable,
                onSuccess = { viewModel.recordStreamPlaySuccess(it.id) },
                // S1142: mirror the live now-playing track onto the active channel's grid tile. The id
                // comes from the manager itself - reading it back via inlineAudio here crashed on the
                // init-time emit (the lateinit is not yet assigned while the constructor runs).
                onNowPlayingChanged = { id, track ->
                    gridAdapter.setNowPlaying(id, track)
                    pinnedGridAdapter.setNowPlaying(id, track)
                },
            ),
        )
        // S0778: keep the bottom mini-control above the navigation bar / side cutout under edge-to-edge.
        inlineAudio.applyWindowInsets()
    }

    private fun setupStreamSections() {
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
        // Same edge-to-edge fix as S0778's miniControl: the bottom scroll-button group is anchored to the
        // window edge (gravity bottom|end) and otherwise draws under the navigation bar / gesture inset.
        binding.streamScrollButtonsBottom.applySystemBarInsetPadding(applyLeft = false, applyTop = false)

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

        // S1141: pinned-section RecyclerView + its own grid-mode manager. The display-toggle icon is
        // owned by the main manager (shared toolbar action), so the pinned manager gets a no-op icon hook.
        binding.rvStreamsPinned.layoutManager = LinearLayoutManager(this)
        binding.rvStreamsPinned.adapter = pinnedAdapter

        pinnedGridModeManager = StreamGridModeManager(
            recyclerView = binding.rvStreamsPinned,
            swipeRefresh = binding.swipeStreamsPinned,
            listAdapter = pinnedAdapter,
            gridAdapter = pinnedGridAdapter,
            snapshotManager = pinnedSnapshotManager,
            cache = streamFrameCache,
            persistentStore = streamFramePersistentStore,
            lifecycleOwner = this,
            resources = resources,
            onToggleIconChanged = { },
            onStreamOutcome = { id, ok -> viewModel.recordStreamProbeOutcome(id, ok) },
        )

        sectionsManager = StreamsSectionsManager(
            pinnedSection = binding.streamsPinnedSection,
            pinnedHeader = binding.streamsPinnedHeader,
            pinnedChevron = binding.ivPinnedChevron,
            mainSection = binding.streamsMainSection,
            mainHeader = binding.streamsMainHeader,
            mainChevron = binding.ivMainChevron,
            pinnedGridMode = pinnedGridModeManager,
            mainGridMode = gridModeManager,
        )
    }

    private fun setupToolbarCommands() {
        binding.toolbar.setNavigationOnClickListener { exitStreamsWithAudioCheck() }
        onBackPressedDispatcher.addCallback(this) { exitStreamsWithAudioCheck() }
        // S1473: the menu is cleared and re-inflated on every orientation change, so this listener
        // must stay on the toolbar - a per-item listener would survive the first rotation as a
        // present but inert command.
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_stream_add -> {
                    showSourceDialog(isImport = false)
                    true
                }
                R.id.action_stream_import_catalog -> {
                    cancelHealthProbe()
                    viewModel.onImportCatalog()
                    true
                }
                R.id.action_stream_import_url -> {
                    cancelHealthProbe()
                    showSourceDialog(isImport = true)
                    true
                }
                R.id.action_stream_display_toggle -> {
                    cancelHealthProbe()
                    viewModel.onToggleDisplayMode()
                    true
                }
                R.id.action_stream_refresh -> {
                    startHealthProbe()
                    true
                }
                R.id.action_stream_clear_downloaded -> {
                    confirmClearDownloaded()
                    true
                }
                else -> false
            }
        }
        tintToolbarMenuIcons()
    }

    private fun setupSearchAndFilters() {
        binding.etSearch.doAfterTextChanged {
            cancelHealthProbe()
            viewModel.onQueryChanged(it?.toString().orEmpty())
        }
        binding.btnFilter.setOnClickListener {
            cancelHealthProbe()
            showFilterDialog()
        }
        binding.btnSort.setOnClickListener {
            cancelHealthProbe()
            showSortDialog()
        }

        // S0940: in landscape the search/filter/sort group moves into the toolbar header to free
        // vertical space for the list/grid; place it for the launch orientation here, then keep it
        // in sync from onConfigurationChanged (this window does not recreate on rotation, S0692).
        // S1473: the inline facet trigger writes through the single-facet ViewModel entry point, not
        // onFilter - that one defaults every unpassed facet and would clear the rest of the filter.
        mediaKindTrigger = StreamsMediaKindTriggerManager(
            videoButton = binding.btnMediaKindVideo,
            audioButton = binding.btnMediaKindAudio,
            onKindSelected = { kind ->
                cancelHealthProbe()
                viewModel.onMediaKindFilter(kind)
            },
        )
        mediaKindTrigger.bind()
    }

    private fun setupOrientationPlacement() {
        controlsPlacement = StreamsControlsPlacementManager(
            controls = binding.streamControls,
            headerHost = binding.headerControlsHost,
            searchField = binding.tilSearch,
            onOrientationApplied = { landscape ->
                mediaKindTrigger.render(latestState.filter.mediaKind, landscape)
            },
        )
        // S1473: the two pinned commands take text labels in landscape, which forces a menu rebuild
        // on every orientation change - see StreamsCommandLabelManager for why a resource variant
        // cannot do it here. Both post-inflate fixups have to be re-applied after each rebuild.
        commandLabels = StreamsCommandLabelManager(
            toolbar = binding.toolbar,
            menuRes = R.menu.menu_streams,
            labelledItemIds = listOf(R.id.action_stream_display_toggle, R.id.action_stream_import_catalog),
            reapplyFixups = {
                // Before the first state emission the display mode is unknown, so only the tint
                // applies; the state collector sets the toggle icon on that first emission.
                val mode = appliedDisplayMode
                if (mode != null) updateDisplayToggleIcon(mode) else tintToolbarMenuIcons()
            },
        )
        val launchLandscape =
            resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        controlsPlacement.applyForOrientation(launchLandscape)
        commandLabels.applyForOrientation(launchLandscape)
    }

    private fun setupEntryActions() {
        // S0700: a user drag of the list aborts an in-flight reachability sweep (programmatic scrolls,
        // e.g. the S0699 position restore, settle without DRAGGING so they do not cancel it).
        binding.rvStreams.addOnScrollListener(streamScrollListener)

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
            // S1154: load the atlas-preview coords on the same pass so a VIDEO tile can show its preview.
            atlasPreviewCoords = channelPreviewAtlasStore.coords()
            // S1201: and the logo coords, which are the only artwork a radio channel can get.
            logoAtlasCoords = streamLogoAtlasStore.coords()
            logStreamArtworkState()
            repaintArtworkRows()
        }
    }

    /**
     * S1503: repaint the rows the user can actually see, in both sections.
     *
     * The coords maps above are read by every adapter through closures over these fields, so freshly
     * loaded artwork is already reachable - what was missing is telling the adapters to rebind. Only
     * the main list adapter was ever told, so in grid mode, and in the pinned section in either mode,
     * logos and previews stayed absent until the display mode was toggled or the screen reopened.
     *
     * Addressed through the RecyclerViews rather than the four adapter fields on purpose: each section
     * has exactly one RecyclerView whose adapter [StreamGridModeManager] swaps on a mode change, so
     * this repaints whichever adapter is attached right now and cannot go stale when a fifth is added.
     */
    private fun repaintArtworkRows() {
        var reached = 0
        listOf(binding.rvStreams, binding.rvStreamsPinned).forEach { recycler ->
            recycler.adapter?.let { bound ->
                bound.notifyItemRangeChanged(0, bound.itemCount)
                reached++
            }
        }
    }

    /**
     * One line that answers "why is there no artwork?" from a device log alone: whether each atlas file
     * is on disk and how many channels each index actually covers. Without it, an empty map and a
     * missing file look the same from outside.
     */
    private fun logStreamArtworkState() {
        Timber.i(
            "Streams artwork: favicon=%b/%d, preview=%s/%d, logo=%s/%d (payload on disk / channels covered)",
            faviconAtlasStore.atlasFile() != null,
            faviconCoords.size,
            payloadKind(channelPreviewAtlasStore.tilePackFile(), channelPreviewAtlasStore.atlasFile()),
            atlasPreviewCoords.size,
            payloadKind(streamLogoAtlasStore.tilePackFile(), streamLogoAtlasStore.atlasFile()),
            logoAtlasCoords.size
        )
    }

    /**
     * Which container an artwork payload is being served from. A pack and a sheet render identical
     * pictures at wildly different speed, so a log that says only "installed" cannot explain a slow
     * grid on a user's device.
     */
    private fun payloadKind(pack: java.io.File?, sheet: java.io.File?): String = when {
        pack != null -> "pack"
        sheet != null -> "sheet"
        else -> "none"
    }

    /**
     * S1154: the channel-preview atlas just landed on disk (download finished, or it was installed from
     * the Extensions Manager while this screen was in the background). The url->index map is read once
     * at setup, so without this reload the freshly installed atlas would only start showing previews
     * after the screen is reopened.
     */
    private suspend fun reloadAtlasPreviews() {
        atlasSlicer.invalidate()
        atlasPreviewCoords = channelPreviewAtlasStore.coords()
        repaintArtworkRows()
    }

    /** S1201: same reload for the logo atlas - it is downloaded and installed independently. */
    private suspend fun reloadLogoTiles() {
        logoSlicer.invalidate()
        logoAtlasCoords = streamLogoAtlasStore.coords()
        repaintArtworkRows()
    }

    /**
     * S0668: a completed catalog import rewrote the atlas + coords sidecar - drop the cached atlas and
     * reload the coords so the new favicons appear without restarting the screen.
     */
    private fun onCatalogRefreshed() {
        lifecycleScope.launch {
            faviconSlicer.invalidate()
            faviconCoords = faviconAtlasStore.coords()
            // S1154: a re-import may have replaced the atlas payload too; drop the decoder and reload.
            atlasSlicer.invalidate()
            atlasPreviewCoords = channelPreviewAtlasStore.coords()
            logoSlicer.invalidate()
            logoAtlasCoords = streamLogoAtlasStore.coords()
            logStreamArtworkState()
            repaintArtworkRows()
        }
    }

    /**
     * MaterialToolbar does not tint menu icons, and ic_add/ic_refresh ship a white fill ("tinted at
     * usage site"). Without an explicit tint they render white-on-light and look missing (only the
     * pre-tinted ic_import shows). The toolbar now uses a colorPrimary background (app header color
     * scheme), so tint to colorOnPrimary for contrast. android:iconTint on a menu item is API 26+,
     * but legacy minSdk is 23, so apply via MenuItemCompat in code.
     */

    /**
     * S1780: asks before clearing, and says exactly what survives.
     *
     * The message names the hand-added channels as staying rather than only what goes: this is the one
     * destructive entry in the streams menu, and the user's real question before tapping it is whether
     * the channels they typed in themselves are about to disappear.
     */
    private fun confirmClearDownloaded() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.streams_clear_downloaded_confirm_title)
            .setMessage(R.string.streams_clear_downloaded_confirm_message)
            .setPositiveButton(R.string.streams_clear_downloaded) { _, _ ->
                viewModel.onClearDownloaded()
            }
            .setNegativeButton(R.string.cancel, null)
            // S1456: bound to the host, so a destroyed activity takes the dialog with it.
            .showBoundToHost(this)
    }

    private fun tintToolbarMenuIcons() {
        val tint = ColorStateList.valueOf(
            MaterialColors.getColor(binding.toolbar, com.google.android.material.R.attr.colorOnPrimary)
        )
        binding.toolbar.menu.forEach { item -> MenuItemCompat.setIconTintList(item, tint) }
        // S1473: the overflow glyph is a toolbar property, not a menu item, so the loop above never
        // reaches it - and it needs the same contrast as the icons beside it.
        binding.toolbar.overflowIcon?.mutate()?.let { icon ->
            DrawableCompat.setTintList(icon, tint)
            binding.toolbar.overflowIcon = icon
        }
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
            // S1141: the sections manager drives both section grid managers; a mode change swaps adapter
            // + layout once, otherwise it keeps both lists current. The pinned section auto-hides when
            // nothing is pinned. S1502: the halves arrive already split from the ViewModel.
            if (state.displayMode != appliedDisplayMode) {
                appliedDisplayMode = state.displayMode
                sectionsManager.applyMode(state.displayMode, state.pinned, state.unpinned)
            } else {
                sectionsManager.submitList(state.pinned, state.unpinned)
            }
            // S0587: recompute the main list's scroll-button visibility after the section split re-submits
            // (filter/sort/search change the unpinned row count). Scroll buttons target the main list only.
            if (state.displayMode == DisplayMode.LIST && ::scrollButtons.isInitialized) {
                binding.rvStreams.post { scrollButtons.updateVisibility() }
            }
            binding.emptyStateView.isVisible = state.isEmpty
            latestState = state
            updateFilterIndicator(state.filter)
            // S1473: renders from the shared filter state, so a change made in the filter dialog
            // repaints the inline icons on the same emission.
            mediaKindTrigger.render(state.filter.mediaKind)
            // S0699: once the list carries the saved row, land on it (once per screen open).
            tryRestoreScroll(state.sources.size)
        }
        // S1502: the status bullet travels beside the list, not inside it - a finished probe repaints
        // the affected rows only, instead of re-emitting the catalog through the filter/sort pass.
        collectOnLifecycle(viewModel.playOutcomes) { outcomes ->
            adapter.playOutcomes = outcomes
            pinnedAdapter.playOutcomes = outcomes
            gridAdapter.playOutcomes = outcomes
            pinnedGridAdapter.playOutcomes = outcomes
        }
        collectOnLifecycle(viewModel.events) { event ->
            when (event) {
                is StreamsViewModel.StreamsEvent.Message ->
                    Toast.makeText(this, event.messageResId, Toast.LENGTH_LONG).show()
                is StreamsViewModel.StreamsEvent.DownloadedCleared -> {
                    Toast.makeText(
                        this,
                        getString(R.string.streams_clear_downloaded_done, event.removed),
                        Toast.LENGTH_LONG,
                    ).show()
                }
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
                    // S1154/S1201/S1481: offer the preview atlas, then the logo atlas once the first
                    // offer is settled - answered, or with nothing to ask. Both payloads get offered on
                    // every catalog update; chaining on settlement is what keeps the two dialogs from
                    // stacking on top of each other.
                    streamAtlasPromptManager.maybeOffer(this) {
                        streamLogoPromptManager.maybeOffer(this)
                    }
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
     * triggers the same curated-catalog import the toolbar action uses.
     *
     * S1823: the offer is a banner, not a Snackbar. A Snackbar dismisses itself after a few seconds, and
     * for most installs this offer is the only path to a catalog at all: the owner's own device had never
     * imported one, and a scripted device run missed the Snackbar twice before catching it. The banner
     * stays until it is taken or closed, so the timeout can no longer decide whether a user ever gets a
     * channel list. Inflated on first use only (Rule 18) - the policy may never raise it.
     */
    private fun showCatalogRefreshSuggestion() {
        val banner = catalogBanner ?: binding.stubCatalogBanner.inflate().also { catalogBanner = it }
        banner.isVisible = true
        banner.findViewById<View>(R.id.btnCatalogBannerAction)?.setOnClickListener {
            banner.isVisible = false
            viewModel.onImportCatalog()
        }
        banner.findViewById<View>(R.id.btnCatalogBannerDismiss)?.setOnClickListener {
            banner.isVisible = false
        }
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
        if (latestState.displayMode == DisplayMode.GRID && ::sectionsManager.isInitialized) {
            // Grid: the frame capture both renders the thumbnail and reports the VIDEO tile's status, so only
            // AUDIO tiles need the lightweight surfaceless probe - avoids two decoders racing per video tile.
            // S1141: refresh both sections' visible tiles.
            gridModeManager.refreshVisibleFrames()
            pinnedGridModeManager.refreshVisibleFrames()
            healthProbe.start(visible.filter { it.mediaKind == "AUDIO" })
        } else {
            healthProbe.start(visible)
        }
    }

    /**
     * S0700/S1141: the rows/tiles currently on screen - the probe scope - across both sections. Each
     * RecyclerView's visible positions index into its own sublist (pinned rows vs unpinned rows); a
     * channel is pinned XOR unpinned, so concatenating the two visible sublists needs no dedup.
     */
    private fun visibleSources(): List<StreamSourceEntity> =
        visibleInSection(binding.rvStreamsPinned, latestState.pinned) +
            visibleInSection(binding.rvStreams, latestState.unpinned)

    private fun visibleInSection(
        recyclerView: RecyclerView,
        list: List<StreamSourceEntity>,
    ): List<StreamSourceEntity> {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return emptyList()
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        return if (first < 0 || last < 0) emptyList() else (first..last).mapNotNull { list.getOrNull(it) }
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

    /**
     * S0637: build a home-screen shortcut for the chosen channel; report if the launcher refuses.
     * S1067: resolve the channel's favicon tile from the atlas (same source as the list/grid icon) and
     * bake it into the shortcut so the pin shows the channel thumbnail, not a blank generic tile. The
     * tile decode is a background file read, so the whole flow runs on lifecycleScope; a missing
     * favicon yields null and the manager falls back to the generic media-kind vector.
     */
    private fun onAddShortcut(source: StreamSourceEntity) {
        lifecycleScope.launch {
            val iconTile = faviconCoords[source.url]?.let { faviconSlicer.tileFor(it) }
            // S1917: an accepted pin request is not a created shortcut - the system confirmation
            // dialog decides that next - so only the unsupported case is reported here.
            if (!StreamShortcutPinManager(this@StreamsActivity).requestPin(source, iconTile)) {
                Toast.makeText(
                    this@StreamsActivity,
                    R.string.streams_shortcut_unsupported,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun onPlay(source: StreamSourceEntity) {
        // S0700: selecting a stream aborts an in-flight reachability sweep.
        cancelHealthProbe()
        when {
            // S0690: tapping the row/tile that is already playing inline toggles it off (stop), so the
            // same gesture both starts and stops the inline radio without hunting for the mini-control.
            // Stopping needs no network, so this toggle runs before the S0711 reachability gate below.
            source.mediaKind == "AUDIO" && inlineAudio.playingId == source.id -> {
                inlineAudio.stop()
                // S1152: an explicit user stop clears the resume record so the next launch does not re-play it.
                clearStreamResume()
            }
            // S0711: starting any stream needs at least one active transport. Refuse fast (no spinner, no
            // connection-timeout) when fully offline. Covers tile taps and the playByUrl shortcut path,
            // both of which funnel through onPlay.
            !viewModel.hasNetworkForStream ->
                Toast.makeText(this, R.string.streams_error_no_network, Toast.LENGTH_SHORT).show()
            source.mediaKind == "AUDIO" ->
                inlineAudio.play(source, useBackgroundService = isBackgroundAudioEnabled())
            else -> launchFullscreenStream(source)
        }
    }

    private fun launchFullscreenStream(source: StreamSourceEntity) {
        // S1151: switching from inline radio to a video stream must fully stop the radio first. onStop
        // leaves ON-mode (background-service) playback alive by design, so without this the service player
        // keeps owning currentSource and the list keeps the animated "now playing" note next to the radio
        // row after the user exits the video player (audio focus had already silenced the sound). stop()
        // quiesces the player and fires onPlayingChanged(null), which clears the row indicator.
        inlineAudio.stop()
        // S1152: a video stream is never a resume candidate (owner decision 2026-07-26) - only radio,
        // which actually keeps playing, is worth reopening. Starting a video also ends whatever radio
        // record existed, since the radio was just stopped above.
        clearStreamResume()
        // VIDEO / RTSP: open the existing fullscreen player. The stream URL is carried as the initial
        // path against the synthetic single-item resource id; the player classifies the scheme to a
        // stream ResourceType and routes it to the stream playback helper (S0565 Phase 04).
        Timber.i("StreamsActivity: launching fullscreen stream - %s", source.url)
        streamPlayerLauncher.launch(
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

    /** S1152: record this radio station as the last active stream, so a cold start resumes it. */
    private fun persistStreamResume(source: StreamSourceEntity) {
        viewModel.persistStreamResume(source)
    }

    /** S1152: drop the resume record when the user explicitly stops the current stream. */
    private fun clearStreamResume() {
        viewModel.clearStreamResume()
    }

    /**
     * S1152: leaving this screen without anything still playing means there is nothing to resume -
     * drop the record so the next cold start opens the normal main screen.
     */
    private fun clearStreamResumeOnExit() {
        if (!::inlineAudio.isInitialized) return
        if (keepBackgroundService || inlineAudio.isServiceAudioActive) return
        viewModel.clearStreamResumeOnExit()
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
                onStopThisTime = {
                    inlineAudio.stop()
                    finish()
                },
                onContinueThisTime = {
                    keepBackgroundService = true
                    finish()
                },
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
     *
     * S1509: removal is offered only when the device had a network - otherwise the user's own dead
     * link, not the channel, is what failed, and the dialog says so instead of proposing a deletion.
     */
    private fun showStreamUnavailable(source: StreamSourceEntity) {
        // S1509: one connectivity sample drives both the recorded outcome and the dialog, so the row
        // bullet and the buttons can never disagree about what just happened.
        val hasNetwork = viewModel.hasNetworkForStream
        // S0593/S1509: the inline audio attempt failed -> red when the channel is to blame, amber when
        // the network was down.
        viewModel.recordStreamPlayFailure(source.id, hasNetwork)
        StreamUnavailableDialog.show(
            activity = this,
            channelTitle = source.title,
            offline = !hasNetwork,
            onRetry = { inlineAudio.play(source, useBackgroundService = isBackgroundAudioEnabled()) },
            onRemove = { viewModel.onRemove(source) },
            onDismiss = {},
        )
    }

    private fun confirmRemove(source: StreamSourceEntity) {
        // S1424: the dialog itself moved to a shared object so the launcher desktop asks the same
        // question instead of a copy of it.
        StreamRemoveConfirmation.show(this, source.title) { viewModel.onRemove(source) }
    }

    private fun showSourceDialog(isImport: Boolean) {
        val dialogBinding = DialogAddStreamBinding.inflate(layoutInflater)
        dialogBinding.tilUrl.hint = getString(
            if (isImport) R.string.streams_import_url_hint else R.string.streams_add_url_hint
        )
        dialogBinding.tilTitle.isVisible = !isImport
        // S1144: an import pulls in a whole playlist, so there is no single channel to attach a track
        // preference to - the rows are shown for a manual add only.
        dialogBinding.trackPreferenceContainer.isVisible = !isImport
        val writeTrackPreference = StreamEditDialog.bindTrackPreference(
            activity = this,
            binding = dialogBinding,
            existing = null,
            write = viewModel::writeTrackPreference,
        )
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (isImport) R.string.streams_import else R.string.streams_add)
            .setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = dialogBinding.etUrl.text?.toString().orEmpty().trim()
                if (isImport) {
                    viewModel.onImport(url)
                } else {
                    viewModel.onAdd(url, dialogBinding.etTitle.text?.toString())
                    if (url.isNotEmpty()) writeTrackPreference(url)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        DialogKeyboardDelegate.applyTo(dialog) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
        }
        dialog.showBoundToHost(this@StreamsActivity)
        dialogBinding.etUrl.requestFocus()
    }

    private fun showEditDialog(source: StreamSourceEntity) = StreamEditDialog.show(
        activity = this,
        source = source,
        callbacks = StreamEditDialog.Callbacks(
            readTrackPreference = viewModel::readTrackPreference,
            writeTrackPreference = viewModel::writeTrackPreference,
            onEdit = { edited, url, title, kindOverride ->
                viewModel.onEdit(edited, url, title, kindOverride)
            },
        ),
    )

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
     *
     * S1473: the command row now offers both sources as direct overflow entries, so this chooser is
     * reachable only from the empty-state button, whose behaviour the owner did not ask to change.
     * It is not dead weight, and the toolbar must not be pointed back at it.
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
        dialog.showBoundToHost(this@StreamsActivity)
    }

    /**
     * Opens the category + language + country + media-kind filter dialog; the manager marshals
     * selections to the ViewModel.
     */
    private fun showFilterDialog() {
        filterDialogManager.show(latestState) { category, topic, language, country, mediaKind, pinnedOnly ->
            viewModel.onFilter(
                category = category,
                topic = topic,
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
            filter.topic != null ||
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
        val target = pendingScrollTarget
        if (scrollRestored || target == null || itemCount <= 0) return
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
        dialog.showBoundToHost(this@StreamsActivity)
    }

    private fun sortLabel(mode: StreamsViewModel.SortMode): Int = when (mode) {
        StreamsViewModel.SortMode.NAME -> R.string.streams_sort_name
        StreamsViewModel.SortMode.TOPIC -> R.string.streams_sort_topic
        StreamsViewModel.SortMode.LANGUAGE -> R.string.streams_sort_language
        StreamsViewModel.SortMode.COUNTRY -> R.string.streams_sort_country
        StreamsViewModel.SortMode.RECENT -> R.string.streams_sort_recent
    }

    /**
     * S0577: background streaming uses the foreground service only when the user enabled it and
     * the flavor supports it.
     */
    private fun isBackgroundAudioEnabled(): Boolean =
        viewModel.settings.value.enablePersistentAudioPlayback &&
            capabilityAvailability.isPersistentAudioPlaybackAvailable()

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // S0692: this Activity handles orientation config changes itself (manifest configChanges), so the
        // list/grid column span must be recomputed here rather than via an Activity recreate.
        if (::sectionsManager.isInitialized) sectionsManager.onConfigurationChanged()
        // S0940: relocate the search/filter/sort group between header (landscape) and the below-toolbar
        // bar (portrait) live on rotation, since the window is not recreated here.
        if (::controlsPlacement.isInitialized) {
            val landscape = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            controlsPlacement.applyForOrientation(landscape)
            // S1473: the menu item views carry their text permission from birth, so the labels
            // follow the rotation only if the menu is rebuilt with it.
            commandLabels.applyForOrientation(landscape)
        }
        applyOrientationDimensions()
    }

    /**
     * S1549: the landscape arrangement of the list padding, the mini control height and the mini title's
     * line count used to live in a landscape layout that never inflated, because this screen absorbs the
     * configuration change to keep inline stream audio alive. Resources already resolve to the new
     * orientation by the time this runs, so re-reading them here produces what the dead layout described.
     */
    private fun applyOrientationDimensions() {
        val sidePadding = resources.getDimensionPixelSize(R.dimen.streams_list_side_padding)
        listOf(binding.rvStreams, binding.rvStreamsPinned).forEach { list ->
            list.setPaddingRelative(sidePadding, list.paddingTop, sidePadding, list.paddingBottom)
        }
        binding.streamMiniControl.minimumHeight =
            resources.getDimensionPixelSize(R.dimen.streams_mini_control_min_height)
        binding.tvMiniTitle.maxLines = resources.getInteger(R.integer.streams_mini_title_max_lines)
    }

    override fun onStart() {
        super.onStart()
        // S1154: the atlas may have been installed from the Extensions Manager while this screen sat in
        // the background - pick it up on return instead of waiting for the next catalog import.
        // S1445: either container counts as installed - a fresh install carries the pack and no sheet.
        val previewInstalled = channelPreviewAtlasStore.tilePackFile() != null ||
            channelPreviewAtlasStore.atlasFile() != null
        if (atlasPreviewCoords.isEmpty() && previewInstalled) {
            lifecycleScope.launch { reloadAtlasPreviews() }
        }
        // S1201: same for the logo atlas - the two payloads install independently.
        val logoInstalled = streamLogoAtlasStore.tilePackFile() != null ||
            streamLogoAtlasStore.atlasFile() != null
        if (logoAtlasCoords.isEmpty() && logoInstalled) {
            lifecycleScope.launch { reloadLogoTiles() }
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
        // S0675/S1141: never run frame captures while backgrounded - stop both sections' snapshot engines
        // + timers. sectionsManager.stop() forwards to each grid manager, and each grid manager's stop()
        // cancels its own snapshot engine.
        if (::sectionsManager.isInitialized) {
            sectionsManager.stop()
        }
        super.onStop()
    }

    override fun onDestroy() {
        binding.rvStreams.removeOnScrollListener(streamScrollListener)
        // S1474: before the engines below are released, so a measurement never outlives its window.
        streamInfoDialogManager.dismiss()
        // S1152: read the playback state BEFORE inlineAudio is released below.
        if (isFinishing) clearStreamResumeOnExit()
        // setupViews() is deferred to a post{}; guard against destroy before it ran.
        if (::inlineAudio.isInitialized) {
            // S0577: on a background-continue exit, detach without stopping the service stream.
            if (keepBackgroundService) {
                inlineAudio.releaseKeepingBackgroundService()
            } else {
                inlineAudio.release()
            }
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY_STREAM = "com.sza.fastmediasorter.action.PLAY_STREAM"
        const val EXTRA_STREAM_URL = "extra_stream_url"

        /**
         * S1471: no longer what a pinned shortcut carries. The shortcut now targets
         * `StreamPlayLaunchActivity`, and this is the fallback that trampoline starts when background
         * playback does not apply - non-audio channel, setting off, no network, or an unknown URL.
         * Keeps the launcher task flags because the trampoline starts it from outside our task.
         */
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
