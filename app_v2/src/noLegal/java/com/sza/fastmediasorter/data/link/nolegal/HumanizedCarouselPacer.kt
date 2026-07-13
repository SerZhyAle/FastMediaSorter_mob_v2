package com.sza.fastmediasorter.data.link.nolegal

import com.sza.fastmediasorter.domain.usecase.link.LinkDownloadPacer
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.random.Random

/**
 * S0973: noLegal carousel pacer. Waits a uniformly-random 0.5..2.0 s between two items of a
 * multi-object link download so a social host sees human-looking spacing instead of a request
 * burst. The delay is a coroutine [delay], so cancelling the download interrupts the wait at once.
 */
class HumanizedCarouselPacer @Inject constructor() : LinkDownloadPacer {

    override suspend fun pauseBetweenItems() {
        // nextLong is exclusive on the upper bound, so +1 keeps 2.0 s inclusive.
        delay(Random.nextLong(MIN_PAUSE_MS, MAX_PAUSE_MS + 1))
    }

    private companion object {
        const val MIN_PAUSE_MS = 500L
        const val MAX_PAUSE_MS = 2_000L
    }
}
