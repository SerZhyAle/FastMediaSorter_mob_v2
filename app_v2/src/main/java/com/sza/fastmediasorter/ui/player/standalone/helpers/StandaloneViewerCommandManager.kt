package com.sza.fastmediasorter.ui.player.standalone.helpers

import android.view.Menu
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.player.StandalonePlayerViewModel
import com.sza.fastmediasorter.ui.player.helpers.PlayerFileSelectionManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerFrameStepManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerPlaybackRangeManager
import com.sza.fastmediasorter.ui.player.helpers.ViewerKeyStepMath
import kotlinx.coroutines.flow.map

/** The five standalone hosts share command semantics while keeping their existing viewer and collection owners. */
class StandaloneViewerCommandManager(
    private val host: FragmentActivity,
    private val viewModel: StandalonePlayerViewModel,
    private val getPlayer: () -> Player? = { null },
    private val applyRotation: (Int) -> Unit = {},
    private val allowCollection: Boolean = true,
) {
    init {
        host.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = viewModel.stopSlideshow()
            override fun onDestroy(owner: LifecycleOwner) {
                owner.lifecycle.removeObserver(this)
            }
        })
    }

    private val selection by lazy {
        PlayerFileSelectionManager(
            host,
            files = {
                if (allowCollection) viewModel.filesForSelection() else listOfNotNull(viewModel.state.value.mediaFile)
            },
            currentPath = { viewModel.selectedFilePath() },
            selectPath = { path ->
                val index = viewModel.filesForSelection().indexOfFirst { it.path == path }
                if (allowCollection && index >= 0) viewModel.pageToIndex(index)
            },
        )
    }
    private val range by lazy {
        PlayerPlaybackRangeManager(
            host,
            getPlayer,
            getSource = { viewModel.state.value.mediaFile?.path },
            sourceChanges = viewModel.state.map { it.mediaFile?.path },
        )
    }

    val hasModalInput: Boolean get() = selection.isShowing

    fun showFiles(grid: Boolean): Boolean = selection.showFiles(grid)

    fun showNumber(): Boolean = selection.showNumber()

    fun stepFiles(offset: Int): Boolean = withCollection { viewModel.pageByOffset(offset) }

    fun edgeFile(last: Boolean): Boolean = withCollection { viewModel.pageToEdge(last) }

    fun randomFile(): Boolean = withCollection { viewModel.pageRandom() }

    fun startSlideshow(random: Boolean): Boolean = withCollection { viewModel.startOrAccelerateSlideshow(random) }

    private fun withCollection(action: () -> Unit): Boolean {
        if (allowCollection && viewModel.state.value.supportsFolderPaging) {
            action()
        } else {
            explain(R.string.player_file_single)
        }
        return true
    }

    fun rotate(clockwise: Boolean): Boolean {
        val type = viewModel.state.value.mediaType
        if (type !in ROTATABLE_TYPES) return false
        if (clockwise) viewModel.rotateSession90() else viewModel.rotateSessionCounter90()
        applyRotation(viewModel.state.value.sessionRotationAngle)
        return true
    }

    fun changeSpeed(faster: Boolean): Boolean {
        val player = getPlayer()
        if (player == null) {
            explain(R.string.player_action_unavailable)
        } else {
            player.setPlaybackSpeed(ViewerKeyStepMath.nextSpeed(player.playbackParameters.speed, faster))
        }
        return true
    }

    fun cycleAudioTrack(): Boolean {
        val player = getPlayer() ?: return explain(R.string.player_action_unavailable)
        val choices = audioChoices(player)
        val selected = choices.indexOfFirst { it.group.isTrackSelected(it.index) }
        val next = ViewerKeyStepMath.nextTrackIndex(selected, choices.size)?.let(choices::get)
        if (next == null) {
            explain(R.string.player_audio_track_single)
        } else {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .setOverrideForType(TrackSelectionOverride(next.group.mediaTrackGroup, listOf(next.index)))
                .build()
        }
        return true
    }

    private fun audioChoices(player: Player): List<AudioChoice> = player.currentTracks.groups.flatMap { group ->
        if (group.type == C.TRACK_TYPE_AUDIO) {
            (0 until group.length).filter { group.isTrackSupported(it) }.map { AudioChoice(group, it) }
        } else {
            emptyList()
        }
    }

    fun frameStep(forward: Boolean): Boolean {
        val player = getPlayer() as? ExoPlayer
        val stepped = forward && player != null && PlayerFrameStepManager.stepForward(player)
        if (!stepped) {
            explain(if (forward) R.string.player_frame_unavailable else R.string.player_frame_back_unavailable)
        }
        return true
    }

    fun toggleLoop(): Boolean = range.toggle()

    fun populateMenu(menu: Menu) {
        addMenu(menu, R.id.menu_file_grid, R.string.player_file_grid, R.drawable.ic_view_grid)
        addMenu(menu, R.id.menu_file_list, R.string.player_file_list, R.drawable.ic_view_list)
        addMenu(menu, R.id.menu_file_number, R.string.player_file_jump, 0)
        if (viewModel.state.value.mediaType == MediaType.VIDEO) {
            addMenu(menu, R.id.menu_frame_forward, R.string.player_frame_forward, R.drawable.ic_skip_next)
            addMenu(menu, R.id.menu_frame_back, R.string.player_frame_back_unavailable, R.drawable.ic_skip_previous)
            menu.findItem(R.id.menu_frame_back)?.isEnabled = false
            addMenu(menu, R.id.menu_ab_loop, R.string.player_loop_title, R.drawable.ic_repeat_one)
        }
    }

    private fun addMenu(menu: Menu, id: Int, title: Int, icon: Int) {
        if (menu.findItem(id) == null) menu.add(Menu.NONE, id, Menu.NONE, title).setIcon(icon)
    }

    fun handleMenu(id: Int): Boolean = when (id) {
        R.id.menu_file_grid -> showFiles(grid = true)
        R.id.menu_file_list -> showFiles(grid = false)
        R.id.menu_file_number -> showNumber()
        R.id.menu_frame_forward -> frameStep(forward = true)
        R.id.menu_frame_back -> frameStep(forward = false)
        R.id.menu_ab_loop -> toggleLoop()
        else -> false
    }

    private fun explain(message: Int): Boolean {
        Toast.makeText(host, message, Toast.LENGTH_SHORT).show()
        return true
    }

    private data class AudioChoice(val group: Tracks.Group, val index: Int)

    private companion object {
        val ROTATABLE_TYPES = setOf(MediaType.IMAGE, MediaType.GIF, MediaType.VIDEO)
    }
}
