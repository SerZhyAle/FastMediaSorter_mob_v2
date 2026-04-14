package com.sza.fastmediasorter.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.player.helpers.QueueTrackAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Bottom sheet showing Now Playing info (Panel A) and track queue (Panel B).
 *
 * Both panels live inside this single fragment; they are toggled by visibility,
 * avoiding fragment transactions and back-stack complexity.
 *
 * Launched by NowPlayingManager when the user taps the mini Now Playing bar.
 */
@AndroidEntryPoint
class NowPlayingBottomSheetFragment : BottomSheetDialogFragment() {

    private val viewModel: NowPlayingViewModel by viewModels()

    // Panel A views
    private lateinit var panelNowPlaying: View
    private lateinit var progressLoading: android.widget.ProgressBar
    private lateinit var artwork: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var tvPosition: TextView
    private lateinit var tvDuration: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPrevious: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnShowQueue: MaterialButton

    // Panel B views
    private lateinit var panelQueue: View
    private lateinit var recyclerQueue: RecyclerView
    private lateinit var btnShowNowPlaying: MaterialButton

    private lateinit var queueAdapter: QueueTrackAdapter

    private var isSeeking = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_now_playing, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupQueueRecycler()
        setupClickListeners()
        setupSeekBar()
        observeState()
        viewModel.connect()
    }

    override fun onDestroyView() {
        viewModel.disconnect()
        super.onDestroyView()
    }

    // ── View binding ──────────────────────────────────────────────────────────

    private fun bindViews(root: View) {
        panelNowPlaying = root.findViewById(R.id.panelNowPlaying)
        progressLoading = root.findViewById(R.id.progressNowPlayingLoading)
        artwork = root.findViewById(R.id.nowPlayingArtwork)
        tvTitle = root.findViewById(R.id.nowPlayingTitle)
        tvArtist = root.findViewById(R.id.nowPlayingArtist)
        seekBar = root.findViewById(R.id.nowPlayingSeekBar)
        tvPosition = root.findViewById(R.id.nowPlayingPosition)
        tvDuration = root.findViewById(R.id.nowPlayingDuration)
        btnPlayPause = root.findViewById(R.id.btnPlayPause)
        btnPrevious = root.findViewById(R.id.btnPrevious)
        btnNext = root.findViewById(R.id.btnNext)
        btnShowQueue = root.findViewById(R.id.btnShowQueue)

        panelQueue = root.findViewById(R.id.panelQueue)
        recyclerQueue = root.findViewById(R.id.recyclerQueue)
        btnShowNowPlaying = root.findViewById(R.id.btnShowNowPlaying)
    }

    private fun setupQueueRecycler() {
        queueAdapter = QueueTrackAdapter { index ->
            viewModel.jumpToQueueItem(index)
            showNowPlayingPanel()
        }
        recyclerQueue.adapter = queueAdapter
        recyclerQueue.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupClickListeners() {
        btnPlayPause.setOnClickListener { viewModel.togglePlayPause() }
        btnPrevious.setOnClickListener { viewModel.seekToPrevious() }
        btnNext.setOnClickListener { viewModel.seekToNext() }
        btnShowQueue.setOnClickListener { showQueuePanel() }
        btnShowNowPlaying.setOnClickListener { showNowPlayingPanel() }
    }

    private fun setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(sb: SeekBar) { isSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar) {
                isSeeking = false
                val state = viewModel.state.value
                if (state.durationMs > 0) {
                    val targetMs = (sb.progress.toLong() * state.durationMs) / sb.max
                    viewModel.seekTo(targetMs)
                }
            }
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {}
        })
    }

    // ── State observation ─────────────────────────────────────────────────────

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    Timber.d("NowPlayingBottomSheetFragment: state updated isPlaying=${state.isPlaying}")
                    updateNowPlayingPanel(state)
                    updateQueuePanel(state)
                }
            }
        }
    }

    private fun updateNowPlayingPanel(state: NowPlayingViewModel.NowPlayingState) {
        // Show spinner while MediaController is connecting; hide content until ready
        progressLoading.isVisible = state.isLoading
        artwork.isVisible = !state.isLoading
        tvTitle.isVisible = !state.isLoading
        tvArtist.isVisible = !state.isLoading && !state.artist.isNullOrEmpty()
        seekBar.isVisible = !state.isLoading
        tvPosition.isVisible = !state.isLoading
        tvDuration.isVisible = !state.isLoading
        btnPlayPause.isVisible = !state.isLoading
        btnPrevious.isVisible = !state.isLoading
        btnNext.isVisible = !state.isLoading
        btnShowQueue.isVisible = !state.isLoading
        if (state.isLoading) return

        tvTitle.text = state.title.ifEmpty { getString(R.string.now_playing_label) }
        tvArtist.isVisible = !state.artist.isNullOrEmpty()
        tvArtist.text = state.artist ?: ""

        if (state.albumArtUri != null) {
            Glide.with(this)
                .load(state.albumArtUri)
                .placeholder(R.drawable.ic_music_note)
                .error(R.drawable.ic_music_note)
                .into(artwork)
        } else {
            artwork.setImageResource(R.drawable.ic_music_note)
        }

        btnPlayPause.setImageResource(
            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        btnPlayPause.contentDescription = getString(
            if (state.isPlaying) R.string.now_playing_pause_desc else R.string.now_playing_play_desc
        )

        tvDuration.text = formatMs(state.durationMs)
        if (!isSeeking) {
            tvPosition.text = formatMs(state.positionMs)
            if (state.durationMs > 0) {
                seekBar.progress = ((state.positionMs.toFloat() / state.durationMs) * seekBar.max).toInt()
            } else {
                seekBar.progress = 0
            }
        }

        val queueCount = state.queueItems.size
        btnShowQueue.text = if (queueCount > 0) {
            getString(R.string.now_playing_track_count, queueCount)
        } else {
            getString(R.string.now_playing_queue_label)
        }
    }

    private fun updateQueuePanel(state: NowPlayingViewModel.NowPlayingState) {
        queueAdapter.submitList(state.queueItems)
        queueAdapter.setCurrentIndex(state.currentQueueIndex)
    }

    // ── Panel switching ───────────────────────────────────────────────────────

    private fun showQueuePanel() {
        panelNowPlaying.isVisible = false
        panelQueue.isVisible = true
    }

    private fun showNowPlayingPanel() {
        panelQueue.isVisible = false
        panelNowPlaying.isVisible = true
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun formatMs(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        return "%d:%02d".format(minutes, seconds)
    }

    companion object {
        const val TAG = "NowPlayingBottomSheet"

        fun newInstance(): NowPlayingBottomSheetFragment = NowPlayingBottomSheetFragment()
    }
}
