package com.sza.fastmediasorter.wear.domain.usecase

import android.net.Uri
import com.sza.fastmediasorter.wear.domain.documents.WearDocumentContent
import com.sza.fastmediasorter.wear.domain.documents.WearDocumentFailure
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.repository.SelectedMedia
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearDocumentRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Hands the reading screen a document's text, wherever the file happens to live.
 *
 * S2532: the screen asks for a file and gets text back. Which source it came from is settled here
 * because strategic §5.3 wants a future source - a copy from the phone, another share - to reach the
 * screen without the screen changing, and a view model that branched on "is this network" would have
 * to be edited for every one of them.
 */
class ReadDocumentTextUseCase @Inject constructor(
    private val documentRepository: WearDocumentRepository,
    private val selectedMediaManager: SelectedMediaManager,
    private val downloadNetworkFile: DownloadNetworkFileUseCase
) {

    suspend operator fun invoke(file: WearMediaFile): WearDocumentContent {
        val networkSelection = selectedMediaManager.getSelectedFileById(file.id)
            ?.takeIf { it.isNetworkSource }
        return if (networkSelection == null) {
            read(file.uri)
        } else {
            readNetwork(networkSelection)
        }
    }

    /**
     * The remote file is fetched whole before it is read: the share protocols behind
     * [DownloadNetworkFileUseCase] hand over a stream that has to be consumed in one pass, and the
     * cached copy is what makes re-reading a document - on a font change, on a return to the screen -
     * cost nothing.
     */
    private suspend fun readNetwork(selection: SelectedMedia): WearDocumentContent =
        downloadNetworkFile(selection, DownloadNetworkFileUseCase.Kind.DOCUMENT).fold(
            onSuccess = { cached -> read(Uri.fromFile(cached)) },
            onFailure = { error ->
                Timber.w(error, "Could not fetch network document: ${selection.file.name}")
                WearDocumentContent.Failure(WearDocumentFailure.IO_ERROR)
            }
        )

    private suspend fun read(uri: Uri): WearDocumentContent =
        documentRepository.readText(uri, documentRepository.defaultCapBytes)
}
