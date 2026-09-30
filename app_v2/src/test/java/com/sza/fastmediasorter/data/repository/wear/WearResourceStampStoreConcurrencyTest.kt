package com.sza.fastmediasorter.data.repository.wear

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** S3832: overlapping stamp writes must all land - each one reads and rewrites the whole map. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class WearResourceStampStoreConcurrencyTest {

    private val edits = 20
    private val context: Context = RuntimeEnvironment.getApplication()
    private val store = SharedPreferencesWearResourceStampStore(context, Gson())

    @Test
    fun `overlapping stamp writes keep every stamp`() = runBlocking {
        (1..edits).map { n -> async(Dispatchers.Default) { store.writeStamp("res-$n", n.toLong()) } }.awaitAll()

        assertEquals((1..edits).associate { "res-$it" to it.toLong() }, store.readStamps())
    }

    @Test
    fun `overlapping forgets and writes leave only the written stamps`() = runBlocking {
        (1..edits).forEach { n -> store.writeStamp("old-$n", n.toLong()) }

        val forgets = (1..edits).map { n -> async(Dispatchers.Default) { store.forget("old-$n") } }
        val writes = (1..edits).map { n -> async(Dispatchers.Default) { store.writeStamp("new-$n", n.toLong()) } }
        (forgets + writes).awaitAll()

        assertEquals((1..edits).associate { "new-$it" to it.toLong() }, store.readStamps())
    }
}
