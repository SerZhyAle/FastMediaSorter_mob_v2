package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearAccountBroadcastSection
import com.sza.fastmediasorter.wear.domain.repository.WearAccountBroadcastRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** S4229: the account's broadcasts per exchange entry, following the server for as long as it is collected. */
class ObserveAccountBroadcastsUseCase @Inject constructor(
    private val repository: WearAccountBroadcastRepository,
) {

    operator fun invoke(): Flow<List<WearAccountBroadcastSection>> = repository.observe()
}
