package com.sza.fastmediasorter.core.xr

import android.app.Activity
import android.content.Context
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.11.1 maxSdkVersion=34; targetSdkVersion=35 needs an explicit SDK pin.
class VrPlaybackActivityContractTest {

    private val context = mockk<Context>(relaxed = true)

    @Test
    fun `getSynchronousResult returns InvalidUri for file launch without uri`() {
        val contract = VrPlaybackActivityContract(entryGateway = unavailableGateway())

        val result = contract.getSynchronousResult(
            context = context,
            input = VrLaunchInput(
                launchMode = VrLaunchMode.FILE_URI,
                mediaType = VrMediaType.IMAGE,
            ),
        )

        assertEquals(
            VrLaunchResult.Unavailable(VrLaunchUnavailableReason.InvalidUri),
            result?.value,
        )
    }

    @Test
    fun `parseResult keeps InvalidUri for synthetic fallback intent`() {
        val contract = VrPlaybackActivityContract(entryGateway = unavailableGateway())

        val fallbackIntent = contract.createIntent(
            context = context,
            input = VrLaunchInput(
                launchMode = VrLaunchMode.FILE_URI,
                mediaType = VrMediaType.IMAGE,
            ),
        )

        assertEquals(
            VrLaunchResult.Unavailable(VrLaunchUnavailableReason.InvalidUri),
            contract.parseResult(Activity.RESULT_CANCELED, fallbackIntent),
        )
    }

    @Test
    fun `getSynchronousResult keeps NoRuntime for valid input when gateway is unavailable`() {
        val contract = VrPlaybackActivityContract(entryGateway = unavailableGateway())

        val result = contract.getSynchronousResult(
            context = context,
            input = VrLaunchInput(
                launchMode = VrLaunchMode.DIAGNOSTIC_PLAYLIST,
                mediaType = VrMediaType.IMAGE,
            ),
        )

        assertEquals(
            VrLaunchResult.Unavailable(VrLaunchUnavailableReason.NoRuntime),
            result?.value,
        )
    }

    private fun unavailableGateway(): XrEntryGateway {
        return mockk {
            every { createImmersiveIntent(any()) } returns null
            coEvery { enterDiagnosticImage() } returns XrEntryResult.UnavailableNoRuntime
            coEvery { tryEnter() } returns false
        }
    }
}