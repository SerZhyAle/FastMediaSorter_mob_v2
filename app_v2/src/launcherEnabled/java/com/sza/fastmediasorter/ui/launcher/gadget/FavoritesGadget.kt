package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.databinding.GadgetLauncherListBinding
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.repository.FavoritesRepository
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * S1170: the favourites widget as a desktop cell - the same file list, drawn in our own process.
 *
 * The Android-home widget fills its rows through `FavoritesWidgetService`, a `RemoteViewsService`. That
 * machinery exists only because a home-screen widget renders in the launcher's process; a desktop cell
 * renders in ours, so it binds a RecyclerView to the same table instead of reproducing the service.
 */
class FavoritesGadget @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
) : LauncherGadget {

    override val key: String = KEY
    override val defaultSpanW: Int = SPAN
    override val defaultSpanH: Int = SPAN
    override val labelRes: Int = R.string.widget_favorites_label
    override val iconRes: Int = R.drawable.ic_widget_favorites
    override val requiresResourceParam: Boolean = false

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        FavoritesGadgetView(container.context, host, favoritesRepository)

    private companion object {
        const val KEY = "favorites"

        /** The widget's own declared `targetCellWidth`/`targetCellHeight`. */
        const val SPAN = 3
    }
}

private class FavoritesGadgetView(
    context: Context,
    private val host: LauncherGadgetHost,
    private val favoritesRepository: FavoritesRepository,
) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherListBinding.inflate(LayoutInflater.from(context), this)

    private val rowAdapter = LauncherGadgetRowAdapter { row ->
        // The row's own file, not just its resource - the widget opens the tapped item and so must this.
        row.id.toLongOrNull()?.let { favoriteId ->
            rows[favoriteId]?.let { host.run(LauncherCellCommand.FavoriteFile(it.first, it.second)) }
        }
    }

    /** favourite id -> (resourceId, uri), so a row tap needs no second database read. */
    private var rows: Map<Long, Pair<Long, String>> = emptyMap()

    init {
        binding.gadgetListTitle.setText(R.string.widget_favorites_label)
        binding.gadgetListItems.layoutManager = LinearLayoutManager(context)
        binding.gadgetListItems.adapter = rowAdapter
        binding.gadgetListMessage.setOnClickListener {
            host.run(LauncherCellCommand.Feature(InternalRouteCatalog.KEY_FAVORITES))
        }
    }

    /**
     * Collected, not snapshotted: [LauncherGadgetView.onActive] already scopes this to attached AND
     * STARTED, so staying subscribed costs nothing while the desktop is up and the list cannot go stale
     * behind a favourite added elsewhere in the app.
     */
    override suspend fun CoroutineScope.onActive() {
        // The file-only slice: a live-channel favourite has no file to open from a cell (S0783).
        favoritesRepository.getFileFavorites().collect { favorites ->
            val visible = favorites.take(FAVORITE_LIMIT)
            rows = visible.associate { it.id to (it.resourceId to it.uri) }
            binding.gadgetListMessage.isVisible = visible.isEmpty()
            binding.gadgetListItems.isVisible = visible.isNotEmpty()
            if (visible.isEmpty()) {
                binding.gadgetListMessage.setText(R.string.launcher_gadget_favorites_empty)
                binding.gadgetListMessage.isClickable = true
                binding.gadgetListMessage.isFocusable = true
                return@collect
            }
            rowAdapter.submitList(
                visible.map { favorite ->
                    LauncherGadgetRow(
                        id = favorite.id.toString(),
                        title = favorite.displayName,
                        iconRes = iconFor(favorite.mediaType),
                        // Flat single-colour glyphs, invisible on the card unless tinted.
                        tintIcon = true,
                    )
                }
            )
        }
    }

    /** Same mapping the favourites widget's row factory uses, so the two lists cannot disagree. */
    private fun iconFor(mediaType: Int): Int = when (mediaType) {
        MEDIA_TYPE_VIDEO -> R.drawable.ic_video
        MEDIA_TYPE_AUDIO -> R.drawable.ic_audio
        MEDIA_TYPE_GIF -> R.drawable.ic_gif
        else -> R.drawable.ic_image
    }

    private companion object {
        /** How many rows are LOADED; the cell scrolls, so this is not how many are visible. */
        const val FAVORITE_LIMIT = 10

        const val MEDIA_TYPE_VIDEO = 1
        const val MEDIA_TYPE_AUDIO = 2
        const val MEDIA_TYPE_GIF = 3
    }
}
