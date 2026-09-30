package com.sza.fastmediasorter.data.repository.wear

import android.content.Context
import com.google.gson.Gson
import com.sza.fastmediasorter.domain.model.WearSourceTombstonePayload
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

/** Overlapping tombstone writes must all land - each one reads and rewrites the whole list. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class WearResourceTombstoneStoreConcurrencyTest {

    private val edits = 20
    private val context: Context = RuntimeEnvironment.getApplication()
    private val store = SharedPreferencesWearResourceTombstoneStore(context, Gson())

    @Test
    fun `overlapping records keep every tombstone`() = runBlocking {
        (1..edits).map { n ->
            async(Dispatchers.Default) { store.record(WearSourceTombstonePayload("res-$n", n.toLong())) }
        }.awaitAll()

        assertEquals((1..edits).map { "res-$it" }.toSet(), store.read().map { it.id }.toSet())
    }

    @Test
    fun `overlapping forgets and records leave only the recorded tombstones`() = runBlocking {
        (1..edits).forEach { n -> store.record(WearSourceTombstonePayload("old-$n", n.toLong())) }

        val forgets = (1..edits).map { n -> async(Dispatchers.Default) { store.forget("old-$n") } }
        val records = (1..edits).map { n ->
            async(Dispatchers.Default) { store.record(WearSourceTombstonePayload("new-$n", n.toLong())) }
        }
        (forgets + records).awaitAll()

        assertEquals((1..edits).map { "new-$it" }.toSet(), store.read().map { it.id }.toSet())
    }
}
