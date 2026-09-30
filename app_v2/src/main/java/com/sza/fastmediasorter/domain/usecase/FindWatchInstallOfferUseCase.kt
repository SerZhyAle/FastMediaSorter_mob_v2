package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.WatchInstallOffer
import com.sza.fastmediasorter.domain.repository.WatchFaceInstallRepository
import javax.inject.Inject

/** S4012: the connected watch to offer the watch app and face to, or null when none answers. */
class FindWatchInstallOfferUseCase @Inject constructor(
    private val repository: WatchFaceInstallRepository,
) {
    suspend operator fun invoke(): WatchInstallOffer? = repository.findInstallOffer()
}
