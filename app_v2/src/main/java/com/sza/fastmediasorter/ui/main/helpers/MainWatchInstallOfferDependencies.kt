package com.sza.fastmediasorter.ui.main.helpers

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.domain.usecase.FindWatchInstallOfferUseCase
import com.sza.fastmediasorter.domain.usecase.OpenWatchAppOnWatchUseCase
import com.sza.fastmediasorter.domain.usecase.OpenWatchFaceOnWatchUseCase
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

/**
 * S4012: what [MainWatchInstallOfferManager] needs, injected as one unit so [MainHelperFactory] stays under
 * the constructor parameter ceiling. The IO dispatcher is injected rather than hardcoded so a test can
 * replace it (S3785).
 */
class MainWatchInstallOfferDependencies @Inject constructor(
    val findOffer: FindWatchInstallOfferUseCase,
    val openWatchApp: OpenWatchAppOnWatchUseCase,
    val openWatchFace: OpenWatchFaceOnWatchUseCase,
    @IoDispatcher val ioDispatcher: CoroutineDispatcher,
)
