package com.sza.fastmediasorter.wear.data.wear

import android.content.Context
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.ChannelClient
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneResourceClientTransferTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val client = PhoneResourceClient(mockk<Context>(relaxed = true), Gson())
    private val channel = mockk<ChannelClient.Channel>()
    private val channelClient = mockk<ChannelClient>()

    private fun <T> completed(result: T?, error: Exception? = null): Task<T> = mockk {
        every { isComplete } returns true
        every { isCanceled } returns false
        every { exception } returns error
        every { getResult() } returns result
    }

    private fun <T> neverCompleting(): Task<T> {
        val task = mockk<Task<T>>()
        every { task.isComplete } returns false
        every { task.addOnCompleteListener(any<Executor>(), any<OnCompleteListener<T>>()) } returns task
        return task
    }

    @Test
    fun `successful copy writes the file and closes the channel`() = runTest {
        val destination = File(tmp.root, "cache/clip.mp4")
        every { channelClient.getInputStream(channel) } returns completed<InputStream>(ByteArrayInputStream(PAYLOAD))
        every { channelClient.close(channel) } returns completed<Void>(null)

        val outcome = client.copyChannelToFile(
            channelClient,
            channel,
            destination,
            StandardTestDispatcher(testScheduler)
        )

        assertEquals(PhoneResourceOutcome.Transferred(destination), outcome)
        assertEquals(PAYLOAD.toList(), destination.readBytes().toList())
        verify(exactly = 1) { channelClient.close(channel) }
    }

    @Test
    fun `failed copy deletes the partial file and closes the channel`() = runTest {
        val destination = tmp.newFile("partial.mp4").apply { writeBytes(PAYLOAD) }
        every { channelClient.getInputStream(channel) } returns completed<InputStream>(null, IOException("link lost"))
        every { channelClient.close(channel) } returns completed<Void>(null)

        val outcome = client.copyChannelToFile(
            channelClient,
            channel,
            destination,
            StandardTestDispatcher(testScheduler)
        )

        assertEquals(PhoneResourceOutcome.PhoneUnavailable, outcome)
        assertFalse(destination.exists())
        verify(exactly = 1) { channelClient.close(channel) }
    }

    @Test
    fun `cancelled copy still deletes the partial file and closes the channel`() = runTest {
        val destination = tmp.newFile("abandoned.mp4").apply { writeBytes(PAYLOAD) }
        every { channelClient.getInputStream(channel) } returns neverCompleting()
        every { channelClient.close(channel) } returns completed<Void>(null)

        val job = launch {
            client.copyChannelToFile(channelClient, channel, destination, StandardTestDispatcher(testScheduler))
        }
        testScheduler.advanceUntilIdle()
        job.cancel()
        job.join()

        assertFalse(destination.exists())
        verify(exactly = 1) { channelClient.close(channel) }
    }

    private companion object {
        val PAYLOAD = byteArrayOf(1, 2, 3, 4, 5)
    }
}
