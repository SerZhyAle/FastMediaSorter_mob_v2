package com.sza.fastmediasorter.ui.streams

import android.content.res.ColorStateList
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.MenuItemCompat
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.color.MaterialColors
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.databinding.ActivityStreamsBinding
import com.sza.fastmediasorter.databinding.DialogAddStreamBinding
import com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior
import com.sza.fastmediasorter.domain.model.SyntheticResourceIds
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.helpers.AudioExitAction
import com.sza.fastmediasorter.ui.player.helpers.AudioExitBehaviorResolver
import com.sza.fastmediasorter.ui.player.helpers.AudioServiceController
import com.sza.fastmediasorter.ui.player.helpers.BackgroundAudioExitDialog
import com.sza.fastmediasorter.ui.streams.helpers.StreamInlineAudioManager
import com.sza.fastmediasorter.ui.streams.helpers.StreamScrollButtonManager
import com.sza.fastmediasorter.ui.streams.helpers.StreamsFilterDialogManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

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

    private val adapter = StreamSourceAdapter(
        onPlay = ::onPlay,
        onPin = { viewModel.onPin(it.id) },
        onRemove = ::confirmRemove,
    )

    private lateinit var inlineAudio: StreamInlineAudioManager

    // S0587: file-browser-style scroll-navigation buttons (top / page-up / page-down / bottom).
    private lateinit var scrollButtons: StreamScrollButtonManager

    /** Last rendered state, kept so the filter dialog can populate its facet choices on demand. */
    private var latestState = StreamsViewModel.StreamsUiState()

    private val filterDialogManager by lazy { StreamsFilterDialogManager(this) }

    /** S0577: set when the user chose to keep a background stream playing on exit (skip teardown). */
    private var keepBackgroundService = false

    override fun getViewBinding(): ActivityStreamsBinding =
        ActivityStreamsBinding.inflate(layoutInflater)

    override fun getMouseScrollTargetView(): View = binding.rvStreams

    override fun getInitialFocusView(): View = binding.toolbar

    override fun setupViews() {
        Timber.d("S0565: Streams screen opened")

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

        binding.toolbar.setNavigationOnClickListener { exitStreamsWithAudioCheck() }
        onBackPressedDispatcher.addCallback(this) { exitStreamsWithAudioCheck() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_stream_add -> { showSourceDialog(isImport = false); true }
                R.id.action_stream_import -> { showImportChooser(); true }
                R.id.action_stream_refresh -> { binding.rvStreams.scrollToPosition(0); true }
                else -> false
            }
        }
        tintToolbarMenuIcons()

        binding.etSearch.doAfterTextChanged { viewModel.onQueryChanged(it?.toString().orEmpty()) }
        binding.btnFilter.setOnClickListener { showFilterDialog() }
        binding.btnSort.setOnClickListener { showSortDialog() }
    }

    /**
     * MaterialToolbar does not tint menu icons, and ic_add/ic_refresh ship a white fill ("tinted at
     * usage site"). Without an explicit tint they render white-on-light and look missing (only the
     * pre-tinted ic_import shows). The toolbar now uses a colorPrimary background (app header color
     * scheme), so tint to colorOnPrimary for contrast. android:iconTint on a menu item is API 26+,
     * but legacy minSdk is 23, so apply via MenuItemCompat in code.
     */
    private fun tintToolbarMenuIcons() {
        Timber.d("S0586: tinting streams toolbar menu icons")
        val tint = ColorStateList.valueOf(
            MaterialColors.getColor(binding.toolbar, com.google.android.material.R.attr.colorOnPrimary)
        )
        binding.toolbar.menu.forEach { item -> MenuItemCompat.setIconTintList(item, tint) }
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.state) { state ->
            adapter.submitList(state.sources) {
                // S0587: recompute scroll-button visibility once the new list is laid out
                // (filter/sort/search change the row count).
                if (::scrollButtons.isInitialized) scrollButtons.updateVisibility()
            }
            binding.tvEmpty.isVisible = state.isEmpty
            latestState = state
            updateFilterIndicator(state.filter)
        }
        collectOnLifecycle(viewModel.events) { event ->
            when (event) {
                is StreamsViewModel.StreamsEvent.Message ->
                    Toast.makeText(this, event.messageResId, Toast.LENGTH_LONG).show()
                is StreamsViewModel.StreamsEvent.ImportFinished -> {
                    Timber.d("S0565: m3u import done inserted=%d", event.inserted)
                    Toast.makeText(
                        this,
                        getString(R.string.streams_import_done, event.inserted),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                is StreamsViewModel.StreamsEvent.CatalogUpdated ->
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
        }
    }

    private fun onPlay(source: StreamSourceEntity) {
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
            )
        )
    }

    /**
     * S0577: leave the streams screen honoring the background-audio exit preference. Mirrors
     * PlayerLifecycleManager.exitPlayerWithAudioCheck via the shared resolver + dialog. Only ON-mode
     * (service) playback can continue in the background; OFF-mode audio is already torn down by onStop.
     */
    private fun exitStreamsWithAudioCheck() {
        Timber.d("S0577: streams exit audio check serviceActive=%b", inlineAudio.isServiceAudioActive)
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
        Timber.d("S0581: inline stream unavailable dialog for %s", source.url)
        // S0593: the inline audio attempt failed -> record the red status for this source.
        Timber.d("S0593: inline audio failed - record FAIL %s", source.url)
        viewModel.recordStreamOutcome(source.id, ok = false)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.streams_unavailable_title)
            .setMessage(getString(R.string.streams_unavailable_message, source.title))
            .setPositiveButton(R.string.retry) { _, _ ->
                inlineAudio.play(source, useBackgroundService = isBackgroundAudioEnabled())
            }
            .setNeutralButton(R.string.streams_remove) { _, _ -> viewModel.onRemove(source) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmRemove(source: StreamSourceEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.streams_remove)
            .setMessage(source.title)
            .setPositiveButton(R.string.streams_remove) { _, _ -> viewModel.onRemove(source) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSourceDialog(isImport: Boolean) {
        val dialogBinding = DialogAddStreamBinding.inflate(layoutInflater)
        dialogBinding.tilUrl.hint = getString(
            if (isImport) R.string.streams_import_url_hint else R.string.streams_add_url_hint
        )
        dialogBinding.tilTitle.isVisible = !isImport
        MaterialAlertDialogBuilder(this)
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
            .show()
    }

    /**
     * Two-way "Import list" chooser: one-tap curated-catalog update vs. the existing manual URL
     * import dialog. The Activity only forwards the choice - the catalog download lives in the
     * ViewModel/use case (Rule 3).
     */
    private fun showImportChooser() {
        Timber.d("S0570: import chooser opened")
        val items = arrayOf(
            getString(R.string.streams_import_catalog),
            getString(R.string.streams_import_from_url),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.streams_import_choose_title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> viewModel.onImportCatalog()
                    1 -> showSourceDialog(isImport = true)
                }
            }
            .show()
    }

    /** Opens the category + language + media-kind filter dialog; the manager marshals selections to the ViewModel. */
    private fun showFilterDialog() {
        filterDialogManager.show(latestState) { category, language, mediaKind ->
            viewModel.onFilter(category = category, language = language, mediaKind = mediaKind)
        }
    }

    /** Marks an active filter on the filter button by a dot glyph (shape, not color alone). */
    private fun updateFilterIndicator(filter: StreamsViewModel.StreamsFilter) {
        val active = filter.category != null ||
            filter.language != null ||
            filter.mediaKind != StreamsViewModel.MediaKindFilter.ALL
        binding.btnFilter.setImageResource(if (active) R.drawable.ic_tune_active else R.drawable.ic_tune)
        binding.btnFilter.contentDescription =
            getString(if (active) R.string.streams_filter_active else R.string.streams_filter)
    }

    /** Sort-mode picker mapping each label to a [StreamsViewModel.SortMode]. */
    private fun showSortDialog() {
        val modes = StreamsViewModel.SortMode.entries.toTypedArray()
        val labels = modes.map { getString(sortLabel(it)) }.toTypedArray()
        val checked = modes.indexOf(latestState.filter.sort).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.streams_sort)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                viewModel.onSort(modes[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun sortLabel(mode: StreamsViewModel.SortMode): Int = when (mode) {
        StreamsViewModel.SortMode.NAME -> R.string.streams_sort_name
        StreamsViewModel.SortMode.TOPIC -> R.string.streams_sort_topic
        StreamsViewModel.SortMode.LANGUAGE -> R.string.streams_sort_language
        StreamsViewModel.SortMode.RECENT -> R.string.streams_sort_recent
    }

    /** S0577: background streaming uses the foreground service only when the user enabled it and the flavor supports it. */
    private fun isBackgroundAudioEnabled(): Boolean =
        viewModel.settings.value.enablePersistentAudioPlayback && BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK

    override fun onStop() {
        // S0577: OFF-mode (in-app) stream audio must not survive the screen going to background -
        // mirrors local audio. Service-mode (ON) playback is owned by AudioPlaybackService and left alone.
        if (::inlineAudio.isInitialized && inlineAudio.isLocalPlaybackActive) {
            inlineAudio.stop()
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
}
