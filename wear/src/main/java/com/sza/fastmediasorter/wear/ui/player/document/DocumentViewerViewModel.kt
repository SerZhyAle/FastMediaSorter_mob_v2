package com.sza.fastmediasorter.wear.ui.player.document

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.documents.DocumentFontSize
import com.sza.fastmediasorter.wear.domain.documents.DocumentReadingAnchor
import com.sza.fastmediasorter.wear.domain.documents.WearDocumentContent
import com.sza.fastmediasorter.wear.domain.documents.WearDocumentFailure
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.ReadDocumentTextUseCase
import com.sza.fastmediasorter.wear.ui.common.WearListPosition
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** The sentinel the other player view models already treat as "no file was named". */
private const val UNRESOLVED_FILE_ID = -1L

/**
 * Longest run of characters the reader puts into a single list item.
 *
 * A scaling list measures and scales whole items, so a file written as one unbroken line would become
 * one item taller than the watch: it could not be anchored inside, and the scaling at the rim would
 * shrink the whole wall of text at once rather than its edges.
 */
private const val MAX_PARAGRAPH_CHARS = 240

/**
 * The reader's half of the document screen: it asks for the text and says what came back.
 *
 * S2532: the fourth content view model, built like the three players - the route carries a file id
 * and nothing else, so the file is resolved here and the screen receives only text and outcomes
 * (strategic §5). Nothing about where the bytes came from reaches this class: [ReadDocumentTextUseCase]
 * settles local against network, which is what lets a future source arrive without touching the screen.
 */
@HiltViewModel
class DocumentViewerViewModel @Inject constructor(
    private val playbackSetManager: PlaybackSetManager,
    private val selectedMediaManager: SelectedMediaManager,
    private val readDocumentText: ReadDocumentTextUseCase,
    private val preferences: WearPreferencesRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentViewerUiState())
    val uiState: StateFlow<DocumentViewerUiState> = _uiState.asStateFlow()

    private val fileId: Long =
        savedStateHandle.get<Long>(WearRoutes.ARG_FILE_ID) ?: UNRESOLVED_FILE_ID

    /**
     * What the stored reading position is filed under.
     *
     * The file's address rather than its id: [WearMediaFile.id] is re-issued on every listing - the
     * browse screen numbers its rows, the phone and favourites screens hash a token - so an id stored
     * today points at another file tomorrow, which is exactly the anchor that must never be restored.
     * The address is what the file IS, and it survives a rescan and a restart alike.
     */
    private var documentKey: String? = null

    private var documentSizeBytes = 0L

    init {
        viewModelScope.launch {
            // Read before the text so the first frame that carries paragraphs already carries the size
            // they are drawn at, rather than re-laying the whole document out one frame later.
            _uiState.update { it.copy(fontSize = preferences.documentFontSize.first()) }
            openDocument()
        }
    }

    fun onFontSizeChanged(size: DocumentFontSize) {
        _uiState.update { it.copy(fontSize = size) }
        viewModelScope.launch {
            preferences.setDocumentFontSize(size)
        }
    }

    /**
     * Records where the reader came to rest.
     *
     * Called when the list settles and never per scrolled frame: strategic §3.2 names the watch's
     * battery as this ticket's main constraint, and only the last position of a gesture is ever read
     * back, so every write before it would be spent on a value nothing asks for.
     */
    fun onScrollStopped(index: Int, offset: Int) {
        val key = documentKey ?: return
        Timber.d("S2532: reading position settled index=%d offset=%d", index, offset)
        viewModelScope.launch {
            preferences.setReadingPosition(key, documentSizeBytes, index, offset)
        }
    }

    private suspend fun openDocument() {
        val file = resolveFile()
        if (file == null) {
            // A browse entry that outlived its set lands here, which is the same thing to the wearer
            // as a file that has been deleted - so it is reported as the absence it is.
            Timber.i("No document for fileId=%d in the published set or the selection", fileId)
            _uiState.update {
                it.copy(isLoading = false, failure = WearDocumentFailure.NOT_FOUND)
            }
            return
        }
        val key = file.uri.toString()
        documentKey = key
        documentSizeBytes = file.size
        _uiState.update { it.copy(fileName = file.name) }
        Timber.d("S2532: reader opening %s size=%d restoring=%s", file.name, file.size, key)
        val content = readDocumentText(file)
        // The anchor is resolved BEFORE the text is published, so the screen never sees a frame where
        // the paragraphs exist and the restored position has not been decided yet - in that frame it
        // would report the opening anchor back as a settled position and overwrite what it came for.
        render(content, preferences.readingPositionFor(key, file.size))
    }

    // The anchor becomes a list position here and nowhere earlier: the pair of numbers is domain data
    // all the way through persistence, and only this class knows the surface it is being handed to.
    private fun render(content: WearDocumentContent, restored: DocumentReadingAnchor?) {
        val position = restored?.let { WearListPosition(it.index, it.offset) }
        _uiState.update { state ->
            when (content) {
                is WearDocumentContent.Text -> state.copy(
                    isLoading = false,
                    paragraphs = paragraphsOf(content.text),
                    truncated = content.truncated,
                    initialPosition = position
                )
                WearDocumentContent.Empty -> state.copy(isLoading = false, isEmpty = true)
                is WearDocumentContent.Failure -> state.copy(
                    isLoading = false,
                    failure = content.reason
                )
            }
        }
    }

    /**
     * The set the browse screen published is asked first: it holds the file exactly as it was listed,
     * while the selection holds only the last file tapped anywhere in the app.
     */
    private fun resolveFile(): WearMediaFile? =
        playbackSetManager.currentSet.value?.files?.firstOrNull { it.id == fileId }
            ?: selectedMediaManager.getSelectedFileById(fileId)?.file
}

/**
 * Cuts the document into the items the list draws.
 *
 * Blank lines carry no text and are dropped rather than drawn: on a watch they cost a whole item of
 * vertical space each, and a text file that separates its paragraphs with them would spend half the
 * scroll on nothing.
 */
internal fun paragraphsOf(text: String): List<String> =
    text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .flatMap { wrapLine(it) }
        .toList()

/** Splits at the last space before the limit, so a long line is never cut through a word. */
private fun wrapLine(line: String): List<String> {
    if (line.length <= MAX_PARAGRAPH_CHARS) {
        return listOf(line)
    }
    val pieces = mutableListOf<String>()
    var rest = line
    while (rest.length > MAX_PARAGRAPH_CHARS) {
        val window = rest.take(MAX_PARAGRAPH_CHARS)
        val cut = window.lastIndexOf(' ').takeIf { it > 0 } ?: MAX_PARAGRAPH_CHARS
        pieces += rest.take(cut).trim()
        rest = rest.drop(cut).trim()
    }
    if (rest.isNotEmpty()) {
        pieces += rest
    }
    return pieces
}
