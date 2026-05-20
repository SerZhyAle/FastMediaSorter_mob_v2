package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.ImportResult
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearSyncPayload
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import timber.log.Timber
import javax.inject.Inject

private const val STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000L

class ImportNetworkSourcesUseCase @Inject constructor(
    private val repository: NetworkSourceRepository
) {
    suspend operator fun invoke(payload: WearSyncPayload): ImportResult {
        if (System.currentTimeMillis() - payload.sentAt > STALE_THRESHOLD_MS) {
            Timber.w("Sync payload is stale (sentAt=${payload.sentAt}) - ignoring")
            return ImportResult(added = 0, updated = 0, skipped = 0)
        }

        val existingIds = repository.getAllSources().map { it.id }.toSet()
        var added = 0
        var updated = 0
        var skipped = 0

        for (item in payload.sources) {
            val type = when (item.type.uppercase()) {
                "SMB"  -> NetworkSourceType.SMB
                "FTP"  -> NetworkSourceType.FTP
                "SFTP" -> NetworkSourceType.SFTP
                else   -> { Timber.w("Unknown type ${item.type} - skipping"); skipped++; continue }
            }
            val source = NetworkSource(
                id = item.id,
                type = type,
                name = item.name,
                server = item.server,
                port = item.port,
                username = item.username,
                password = item.password,
                shareName = item.shareName,
                basePath = item.basePath,
                domain = item.domain,
                sshPrivateKey = item.sshPrivateKey
            )
            // upsertSource will update if merge key matches, add otherwise.
            // We detect add vs update by checking if the id already existed.
            repository.upsertSource(source)
            if (item.id in existingIds) updated++ else added++
        }

        Timber.i("Import complete: added=$added updated=$updated skipped=$skipped")
        return ImportResult(added, updated, skipped)
    }
}
