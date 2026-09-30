package com.sza.fastmediasorter.core.xr

import android.app.Activity
import android.content.Context
import android.content.Intent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.16.1 maxSdkVersion=34; targetSdkVersion=35 needs an explicit SDK pin.
class VrPlaybackActivityContractTest {

    private val context = mockk<Context>(relaxed = true)
    private val payloadHolder = VrLaunchPayloadHolder()

    @Test
    fun `getSynchronousResult returns InvalidUri for file launch without uri`() {
        val contract = VrPlaybackActivityContract(
            entryGateway = unavailableGateway(),
            payloadHolder = payloadHolder,
        )

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
        val contract = VrPlaybackActivityContract(
            entryGateway = unavailableGateway(),
            payloadHolder = payloadHolder,
        )

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
        val contract = VrPlaybackActivityContract(
            entryGateway = unavailableGateway(),
            payloadHolder = payloadHolder,
        )

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

    @Test
    fun `one launch builds the immersive intent once and stores one payload`() {
        val gateway = mockk<XrEntryGateway> {
            every { createImmersiveIntent(any()) } answers {
                Intent(ACTION_IMMERSIVE).putExtra(EXTRA_TOKEN, payloadHolder.put(firstArg<VrLaunchInput>()))
            }
        }
        val contract = VrPlaybackActivityContract(entryGateway = gateway, payloadHolder = payloadHolder)
        val input = VrLaunchInput(launchMode = VrLaunchMode.DIAGNOSTIC_PLAYLIST, mediaType = VrMediaType.IMAGE)

        assertNull(contract.getSynchronousResult(context, input))
        val intent = contract.createIntent(context, input)

        verify(exactly = 1) { gateway.createImmersiveIntent(input) }
        assertEquals(input, payloadHolder.consume<VrLaunchInput>(intent.getStringExtra(EXTRA_TOKEN)))
    }

    @Test
    fun `createIntent for another input than the checked one builds its own intent`() {
        val gateway = mockk<XrEntryGateway> {
            every { createImmersiveIntent(any()) } returns Intent(ACTION_IMMERSIVE)
        }
        val contract = VrPlaybackActivityContract(entryGateway = gateway, payloadHolder = payloadHolder)
        val first = VrLaunchInput(launchMode = VrLaunchMode.DIAGNOSTIC_PLAYLIST, mediaType = VrMediaType.IMAGE)
        val second = VrLaunchInput(launchMode = VrLaunchMode.DIAGNOSTIC_PLAYLIST, mediaType = VrMediaType.VIDEO)

        contract.getSynchronousResult(context, first)
        contract.createIntent(context, second)

        verify(exactly = 1) { gateway.createImmersiveIntent(second) }
    }

    private fun unavailableGateway(): XrEntryGateway {
        return mockk {
            every { createImmersiveIntent(any()) } returns null
            coEvery { enterDiagnosticImage() } returns XrEntryResult.UnavailableNoRuntime
            coEvery { tryEnter() } returns false
        }
    }

    private companion object {
        const val ACTION_IMMERSIVE = "test.action.IMMERSIVE"
        const val EXTRA_TOKEN = "test.extra.TOKEN"
    }
}
