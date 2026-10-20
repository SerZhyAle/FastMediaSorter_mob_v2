package com.sza.fastmediasorter.ui.player.helpers

import android.text.format.DateUtils
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

/** A range belongs to one source and one active player, and never outlives its visible host. */
class PlayerPlaybackRangeManager(
    private val host: FragmentActivity,
    private val getPlayer: () -> Player?,
    private val getSource: () -> String?,
    private val sourceChanges: Flow<String?>,
) : DefaultLifecycleObserver {
    private var player: ExoPlayer? = null
    private var source: String? = null
    private var startMs: Long? = null
    private var endMs: Long? = null
    private var boundary: PlayerMessage? = null
    private var status: Snackbar? = null
    private val transitionListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = clear()
    }

    init {
        host.lifecycle.addObserver(this)
        host.lifecycleScope.launch {
            host.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sourceChanges.distinctUntilChanged().collect { key ->
                    if (source != null && key != source) clear()
                }
            }
        }
    }

    fun toggle(): Boolean {
        Timber.d("S4373: A-B loop toggled")
        val active = getPlayer() as? ExoPlayer
        val key = getSource()
        if (active == null || key == null || !active.isCurrentMediaItemSeekable) {
            Toast.makeText(host, R.string.player_loop_unavailable, Toast.LENGTH_SHORT).show()
        } else {
            if (player !== active || source != key) {
                clear()
                player = active
                source = key
                active.addListener(transitionListener)
            }
            advance(active)
        }
        return true
    }

    private fun advance(active: ExoPlayer) {
        val start = startMs
        when {
            start == null -> {
                startMs = active.currentPosition
                showStatus(host.getString(R.string.player_loop_start, time(active.currentPosition)))
            }
            endMs != null -> clear()
            active.currentPosition <= start -> {
                Toast.makeText(host, R.string.player_loop_invalid_end, Toast.LENGTH_SHORT).show()
            }
            else -> activate(active, start, active.currentPosition)
        }
    }

    private fun activate(active: ExoPlayer, start: Long, end: Long) {
        endMs = end
        val sourceAtCreation = source
        boundary = active.createMessage { _, _ ->
            host.runOnUiThread {
                val sameSource = getSource() == sourceAtCreation && endMs == end
                if (player === active && getPlayer() === active && sameSource) {
                    active.seekTo(start)
                }
            }
        }.setPosition(end).setDeleteAfterDelivery(false).send()
        active.seekTo(start)
        showStatus(host.getString(R.string.player_loop_active, time(start), time(end)))
    }

    private fun showStatus(text: String) {
        status?.dismiss()
        status = Snackbar.make(host.window.decorView, text, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.cancel) { clear() }
            .also { it.show() }
    }

    private fun time(positionMs: Long): String = String.format(
        Locale.ROOT,
        "%s.%03d",
        DateUtils.formatElapsedTime(positionMs / MILLIS_PER_SECOND),
        positionMs % MILLIS_PER_SECOND,
    )

    private fun clear() {
        boundary?.cancel()
        boundary = null
        player?.removeListener(transitionListener)
        player = null
        source = null
        startMs = null
        endMs = null
        status?.dismiss()
        status = null
    }

    override fun onStop(owner: LifecycleOwner) = clear()

    override fun onDestroy(owner: LifecycleOwner) {
        clear()
        owner.lifecycle.removeObserver(this)
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
