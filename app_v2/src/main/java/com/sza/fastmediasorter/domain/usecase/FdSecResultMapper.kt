package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.data.security.fdsec.FdSecOutcome
import com.sza.fastmediasorter.domain.model.FdSecResult

/**
 * S3382: the one crossing from the container format's outcome type to the domain result the UI is
 * allowed to see.
 *
 * It exists so the media type of a recovered file is resolved here, from the sealed true name,
 * rather than in whichever surface happens to open it - the container's own visible name carries no
 * extension at all, so there is nothing above this point to resolve it from.
 */
internal object FdSecResultMapper {

    fun toResult(outcome: FdSecOutcome): FdSecResult = when (outcome) {
        is FdSecOutcome.Packed -> FdSecResult.Packed(outcome.container)
        is FdSecOutcome.Unpacked -> FdSecResult.Restored(
            file = outcome.restored,
            mediaType = MediaTypeUtils.getMediaType(outcome.restored.name),
            realSize = outcome.metadata.realSize,
        )
        is FdSecOutcome.WrongCredentialOrTamper -> FdSecResult.WrongCredentialOrTamper
        is FdSecOutcome.Damaged -> FdSecResult.Damaged
        is FdSecOutcome.Unsupported -> FdSecResult.Unsupported
        is FdSecOutcome.Failed -> FdSecResult.Failed(outcome.detail)
    }
}
