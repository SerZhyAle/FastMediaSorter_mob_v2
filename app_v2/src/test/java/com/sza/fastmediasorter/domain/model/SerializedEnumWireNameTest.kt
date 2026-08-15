package com.sza.fastmediasorter.domain.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S1661: these four enums reach storage that outlives the process - the parked transfer request, every cached
 * file list row, and the command sent to the watch. Gson writes an enum as the constant, never as the property
 * that holds it, so a keep rule or a @SerializedName on the owning model protects the surrounding keys and
 * leaves the value between them renameable. These tests are what makes that break loudly instead of silently:
 * a dropped annotation turns a stored MOVE into an unparsable value rather than into an error anyone reads.
 *
 * Constants are reached by name through getField rather than by walking declaredFields, which would also see
 * the compiler-generated statics that are not enum constants at all.
 */
class SerializedEnumWireNameTest {

    private val gson = Gson()

    @Test
    fun `every constant of every persisted enum pins its wire name`() {
        val unpinned = unpinnedConstants(FileOperationType::class.java) +
            unpinnedConstants(MediaType::class.java) +
            unpinnedConstants(MetadataState::class.java) +
            unpinnedConstants(WearPlaybackCommand::class.java)

        assertEquals("constants without @SerializedName", emptyList<String>(), unpinned)
    }

    @Test
    fun `the pinned wire name repeats the constant name so stored JSON keeps parsing`() {
        val drifted = driftedConstants(FileOperationType::class.java) +
            driftedConstants(MediaType::class.java) +
            driftedConstants(MetadataState::class.java) +
            driftedConstants(WearPlaybackCommand::class.java)

        assertEquals("constants whose wire name left the constant name", emptyList<String>(), drifted)
    }

    @Test
    fun `a constant of each persisted enum serializes to its documented literal`() {
        assertEquals(""""MOVE"""", gson.toJson(FileOperationType.MOVE))
        assertEquals(""""BINARY_ARCHIVE"""", gson.toJson(MediaType.BINARY_ARCHIVE))
        assertEquals(""""PARTIAL"""", gson.toJson(MetadataState.PARTIAL))
        assertEquals(""""PLAY_PAUSE"""", gson.toJson(WearPlaybackCommand.PLAY_PAUSE))
    }

    @Test
    fun `every constant round trips through Gson`() {
        assertRoundTrip(FileOperationType::class.java)
        assertRoundTrip(MediaType::class.java)
        assertRoundTrip(MetadataState::class.java)
        assertRoundTrip(WearPlaybackCommand::class.java)
    }

    @Test
    fun `a value written in the current format still parses`() {
        // Written by hand rather than by toJson on purpose: a literal is what a previous release parked on
        // disk, so it keeps proving compatibility even if the writer changes.
        assertEquals(FileOperationType.ARCHIVE, gson.fromJson(""""ARCHIVE"""", FileOperationType::class.java))
        assertEquals(MediaType.OFFICE_DOCUMENT, gson.fromJson(""""OFFICE_DOCUMENT"""", MediaType::class.java))
        assertEquals(MetadataState.BROKEN, gson.fromJson(""""BROKEN"""", MetadataState::class.java))
        assertEquals(
            WearPlaybackCommand.PREVIOUS,
            gson.fromJson(""""PREVIOUS"""", WearPlaybackCommand::class.java)
        )
    }

    @Test
    fun `a cached file list row written in the current format still parses`() {
        val stored = """{"name":"clip.mp4","path":"/movies/clip.mp4","type":"VIDEO",""" +
            """"size":42,"createdDate":1700000000000,"metadataState":"PARTIAL"}"""

        val restored = gson.fromJson(stored, MediaFile::class.java)

        assertEquals(MediaType.VIDEO, restored.type)
        assertEquals(MetadataState.PARTIAL, restored.metadataState)
    }

    // getEnumConstants is nullable for a non-enum class, which none of the callers can pass; the check keeps
    // that impossible case loud instead of turning it into an empty list that passes every assertion here.
    private fun <T : Enum<T>> constantsOf(type: Class<T>): List<T> =
        checkNotNull(type.enumConstants) { "${type.simpleName} exposes no enum constants" }.toList()

    private fun <T : Enum<T>> unpinnedConstants(type: Class<T>): List<String> =
        constantsOf(type)
            .filter { wireNameOf(type, it) == null }
            .map { "${type.simpleName}.${it.name}" }

    private fun <T : Enum<T>> driftedConstants(type: Class<T>): List<String> =
        constantsOf(type)
            .filter { wireNameOf(type, it) != null && wireNameOf(type, it) != it.name }
            .map { "${type.simpleName}.${it.name} -> ${wireNameOf(type, it)}" }

    private fun <T : Enum<T>> assertRoundTrip(type: Class<T>) {
        for (constant in constantsOf(type)) {
            assertEquals(constant, gson.fromJson(gson.toJson(constant), type))
        }
    }

    private fun <T : Enum<T>> wireNameOf(type: Class<T>, constant: T): String? =
        type.getField(constant.name).getAnnotation(SerializedName::class.java)?.value
}
