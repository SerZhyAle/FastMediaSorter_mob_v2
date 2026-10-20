package com.sza.fastmediasorter.wear.data.exchange

import android.content.SharedPreferences
import com.google.gson.JsonObject
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPass
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** S4261: a handed-off broadcast is kept per channel URL under a key that names no part of it. */
class WearBroadcastPassStoreTest {

    private val prefs = MapPreferences()
    private val store = EncryptedWearBroadcastPassStore(prefs)

    @Test
    fun `a stored broadcast reads back with its descriptor and pass`() = runBlocking {
        assertEquals(true, store.save(URL_A, stored("b1", "secret")))

        val read = store.get(URL_A)

        assertNotNull(read)
        read!!
        assertEquals("b1", read.broadcastId)
        assertEquals("VIDEO_AUDIO", read.descriptor.get("mode").asString)
        assertEquals("own", read.pass?.user)
        assertEquals("secret", read.pass?.password)
    }

    @Test
    fun `an unprotected broadcast reads back without a pass`() = runBlocking {
        store.save(URL_A, WearStoredBroadcast("b1", descriptor(), pass = null))

        assertNull(store.get(URL_A)?.pass)
    }

    @Test
    fun `a second save replaces the first`() = runBlocking {
        store.save(URL_A, stored("b1", "old"))
        store.save(URL_A, stored("b2", "new"))

        assertEquals("b2", store.get(URL_A)?.broadcastId)
        assertEquals("new", store.get(URL_A)?.pass?.password)
        assertEquals(1, prefs.values.size)
    }

    @Test
    fun `forget removes the stored broadcast`() = runBlocking {
        store.save(URL_A, stored("b1", "secret"))

        store.forget(URL_A)

        assertNull(store.get(URL_A))
    }

    @Test
    fun `retainOnly keeps the listed channels and drops the rest`() = runBlocking {
        store.save(URL_A, stored("b1", "a"))
        store.save(URL_B, stored("b2", "b"))
        prefs.values["other_key"] = "kept"

        store.retainOnly(setOf(URL_B))

        assertNull(store.get(URL_A))
        assertEquals("b2", store.get(URL_B)?.broadcastId)
        assertEquals("kept", prefs.values["other_key"])
    }

    @Test
    fun `the stored key names no part of the url`() = runBlocking {
        store.save(URL_A, stored("b1", "secret"))

        val key = prefs.values.keys.single()

        assertFalse(key.contains("192.168"))
        assertFalse(key.contains("stream"))
        assertFalse(key.contains("8080"))
    }

    private fun stored(id: String, password: String) =
        WearStoredBroadcast(id, descriptor(), WearBroadcastPass("own", password))

    private fun descriptor() = JsonObject().apply { addProperty("mode", "VIDEO_AUDIO") }

    private class MapPreferences(
        private val delegate: SharedPreferences = mockk(relaxed = true),
    ) : SharedPreferences by delegate {

        val values = linkedMapOf<String, String>()

        override fun getString(key: String, defValue: String?): String? = values[key] ?: defValue

        override fun getAll(): Map<String, *> = values.toMap()

        override fun edit(): SharedPreferences.Editor = Editor()

        private inner class Editor(
            private val editorDelegate: SharedPreferences.Editor = mockk(relaxed = true),
        ) : SharedPreferences.Editor by editorDelegate {

            private val pending = mutableMapOf<String, String?>()

            override fun putString(key: String, value: String?): SharedPreferences.Editor =
                apply { pending[key] = value }

            override fun remove(key: String): SharedPreferences.Editor = apply { pending[key] = null }

            override fun commit(): Boolean {
                pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                return true
            }
        }
    }

    private companion object {
        const val URL_A = "http://192.168.1.20:8080/stream"
        const val URL_B = "http://192.168.1.21:8080/stream"
    }
}
