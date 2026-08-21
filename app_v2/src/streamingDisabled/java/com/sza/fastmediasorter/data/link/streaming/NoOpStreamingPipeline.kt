package com.sza.fastmediasorter.data.link.streaming

import com.sza.fastmediasorter.domain.model.link.MediaQualityPreference
import com.sza.fastmediasorter.domain.model.link.StreamingManifest
import com.sza.fastmediasorter.domain.usecase.link.streaming.PipelineOutcome
import com.sza.fastmediasorter.domain.usecase.link.streaming.StreamingPipeline
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0116 §5.1 pillar I: no-op `StreamingPipeline` for `lite` and `photos` flavors.
 *
 * Always returns [PipelineOutcome.Disabled] so the coordinator surfaces a localized
 * `streaming_disabled` toast (Phase 06) instead of crashing. Compile-time isolation:
 * this class lives only in `src/streamingDisabled/`, never reaches market video flavors.
 */
@Singleton
class NoOpStreamingPipeline @Inject constructor() : StreamingPipeline {

    override suspend fun fetchAndRemux(
        manifest: StreamingManifest,
        fileName: String,
        quality: MediaQualityPreference,
        accountId: String?,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): PipelineOutcome = PipelineOutcome.Disabled
}
