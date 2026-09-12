package com.sza.fastmediasorter.wear.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearTileContent
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.model.carriesAssignableTarget
import com.sza.fastmediasorter.wear.domain.usecase.LoadWearTileContentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the row says under the tile's name about the target chosen for it right now. */
sealed interface TileTargetCaption {
    /** A target is assigned and still resolves; [title] is what the tile itself shows. */
    data class Assigned(val title: String) : TileTargetCaption

    /** Nothing was ever chosen for this kind. */
    data object None : TileTargetCaption

    /** Something was chosen and no longer exists - the same state the tile draws as "target missing". */
    data object Missing : TileTargetCaption
}

data class TileTargetRow(
    val kind: WearTileKind,
    @StringRes val labelRes: Int,
    val caption: TileTargetCaption
)

data class TileTargetsUiState(
    val rows: List<TileTargetRow> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * S2587: the rows of the tile-targets settings screen.
 *
 * The list is built from the enum filtered by [carriesAssignableTarget] rather than from a written-out pair
 * of kinds, so a kind added later either appears here on its own or is classified beside the enum - the two
 * places S2511 already put that answer.
 *
 * Captions come from [LoadWearTileContentUseCase], the same reader the tile service uses, so this screen
 * cannot disagree with the tile about which target is assigned or whether it still resolves.
 */
@HiltViewModel
class TileTargetsSettingsViewModel @Inject constructor(
    private val loadWearTileContentUseCase: LoadWearTileContentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TileTargetsUiState())
    val uiState: StateFlow<TileTargetsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /**
     * Re-reads every row's caption.
     *
     * Called again whenever the screen becomes resumed, because the picker this screen opens writes the new
     * target and pops straight back: without the re-read the row would keep naming the target it replaced.
     */
    fun refresh() {
        viewModelScope.launch {
            val rows = WearTileKind.entries
                .filter { kind -> kind.carriesAssignableTarget }
                .map { kind ->
                    TileTargetRow(
                        kind = kind,
                        labelRes = labelFor(kind),
                        caption = captionFor(loadWearTileContentUseCase(kind))
                    )
                }
            _uiState.value = TileTargetsUiState(rows = rows, isLoading = false)
        }
    }

    private fun captionFor(content: WearTileContent): TileTargetCaption = when (content) {
        is WearTileContent.Assigned -> TileTargetCaption.Assigned(content.title)
        is WearTileContent.TargetMissing -> TileTargetCaption.Missing
        // Every other state belongs to a kind this list filtered out, so it can only mean "nothing chosen".
        else -> TileTargetCaption.None
    }
}

/**
 * Exhaustive with no else branch on purpose, for [carriesAssignableTarget]'s reason: a kind added later is
 * named here or it does not compile.
 */
@StringRes
internal fun labelFor(kind: WearTileKind): Int = when (kind) {
    WearTileKind.RESOURCE -> R.string.wear_tile_resource_label
    WearTileKind.STREAM -> R.string.wear_tile_stream_label
    WearTileKind.FAVOURITES -> R.string.wear_tile_favourites_label
    WearTileKind.PROGRAMS -> R.string.wear_tile_programs_label
    WearTileKind.SECTIONS -> R.string.wear_tile_sections_label
}
