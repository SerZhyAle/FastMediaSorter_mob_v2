package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherMediaAudioWindowBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import com.sza.fastmediasorter.domain.usecase.launcher.LoadLauncherGadgetFilesUseCase
import com.sza.fastmediasorter.ui.launcher.gadget.nowplaying.NowPlayingCommand
import com.sza.fastmediasorter.ui.launcher.gadget.nowplaying.OwnSessionNowPlayingSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import javax.inject.Inject

/**
 * S1754: one audio resource, played and steered from its own desktop cell.
 *
 * Deliberately not a second [AudioNowPlayingGadget]: that one is a 2x1 strip following whatever session
 * is active, including another application's. This is a 2x2 window bound to ONE of the user's resources -
 * it says which resource it is before it says what plays, and its play button starts THAT resource.
 *
 * It drives no playback stack of its own. Transport goes to the app's existing playback service through
 * the same source the now-playing strip uses, so there is exactly one place that talks to that service.
 */
class AudioWindowGadget @Inject constructor(
    private val loadFiles: LoadLauncherGadgetFilesUseCase,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_MEDIA_AUDIO_WINDOW
    override val defaultSpanW: Int = MediaWindow.SPAN
    override val defaultSpanH: Int = MediaWindow.SPAN
    override val labelRes: Int = R.string.launcher_gadget_media_audio_window
    override val iconRes: Int = R.drawable.ic_audio
    override val requiresResourceParam: Boolean = true

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        AudioWindowGadgetView(container.context, host, param?.toLongOrNull(), loadFiles)
}

private class AudioWindowGadgetView(
    context: Context,
    private val host: LauncherGadgetHost,
    private val resourceId: Long?,
    private val loadFiles: LoadLauncherGadgetFilesUseCase,
) : LauncherGadgetView(context) {

    private val binding =
        GadgetLauncherMediaAudioWindowBinding.inflate(LayoutInflater.from(context), this)

    private val ownSession = OwnSessionNowPlayingSource(context)

    /** What a play tap starts when nothing is playing yet - the resource's newest track. */
    private var firstTrackName: String? = null

    init {
        binding.mediaAudioWindowTitle.setOnClickListener { open() }
        binding.mediaAudioWindowPrevious.setOnClickListener { ownSession.send(NowPlayingCommand.PREVIOUS) }
        binding.mediaAudioWindowNext.setOnClickListener { ownSession.send(NowPlayingCommand.NEXT) }
        binding.mediaAudioWindowPlayPause.setOnClickListener { playOrPause() }
    }

    /**
     * Polls rather than subscribes, exactly as [AudioNowPlayingGadget] does and for the same reason: the
     * service publishes its state as a snapshot blob with no change feed. The loop is bounded by the base
     * class - it runs only while this cell is attached and the launcher is STARTED.
     */
    override suspend fun CoroutineScope.onActive() {
        val loaded = resourceId?.let {
            loadFiles(it, limit = MediaWindow.SCAN_LIMIT, sortMode = SortMode.DATE_DESC)
        } as? LoadLauncherGadgetFilesUseCase.Result.Files
        binding.mediaAudioWindowTitle.text = loaded?.resourceName
            ?: context.getString(R.string.launcher_gadget_media_audio_window)
        firstTrackName = loaded?.files?.firstOrNull { it.type == MediaType.AUDIO }?.name
        while (isActive) {
            render()
            delay(POLL_MS)
        }
    }

    /**
     * The playing track when the service has one, otherwise what a play tap would start.
     *
     * The transport row never hides. Unlike the now-playing strip it is not steering someone else's
     * session that may be gone - this cell owns a resource it can always start.
     */
    private fun render() {
        val state = ownSession.read()
        binding.mediaAudioWindowTrack.text = when {
            state.active && state.title.isNotBlank() -> state.title
            else -> firstTrackName.orEmpty()
        }
        binding.mediaAudioWindowPlayPause.setImageResource(
            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    /**
     * Resumes the running session, or starts this resource when there is none.
     *
     * Starting goes through the host command rather than the service directly: a stopped service cannot
     * be started from the background on API 26+, and the host already owns the one launch guard every
     * desktop cell shares.
     */
    private fun playOrPause() {
        if (ownSession.read().active) {
            ownSession.send(NowPlayingCommand.PLAY_PAUSE)
        } else {
            open()
        }
    }

    private fun open() {
        resourceId?.let { host.run(LauncherCellCommand.Resource(it, LauncherResourceMode.PLAY)) }
    }

    private companion object {
        /** Matches the now-playing strip: a title change is not worth a tighter loop on a home screen. */
        const val POLL_MS = 2_000L
    }
}
