package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorParser
import com.sza.fastmediasorter.domain.usecase.streams.AddStreamSourceUseCase.AddResult
import javax.inject.Inject

class ImportStreamBroadcastUseCase @Inject constructor(
    private val parser: BroadcastDescriptorParser,
    private val addStreamSourceUseCase: AddStreamSourceUseCase,
) {
    suspend operator fun invoke(rawPayload: String): ImportResult =
        when (val outcome = parser.parseDetailed(rawPayload)) {
            is BroadcastDescriptorParser.ParseOutcome.Valid -> addAsStreamSource(outcome.dto)
            BroadcastDescriptorParser.ParseOutcome.UnsupportedVersion ->
                ImportResult.UnsupportedVersion
            BroadcastDescriptorParser.ParseOutcome.Malformed -> ImportResult.InvalidDescriptor
        }

    private suspend fun addAsStreamSource(dto: BroadcastDescriptorDto): ImportResult {
        val title = dto.title?.takeIf { it.isNotBlank() } ?: "Audio Broadcast"
        return when (addStreamSourceUseCase(url = dto.url, title = title)) {
            AddResult.Success -> ImportResult.Success
            AddResult.Duplicate -> ImportResult.Duplicate
            AddResult.InvalidUrl -> ImportResult.InvalidUrl
        }
    }

    sealed interface ImportResult {
        data object Success : ImportResult
        data object Duplicate : ImportResult
        data object InvalidUrl : ImportResult
        data object InvalidDescriptor : ImportResult
        data object UnsupportedVersion : ImportResult
    }
}
