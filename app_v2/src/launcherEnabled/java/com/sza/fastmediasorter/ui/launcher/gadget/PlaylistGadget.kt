package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherListBinding
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import com.sza.fastmediasorter.domain.usecase.launcher.LoadLauncherGadgetFilesUseCase
import com.sza.fastmediasorter.ui.player.PlayerActivity
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import javax.inject.Inject

/**
 * S0404: the tracks of one audio resource, playable from the desktop.
 */
class PlaylistGadget @Inject constructor(
    private val loadFiles: LoadLauncherGadgetFilesUseCase,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_PLAYLIST
    override val defaultSpanW: Int = 2
    override val defaultSpanH: Int = 2
    override val labelRes: Int = R.string.launcher_gadget_playlist
    override val iconRes: Int = R.drawable.ic_playlist
    override val requiresResourceParam: Boolean = true

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        PlaylistGadgetView(container.context, host, param?.toLongOrNull(), loadFiles)
}

private class PlaylistGadgetView(
    context: Context,
    private val host: LauncherGadgetHost,
    private val resourceId: Long?,
    private val loadFiles: LoadLauncherGadgetFilesUseCase,
) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherListBinding.inflate(LayoutInflater.from(context), this)

    private val rowAdapter = LauncherGadgetRowAdapter { row ->
        val id = resourceId ?: return@LauncherGadgetRowAdapter
        openTrack(id, row.id)
    }

    init {
        binding.gadgetListItems.layoutManager = LinearLayoutManager(context)
        binding.gadgetListItems.adapter = rowAdapter
        binding.gadgetListActionPrimary.isVisible = true
        binding.gadgetListActionSecondary.isVisible = true
        binding.gadgetListActionPrimary.contentDescription = context.getString(R.string.launcher_gadget_playlist_play_all)
        binding.gadgetListActionSecondary.contentDescription = context.getString(R.string.launcher_gadget_playlist_shuffle)
        binding.gadgetListActionPrimary.setOnClickListener {
            resourceId?.let { host.run(LauncherCellCommand.Resource(it, LauncherResourceMode.PLAY)) }
        }
        binding.gadgetListActionSecondary.setOnClickListener { shuffleAll() }
    }

    override suspend fun CoroutineScope.onActive() {
        val id = resourceId
        if (id == null) {
            showMessage(context.getString(R.string.launcher_home_cell_unavailable))
            return
        }
        when (val result = loadFiles(id, limit = TRACK_LIMIT, sortMode = SortMode.NAME_ASC)) {
            is LoadLauncherGadgetFilesUseCase.Result.Unavailable ->
                showMessage(context.getString(R.string.launcher_home_cell_unavailable))

            is LoadLauncherGadgetFilesUseCase.Result.Files -> {
                binding.gadgetListTitle.text = result.resourceName
                binding.gadgetListMessage.isVisible = false
                binding.gadgetListItems.isVisible = true
                rowAdapter.submitList(
                    result.files.map { file ->
                        LauncherGadgetRow(
                            id = file.path,
                            // Metadata is only extracted when the resource remembers its file list or the
                            // sort needs it, so a title is frequently absent - the filename is what the
                            // user would see in Browse anyway.
                            title = file.title?.takeIf { it.isNotBlank() } ?: file.name,
                            iconRes = R.drawable.ic_music_note,
                            // ic_music_note is a gold gradient with a gloss highlight - tinting it would
                            // flatten a deliberately drawn icon into a silhouette.
                            tintIcon = false,
                        )
                    }
                )
            }
        }
    }

    private fun showMessage(text: String) {
        binding.gadgetListTitle.text = context.getString(R.string.launcher_gadget_playlist)
        binding.gadgetListMessage.text = text
        binding.gadgetListMessage.isVisible = true
        binding.gadgetListItems.isVisible = false
        binding.gadgetListActionPrimary.isVisible = false
        binding.gadgetListActionSecondary.isVisible = false
    }

    /**
     * Shuffle and per-track start both build their intent here instead of going through
     * [LauncherCellCommand]: the codec is frozen at what a persisted cell can hold, and neither
     * "shuffle" nor "start at this file" is a thing a cell stores. They are gadget-local actions, so
     * they stay gadget-local rather than widening a storage format to carry UI state.
     */
    private fun shuffleAll() {
        val id = resourceId ?: return
        start(PlayerActivity.createPanelIntent(context, resourceId = id, shuffleOnStart = true))
    }

    private fun openTrack(resourceId: Long, filePath: String) {
        start(PlayerActivity.createPanelIntent(context, resourceId = resourceId, initialFilePath = filePath))
    }

    private fun start(intent: android.content.Intent) {
        runCatching {
            context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Timber.w(it, "Launcher playlist gadget: player refused to open") }
    }

    private companion object {
        /**
         * How many tracks are LOADED, not how many are visible: a 2x2 cell fits about six rows at the
         * default density and three at factor 1.5, and the rest are a scroll away. Ten is the point
         * where scrolling still beats opening the player.
         */
        const val TRACK_LIMIT = 10
    }
}
