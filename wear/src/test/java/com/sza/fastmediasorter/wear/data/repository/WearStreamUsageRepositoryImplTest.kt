package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** S3797: plays recorded together must each be counted. */
class WearStreamUsageRepositoryImplTest {

    @Test
    fun `overlapping plays of one stream are all counted`() = runBlocking {
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns InMemorySharedPreferences()
        val repo = WearStreamUsageRepositoryImpl(context, Gson())
        val plays = 50

        repeat(plays) { repo.recordPlay("https://radio.example.com/live") }
        repo.awaitPendingWrites()

        assertEquals(plays, repo.usageByIdentity().getValue("https://radio.example.com/live").playCount)
    }
}
