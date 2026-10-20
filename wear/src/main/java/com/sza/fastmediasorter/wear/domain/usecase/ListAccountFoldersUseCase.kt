package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.data.exchange.WearExchangeDirectoryClient
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeEntry
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeEntryStore
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceMerge
import com.sza.fastmediasorter.wear.domain.model.WearAccountFolderRow
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import kotlinx.coroutines.CancellationException
import java.io.IOException
import javax.inject.Inject

/** S4228: the folders of one exchange entry's account, each marked when this watch already holds it. */
class ListAccountFoldersUseCase @Inject constructor(
    private val entryStore: WearExchangeEntryStore,
    private val directoryClient: WearExchangeDirectoryClient,
    private val repository: NetworkSourceRepository,
) {

    /** The entry [entryIdentity] names, or null once it was removed while its list was open. */
    suspend fun entry(entryIdentity: String): WearExchangeEntry? =
        entryStore.all().firstOrNull { it.identity == entryIdentity }

    suspend operator fun invoke(entry: WearExchangeEntry): Result<List<WearAccountFolderRow>> = try {
        val folders = directoryClient.folders(entry)
        val stored = repository.getAllSources()
        Result.success(
            folders.map { folder ->
                val source = AttachAccountFolderUseCase.sourceOf(folder, entry)
                WearAccountFolderRow(folder, isOnWatch = NetworkSourceMerge.indexOfMatch(stored, source) != -1)
            }
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Result.failure(e)
    }
}
