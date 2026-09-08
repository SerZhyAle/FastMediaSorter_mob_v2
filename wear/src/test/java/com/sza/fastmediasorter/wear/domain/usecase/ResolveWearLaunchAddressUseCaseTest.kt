package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearDestinationId
import com.sza.fastmediasorter.wear.domain.model.WearLaunchAddress
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2511: the destination branch of the resolver, which is the whole tap path of a shortcut grid.
 *
 * The three older branches all resolve by reading a store, so they need one; a destination is a static
 * address and must resolve without touching any of the collaborators. That is what these tests pin - the
 * mocks below are deliberately not configured, so a destination that reached a repository would fail here
 * rather than on a watch where the app has never run.
 *
 * S2751: the resolver answers with a domain address now, so the "no two destinations collide" half moved
 * to `WearLaunchRoutesTest`, where the route table it was really about lives.
 */
class ResolveWearLaunchAddressUseCaseTest {

    private val useCase = ResolveWearLaunchAddressUseCase(
        networkSourceRepository = mockk<NetworkSourceRepository>(),
        streamChannelRepository = mockk<WearStreamChannelRepository>(),
        prepareWearStreamPlayback = mockk<PrepareWearStreamPlaybackUseCase>(),
        prepareWearFilePlayback = mockk<PrepareWearFilePlaybackUseCase>()
    )

    @Test
    fun `every destination resolves to its own screen`() = runTest {
        WearDestinationId.entries.forEach { id ->
            assertEquals(
                "$id is offered on a tile, so it must have somewhere to go",
                WearLaunchAddress.Screen(id),
                useCase(WearLaunchTarget.Destination(id))
            )
        }
    }

    @Test
    fun `a pick target names the tile it is picking for`() = runTest {
        val kind = WearTileKind.STREAM

        assertEquals(
            WearLaunchAddress.TileTargetPicker(kind),
            useCase(WearLaunchTarget.Pick(kind))
        )
    }
}
