package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearDestinationId
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * S2511: the destination branch of the resolver, which is the whole tap path of a shortcut grid.
 *
 * The three older branches all resolve by reading a store, so they need one; a destination is a static
 * address and must resolve without touching any of the collaborators. That is what these tests pin - the
 * mocks below are deliberately not configured, so a destination that reached a repository would fail here
 * rather than on a watch where the app has never run.
 */
class ResolveWearLaunchRouteUseCaseTest {

    private val useCase = ResolveWearLaunchRouteUseCase(
        networkSourceRepository = mockk<NetworkSourceRepository>(),
        streamChannelRepository = mockk<WearStreamChannelRepository>(),
        prepareWearStreamPlayback = mockk<PrepareWearStreamPlaybackUseCase>(),
        prepareWearFilePlayback = mockk<PrepareWearFilePlaybackUseCase>()
    )

    @Test
    fun `every destination resolves to a route`() = runTest {
        WearDestinationId.entries.forEach { id ->
            assertNotNull(
                "$id is offered on a tile, so it must have somewhere to go",
                useCase(WearLaunchTarget.Destination(id))
            )
        }
    }

    @Test
    fun `no two destinations resolve to the same route`() = runTest {
        val routes = WearDestinationId.entries.map { useCase(WearLaunchTarget.Destination(it)) }

        // Two buttons that land on one screen would be two ways to reach it and no way to reach the other,
        // which is a mapping typo rather than a design choice - and it is invisible on the watch.
        assertEquals("each destination owns its route", routes.size, routes.toSet().size)
    }
}
