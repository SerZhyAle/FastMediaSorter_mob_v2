package com.sza.fastmediasorter.data.repository.wear

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Overlapping alias writes must all land - each one reads and rewrites the whole map. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class WearResourceIdAliasStoreConcurrencyTest {

    private val edits = 20
    private val context: Context = RuntimeEnvironment.getApplication()
    private val store = SharedPreferencesWearResourceIdAliasStore(context, Gson())

    @Test
    fun `overlapping records keep every alias`() = runBlocking {
        (1..edits).map { n -> async(Dispatchers.Default) { store.record("uuid-$n", n.toLong()) } }.awaitAll()

        (1..edits).forEach { n -> assertEquals(n.toLong(), store.resolve("uuid-$n")) }
    }

    @Test
    fun `overlapping forgets and records leave only the recorded aliases`() = runBlocking {
        (1..edits).forEach { n -> store.record("old-$n", n.toLong()) }

        val forgets = (1..edits).map { n -> async(Dispatchers.Default) { store.forget("old-$n") } }
        val records = (1..edits).map { n -> async(Dispatchers.Default) { store.record("new-$n", n.toLong()) } }
        (forgets + records).awaitAll()

        (1..edits).forEach { n ->
            assertNull(store.resolve("old-$n"))
            assertEquals(n.toLong(), store.resolve("new-$n"))
        }
    }
}
