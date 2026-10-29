package com.sza.fastmediasorter.domain.usecase.broadcast

import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.data.repository.DeviceExchangeAccountRepository
import com.sza.fastmediasorter.domain.repository.PreparedBroadcastLinkStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * S4247, owner decision В6: whether a start keeps the promise of a handed-out link. [invoke] takes null for a
 * stored-file start, whose mode is known only after the track probe at the start.
 */
class CheckPreparedBroadcastStartUseCase @Inject constructor(
    private val store: PreparedBroadcastLinkStore,
    private val accounts: DeviceExchangeAccountRepository,
) {

    enum class Verdict { NothingPrepared, Matches, WillNotWork }

    suspend operator fun invoke(mode: BroadcastMode?): Verdict {
        val now = System.currentTimeMillis()
        val live = store.all().filter { it.isLive(now) }.sortedBy { it.createdAtMs }
        val identity = accounts.producer.first()?.identity
        val ofEntry = live.filter { it.entryIdentity == identity }
        return when {
            live.isEmpty() -> Verdict.NothingPrepared
            mode == null && ofEntry.isNotEmpty() -> Verdict.Matches
            ofEntry.any { it.mode == mode?.name } -> Verdict.Matches
            else -> Verdict.WillNotWork
        }
    }
}
