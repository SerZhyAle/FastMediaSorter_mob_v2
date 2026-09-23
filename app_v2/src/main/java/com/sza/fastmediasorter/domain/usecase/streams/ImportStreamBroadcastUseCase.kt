package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorParser
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.usecase.streams.AddStreamSourceUseCase.AddResult
import javax.inject.Inject

class ImportStreamBroadcastUseCase @Inject constructor(
    private val parser: BroadcastDescriptorParser,
    private val addStreamSourceUseCase: AddStreamSourceUseCase,
    private val repository: StreamSourceRepository,
) {
    suspend operator fun invoke(rawPayload: String): ImportResult =
        when (val outcome = parser.parseDetailed(rawPayload)) {
            is BroadcastDescriptorParser.ParseOutcome.Valid -> resolveImport(outcome.dto)
            BroadcastDescriptorParser.ParseOutcome.UnsupportedVersion ->
                ImportResult.UnsupportedVersion
            BroadcastDescriptorParser.ParseOutcome.Malformed -> ImportResult.InvalidDescriptor
        }

    private suspend fun resolveImport(dto: BroadcastDescriptorDto): ImportResult {
        val offered = dto.title?.trim()?.takeIf { it.isNotEmpty() }
        val deviceId = dto.sourceId?.trim()?.takeIf { it.isNotEmpty() }
        val known = deviceId?.let { repository.getBySourceDeviceId(it) }
        return if (known == null) {
            addNew(dto.url, offered ?: FALLBACK_TITLE, sourceDeviceId = deviceId)
        } else {
            refresh(known, dto.url, offered)
        }
    }

    /**
     * S2813: one broadcasting device owns one row, so a device whose address moved refreshes that row
     * instead of adding a second one. Without it the entry the user had already pinned and launched
     * from keeps pointing at the previous session's dead address, which is the reported defect.
     *
     * S2868: the same visit may also carry a better name. A row created before the watch named itself
     * through the node still reads its model code, so it is renamed - but only while its title is one
     * this code generated; a title the user typed is never overwritten.
     */
    private suspend fun refresh(known: StreamSourceEntity, scannedUrl: String, offered: String?): ImportResult {
        val trimmed = scannedUrl.trim()
        val moved = trimmed != known.url
        val renamed = offered != null && offered != known.title && isGeneratedTitle(known.title)
        if (moved) repository.refreshSourceAddress(id = known.id, url = trimmed)
        if (renamed) repository.renameSource(id = known.id, title = requireNotNull(offered))
        return if (moved || renamed) ImportResult.Updated else ImportResult.Duplicate
    }

    private fun isGeneratedTitle(title: String): Boolean {
        val stored = title.trim()
        return stored.isEmpty() || stored == FALLBACK_TITLE || MODEL_CODE.matches(stored)
    }

    private suspend fun addNew(url: String, title: String, sourceDeviceId: String?): ImportResult =
        when (addStreamSourceUseCase(url = url, title = title, sourceDeviceId = sourceDeviceId)) {
            AddResult.Success -> ImportResult.Success
            AddResult.Duplicate -> ImportResult.Duplicate
            AddResult.InvalidUrl -> ImportResult.InvalidUrl
        }

    sealed interface ImportResult {
        data object Success : ImportResult

        /** S2813: a known device came back on a new address; its existing entry now points at it. */
        data object Updated : ImportResult
        data object Duplicate : ImportResult
        data object InvalidUrl : ImportResult
        data object InvalidDescriptor : ImportResult
        data object UnsupportedVersion : ImportResult
    }

    private companion object {
        const val FALLBACK_TITLE = "Audio Broadcast"

        // A manufacturer model code as Build.MODEL reports it (SM-L310, SMR870): one token of capitals
        // and digits with at least one digit. A human name has a space or lowercase letters.
        val MODEL_CODE = Regex("^(?=.*\\d)[A-Z][A-Z0-9]*(-[A-Z0-9]+)*$")
    }
}
