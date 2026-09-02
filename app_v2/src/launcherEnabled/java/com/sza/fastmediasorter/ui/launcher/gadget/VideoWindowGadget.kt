package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherMediaVideoWindowBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import com.sza.fastmediasorter.domain.usecase.launcher.LoadLauncherGadgetFilesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import javax.inject.Inject

/**
 * S1754: the resource's newest video, running inside its own desktop cell.
 *
 * Muted and looping, because a home screen is not a place that may start making noise on its own - the
 * user asked for the picture in the window, and the sound belongs to the player one tap away.
 */
class VideoWindowGadget @Inject constructor(
    private val loadFiles: LoadLauncherGadgetFilesUseCase,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_MEDIA_VIDEO_WINDOW
    override val defaultSpanW: Int = MediaWindow.SPAN
    override val defaultSpanH: Int = MediaWindow.SPAN
    override val labelRes: Int = R.string.launcher_gadget_media_video_window
    override val iconRes: Int = R.drawable.ic_video
    override val requiresResourceParam: Boolean = true

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        VideoWindowGadgetView(container.context, host, param?.toLongOrNull(), loadFiles)
}

private class VideoWindowGadgetView(
    context: Context,
    private val host: LauncherGadgetHost,
    private val resourceId: Long?,
    private val loadFiles: LoadLauncherGadgetFilesUseCase,
) : LauncherGadgetView(context) {

    private val binding =
        GadgetLauncherMediaVideoWindowBinding.inflate(LayoutInflater.from(context), this)

    /**
     * Created on attach and released on detach, never held past the cell.
     *
     * A desktop rebuild drops and rebuilds every cell view with no recycler callback to hook (ADR-9), so
     * detach is the one honest place a codec can be handed back. A player kept here across rebinds would
     * leak one decoder per rebuild - and a device has very few.
     */
    private var player: ExoPlayer? = null

    init {
        binding.mediaVideoWindowTitle.setOnClickListener {
            resourceId?.let { host.run(LauncherCellCommand.Resource(it, LauncherResourceMode.PLAY)) }
        }
        binding.mediaVideoWindowPlayer.setOnClickListener { toggle() }
    }

    override suspend fun CoroutineScope.onActive() {
        val loaded = resourceId?.let {
            loadFiles(it, limit = MediaWindow.SCAN_LIMIT, sortMode = SortMode.DATE_DESC)
        } as? LoadLauncherGadgetFilesUseCase.Result.Files
        val source = loaded?.files
            ?.firstOrNull { it.type == MediaType.VIDEO }
            ?.let(MediaWindow::localModel)
        if (loaded == null || source == null) {
            showMessage()
        } else {
            binding.mediaVideoWindowTitle.text = loaded.resourceName
            playUntilCancelled(source)
        }
    }

    /**
     * Holds the codec exactly as long as this cell is live, and hands it back the moment it is not.
     *
     * Releasing only in [onDetachedFromWindow] is not enough, which the phase-02 audit caught: leaving
     * the launcher stops the lifecycle without detaching the view, so a player released only on detach
     * would keep decoding - and playing - behind whatever the user opened instead. The base class
     * cancels this scope on STOP, so the `finally` is the one place that covers both exits.
     */
    private suspend fun playUntilCancelled(source: Uri) {
        try {
            start(source)
            awaitCancellation()
        } finally {
            release()
        }
    }

    private fun start(source: Uri) {
        val exo = ExoPlayer.Builder(context).build()
        player = exo
        binding.mediaVideoWindowPlayer.player = exo
        exo.setMediaItem(MediaItem.fromUri(source))
        exo.repeatMode = Player.REPEAT_MODE_ONE
        // Silent by contract, not by preference - see the class KDoc.
        exo.volume = 0f
        exo.playWhenReady = true
        exo.prepare()
    }

    private fun toggle() {
        val exo = player ?: return
        exo.playWhenReady = !exo.playWhenReady
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    /** Idempotent: the scope's exit and a detach can both reach it, in either order. */
    private fun release() {
        binding.mediaVideoWindowPlayer.player = null
        player?.release()
        player = null
    }

    private fun showMessage() {
        binding.mediaVideoWindowTitle.setText(R.string.launcher_gadget_media_video_window)
        binding.mediaVideoWindowMessage.setText(R.string.launcher_home_cell_unavailable)
        binding.mediaVideoWindowMessage.isVisible = true
        binding.mediaVideoWindowPlayer.isVisible = false
    }
}
