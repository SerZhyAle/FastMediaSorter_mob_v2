package com.sza.fastmediasorter.ui.streams

import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.databinding.ActivityStreamsBinding
import com.sza.fastmediasorter.databinding.DialogAddStreamBinding
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.helpers.AudioServiceController
import com.sza.fastmediasorter.ui.streams.helpers.StreamInlineAudioManager
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

    /** Last rendered state, kept so the filter dialog can populate its facet choices on demand. */
    private var latestState = StreamsViewModel.StreamsUiState()

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
        )

        binding.rvStreams.layoutManager = LinearLayoutManager(this)
        binding.rvStreams.adapter = adapter

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_stream_add -> { showSourceDialog(isImport = false); true }
                R.id.action_stream_import -> { showImportChooser(); true }
                R.id.action_stream_refresh -> { binding.rvStreams.scrollToPosition(0); true }
                else -> false
            }
        }

        binding.etSearch.doAfterTextChanged { viewModel.onQueryChanged(it?.toString().orEmpty()) }
        binding.btnFilter.setOnClickListener { showFilterDialog() }
        binding.btnSort.setOnClickListener { showSortDialog() }
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.state) { state ->
            adapter.submitList(state.sources)
            binding.tvEmpty.isVisible = state.isEmpty
            latestState = state
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
            inlineAudio.play(source)
            return
        }
        // VIDEO / RTSP: open the existing fullscreen player. The stream URL is carried as the initial
        // path against the synthetic single-item resource id; the player classifies the scheme to a
        // stream ResourceType and routes it to the stream playback helper (S0565 Phase 04).
        Timber.i("StreamsActivity: launching fullscreen stream - %s", source.url)
        startActivity(
            PlayerActivity.createIntent(
                context = this,
                resourceId = SYNTHETIC_STREAM_RESOURCE_ID,
                initialFilePath = source.url,
                skipAvailabilityCheck = true,
            )
        )
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

    /**
     * Single-facet filter picker. Offers the distinct topic values present in the catalog plus an
     * "All" reset; topic is the primary facet surfaced here (category/language stay available in the
     * ViewModel API for later controls). The chosen value is forwarded to the ViewModel.
     */
    private fun showFilterDialog() {
        val topics = latestState.facets.topics
        val items = (listOf(getString(R.string.streams_filter_all)) + topics).toTypedArray()
        val current = latestState.filter.topic
        val checked = if (current == null) 0 else topics.indexOf(current) + 1
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.streams_filter)
            .setSingleChoiceItems(items, checked.coerceAtLeast(0)) { dialog, which ->
                viewModel.onFilter(topic = if (which == 0) null else topics[which - 1])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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

    override fun onDestroy() {
        // setupViews() is deferred to a post{}; guard against destroy before it ran.
        if (::inlineAudio.isInitialized) inlineAudio.release()
        super.onDestroy()
    }

    private companion object {
        // Matches the synthetic single-item resource branch in PlayerMediaFilesLoader (no DB resource).
        const val SYNTHETIC_STREAM_RESOURCE_ID = -100L
    }
}
