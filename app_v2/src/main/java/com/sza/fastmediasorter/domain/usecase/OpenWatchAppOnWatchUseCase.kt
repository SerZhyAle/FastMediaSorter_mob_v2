package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.WatchFaceOpenResult
import com.sza.fastmediasorter.domain.repository.WatchFaceInstallRepository
import javax.inject.Inject

/** S4012: opens the watch app's store page on the paired watch. */
class OpenWatchAppOnWatchUseCase @Inject constructor(
    private val repository: WatchFaceInstallRepository,
) {
    suspend operator fun invoke(): WatchFaceOpenResult = repository.openWatchAppListingOnWatch()
}
