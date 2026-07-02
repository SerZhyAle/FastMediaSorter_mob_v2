package com.sza.fastmediasorter.core

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.player.AudioPlaybackService
import timber.log.Timber

/**
 * Quick Settings tile for audio play/pause toggle.
 * - Playing → shows pause icon, tap pauses
 * - Paused → shows play icon, tap resumes
 * - No session → shows "Play Music", tap launches random music via MainActivity
 */
class AudioToggleTileService : TileService() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    override fun onStartListening() {
        super.onStartListening()
        if (AudioPlaybackService.isRunning) {
            connectToSession()
        } else {
            updateTile(isActive = false, isPlaying = false)
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        releaseController()
    }

    override fun onClick() {
        super.onClick()
        val controller = mediaController
        when {
            controller != null && controller.isConnected -> {
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    controller.play()
                }
            }
            else -> {
                val intent = Intent(this, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_RANDOM_MUSIC
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pendingIntent = PendingIntent.getActivity(
                        this, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    startActivityAndCollapse(pendingIntent)
                } else {
                    @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
                    startActivityAndCollapse(intent)
                }
            }
        }
    }

    private fun connectToSession() {
        if (mediaController?.isConnected == true) return
        if (controllerFuture != null) return

        val token = SessionToken(
            this,
            ComponentName(this, AudioPlaybackService::class.java)
        )
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future

        future.addListener({
            try {
                if (future !== controllerFuture) {
                    try {
                        future.get().release()
                    } catch (ignored: Exception) {
                        // ignore release failures of obsolete controllers
                    }
                    return@addListener
                }
                val controller = future.get()
                mediaController = controller
                controller.addListener(tilePlayerListener)
                val playing = controller.isPlaying
                updateTile(isActive = true, isPlaying = playing)
            } catch (e: Exception) {
                Timber.w(e, "TileService: failed to connect to MediaSession")
                updateTile(isActive = false, isPlaying = false)
            } finally {
                if (future === controllerFuture) {
                    controllerFuture = null
                }
            }
        }, MoreExecutors.directExecutor())
    }

    private val tilePlayerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateTile(isActive = true, isPlaying = isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                updateTile(isActive = false, isPlaying = false)
            }
        }
    }

    private fun updateTile(isActive: Boolean, isPlaying: Boolean) {
        val tile = qsTile ?: return
        when {
            isActive && isPlaying -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = getString(R.string.tile_audio_pause)
                tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_pause)
            }
            isActive -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.tile_audio_play)
                tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_play)
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.tile_audio_play_music)
                tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_play)
            }
        }
        tile.updateTile()
    }

    private fun releaseController() {
        mediaController?.removeListener(tilePlayerListener)
        mediaController?.release()
        mediaController = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    override fun onDestroy() {
        releaseController()
        super.onDestroy()
    }
}
