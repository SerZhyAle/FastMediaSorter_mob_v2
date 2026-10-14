package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.data.exchange.WearExchangeEntry
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceMerge
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearAccountFolder
import com.sza.fastmediasorter.wear.domain.model.WearEndpoint
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * S4228: adds a folder of the watch's own account to its network sources (contract DEVICE-EXCHANGE 23 item CF). One
 * folder is one record whatever path brought it (20 item BA), so a folder the phone already sent is updated in place
 * - what the phone set on it, read-only included, is kept - and never doubled.
 */
class AttachAccountFolderUseCase @Inject constructor(
    private val repository: NetworkSourceRepository,
) {

    suspend operator fun invoke(folder: WearAccountFolder, entry: WearExchangeEntry): Result<Unit> = runCatching {
        val stored = repository.getAllSources()
        val incoming = sourceOf(folder, entry).copy(lastEditedAt = System.currentTimeMillis())
        val existing = stored.getOrNull(NetworkSourceMerge.indexOfMatch(stored, incoming))
        val applied = existing?.let {
            incoming.copy(
                id = it.id,
                name = it.name,
                readOnly = it.readOnly,
                iconId = it.iconId,
                supportedMediaTypes = it.supportedMediaTypes,
                allFiles = it.allFiles,
            )
        } ?: incoming
        repository.upsertSource(applied)
        Timber.i("Account folder attached (updated=%s)", existing != null)
    }

    companion object {
        /** The SFTP source a folder opens as: first host as server, the whole host list as its address group. */
        fun sourceOf(folder: WearAccountFolder, entry: WearExchangeEntry): NetworkSource {
            val code = folder.code
            return NetworkSource(
                type = NetworkSourceType.SFTP,
                name = folder.name,
                server = code.hosts.first(),
                port = code.port,
                username = code.username,
                password = code.password.orEmpty(),
                basePath = "/" + folder.root,
                hostKeyFingerprint = code.hostKeyFingerprint,
                endpoints = code.hosts.map { WearEndpoint(it, code.port) }.takeIf { it.size > 1 },
                exchangeAccount = entry.identity,
                exchangeResourceId = folder.resourceId,
                pinProtected = folder.pinProtected,
            )
        }
    }
}
