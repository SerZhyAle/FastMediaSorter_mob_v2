package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorParser
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.usecase.streams.AddStreamSourceUseCase.AddResult
import timber.log.Timber
import javax.inject.Inject

class ImportStreamBroadcastUseCase @Inject constructor(
    private val parser: BroadcastDescriptorParser,
    private val addStreamSourceUseCase: AddStreamSourceUseCase,
    private val repository: StreamSourceRepository,
) {
    suspend operator fun invoke(rawPayload: String): ImportResult =
        when (val outcome = parser.parseDetailed(rawPayload)) {
            is BroadcastDescriptorParser.ParseOutcome.Valid -> addAsStreamSource(outcome.dto)
            BroadcastDescriptorParser.ParseOutcome.UnsupportedVersion ->
                ImportResult.UnsupportedVersion
            BroadcastDescriptorParser.ParseOutcome.Malformed -> ImportResult.InvalidDescriptor
        }

    private suspend fun addAsStreamSource(dto: BroadcastDescriptorDto): ImportResult {
        val result = resolveImport(dto)
        Timber.d("S2813: import named a source=${dto.sourceId != null}, outcome=$result")
        return result
    }

    private suspend fun resolveImport(dto: BroadcastDescriptorDto): ImportResult {
        val title = dto.title?.takeIf { it.isNotBlank() } ?: "Audio Broadcast"
        val deviceId = dto.sourceId?.trim()?.takeIf { it.isNotEmpty() }
        val known = deviceId?.let { repository.getBySourceDeviceId(it) }
        return if (known == null) {
            addNew(dto.url, title, sourceDeviceId = deviceId)
        } else {
            refresh(known.id, known.url, dto.url)
        }
    }

    /**
     * S2813: one broadcasting device owns one row, so a device whose address moved refreshes that row
     * instead of adding a second one. Without it the entry the user had already pinned and launched
     * from keeps pointing at the previous session's dead address, which is the reported defect.
     */
    private suspend fun refresh(id: String, storedUrl: String, scannedUrl: String): ImportResult {
        val trimmed = scannedUrl.trim()
        if (trimmed == storedUrl) return ImportResult.Duplicate
        repository.refreshSourceAddress(id = id, url = trimmed)
        return ImportResult.Updated
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
}
