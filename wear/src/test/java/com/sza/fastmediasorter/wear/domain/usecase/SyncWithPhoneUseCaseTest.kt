package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import com.sza.fastmediasorter.wear.domain.model.WearSyncLeg
import com.sza.fastmediasorter.wear.domain.model.WearSyncLegResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S2484: unit test for the watch-side orchestrator (SyncWithPhoneUseCase).
 * Covers no-connection refusal and partial-success isolation between outbound legs.
 */
class SyncWithPhoneUseCaseTest {

    private lateinit var context: Context
    private lateinit var exportSources: ExportSourcesUseCase
    private lateinit var reportWearSettings: ReportWearSettingsUseCase
    private lateinit var nodeClient: NodeClient
    private lateinit var messageClient: MessageClient
    private lateinit var useCase: SyncWithPhoneUseCase

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        exportSources = mockk()
        reportWearSettings = mockk()
        nodeClient = mockk()
        messageClient = mockk()

        mockkStatic(Wearable::class)
        every { Wearable.getNodeClient(context) } returns nodeClient
        every { Wearable.getMessageClient(context) } returns messageClient

        val fakeNode = mockk<Node> { every { id } returns "node-1" }
        every { nodeClient.connectedNodes } returns Tasks.forResult(listOf(fakeNode))
        every { messageClient.sendMessage(any(), any(), any()) } returns Tasks.forResult(1)

        useCase = SyncWithPhoneUseCase(context, exportSources, reportWearSettings)
    }

    @After
    fun tearDown() {
        unmockkStatic(Wearable::class)
    }

    @Test
    fun `no connected phone fails every leg`() = runTest {
        every { nodeClient.connectedNodes } returns Tasks.forResult(emptyList())

        val outcome = useCase()

        assertFalse(outcome.allSucceeded)
        assertEquals(WearSyncLeg.entries.size, outcome.failedLegs.size)
    }

    @Test
    fun `both outbound legs succeeding yields those legs Succeeded`() = runTest {
        coEvery { exportSources() } returns Result.success(2)
        coEvery { reportWearSettings() } returns Result.success(Unit)

        val outcome = useCase()

        assertEquals(WearSyncLegResult.Succeeded(2), outcome.legs[WearSyncLeg.RESOURCES_OUT])
        assertEquals(WearSyncLegResult.Succeeded(1), outcome.legs[WearSyncLeg.SETTINGS_OUT])
    }

    @Test
    fun `failing settings report leaves resource export success intact`() = runTest {
        coEvery { exportSources() } returns Result.success(3)
        coEvery { reportWearSettings() } returns Result.failure(RuntimeException("settings failed"))

        val outcome = useCase()

        assertEquals(WearSyncLegResult.Succeeded(3), outcome.legs[WearSyncLeg.RESOURCES_OUT])
        assertEquals(listOf(WearSyncLeg.SETTINGS_OUT), outcome.failedLegs)
    }
}
