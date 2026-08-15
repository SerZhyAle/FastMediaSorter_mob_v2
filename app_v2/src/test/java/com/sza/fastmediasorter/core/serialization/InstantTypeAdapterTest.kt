package com.sza.fastmediasorter.core.serialization

import com.google.gson.Gson
import com.sza.fastmediasorter.core.di.RepositoryModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * S1668: [InstantTypeAdapter] exists to take a durable wire format off Gson's reflective walk over
 * `java.time.Instant`'s private fields, which the JVM refuses under JPMS and ART may refuse through its non-SDK
 * interface filter. Replacing it is only safe while the bytes stay identical, because blobs written by earlier
 * builds already sit in the `primary_google_account_v1` encrypted preferences.
 *
 * The compatibility claim is therefore asserted against a bare reflective [Gson] directly rather than by eye, and
 * the fixture Gson is taken from [RepositoryModule.provideGson] itself: replicating the builder here would let
 * the registration be deleted from the module without a single test noticing.
 */
class InstantTypeAdapterTest {

    private val gson = RepositoryModule.provideGson()

    // The format already in users' preferences, and the only reason the test worker opens java.base/java.time.
    private val reflectiveGson = Gson()

    private val instant = Instant.ofEpochSecond(SECONDS, NANOS.toLong())

    @Test
    fun `instant is written as a seconds and nanos object in that order`() {
        assertEquals("""{"seconds":$SECONDS,"nanos":$NANOS}""", gson.toJson(instant))
    }

    @Test
    fun `adapter output is byte identical to the reflective format it replaces`() {
        val fromAdapter = gson.toJson(instant)
        val fromReflection = reflectiveGson.toJson(instant)

        assertEquals("adapter must reproduce the reflective wire format", fromReflection, fromAdapter)
        assertEquals("""{"seconds":$SECONDS,"nanos":$NANOS}""", fromReflection)
    }

    @Test
    fun `round trip preserves both the second and the nano precision`() {
        val decoded = gson.fromJson(gson.toJson(instant), Instant::class.java)

        assertEquals(instant, decoded)
        assertEquals(SECONDS, decoded.epochSecond)
        assertEquals(NANOS.toLong(), decoded.nano.toLong())
    }

    @Test
    fun `an instant stored in the current format decodes to the same value`() {
        val stored = """{"seconds":$SECONDS,"nanos":$NANOS}"""

        assertEquals(instant, gson.fromJson(stored, Instant::class.java))
    }

    @Test
    fun `an empty object decodes to epoch instead of throwing`() {
        assertEquals(Instant.EPOCH, gson.fromJson("{}", Instant::class.java))
    }

    @Test
    fun `a missing field counts as zero`() {
        val withoutNanos = gson.fromJson("""{"seconds":$SECONDS}""", Instant::class.java)
        val withoutSeconds = gson.fromJson("""{"nanos":$NANOS}""", Instant::class.java)

        assertEquals(Instant.ofEpochSecond(SECONDS), withoutNanos)
        assertEquals(Instant.ofEpochSecond(0L, NANOS.toLong()), withoutSeconds)
    }

    @Test
    fun `an unknown field is skipped rather than failing the read`() {
        val stored = """{"seconds":$SECONDS,"nanos":$NANOS,"zone":"UTC"}"""

        assertEquals(instant, gson.fromJson(stored, Instant::class.java))
    }

    @Test
    fun `json null decodes to null and a null instant encodes to json null`() {
        val absent: Instant? = null

        assertNull(gson.fromJson("null", Instant::class.java))
        assertEquals("null", gson.toJson(absent, Instant::class.java))
    }

    private companion object {
        const val SECONDS = 1700000000L
        const val NANOS = 123456789
    }
}
