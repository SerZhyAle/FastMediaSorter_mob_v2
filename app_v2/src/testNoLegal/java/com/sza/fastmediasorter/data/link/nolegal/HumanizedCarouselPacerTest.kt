package com.sza.fastmediasorter.data.link.nolegal

import com.sza.fastmediasorter.domain.usecase.link.LinkDownloadPacer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S0973: the humanized carousel pause always lands in the 0.5..2.0 s window. Uses the test
 * scheduler's virtual clock, so [pauseBetweenItems]'s `delay` advances testScheduler.currentTime by
 * exactly the random interval - no wall-clock sleep. Sampled many times to cover the random range.
 */
class HumanizedCarouselPacerTest {

    private val pacer: LinkDownloadPacer = HumanizedCarouselPacer()

    @Test
    fun pause_is_within_half_second_to_two_seconds() = runTest {
        repeat(200) {
            val before = testScheduler.currentTime
            pacer.pauseBetweenItems()
            val elapsed = testScheduler.currentTime - before
            assertTrue("elapsed=$elapsed ms out of [500,2000]", elapsed in 500L..2000L)
        }
    }
}
