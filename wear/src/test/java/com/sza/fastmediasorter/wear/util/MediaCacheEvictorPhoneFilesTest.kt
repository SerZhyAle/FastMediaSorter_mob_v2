package com.sza.fastmediasorter.wear.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * S2004: pins both halves of the temporary-copy contract for the paired-phone cache - the directory
 * stays under its cap after a run of openings, and the file just fetched is never the one evicted.
 *
 * A real temporary directory rather than a mocked file system: the evictor reads `length()` and
 * `lastModified()` off disk, so a mock would prove the test's arithmetic and not the evictor's.
 */
class MediaCacheEvictorPhoneFilesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `a directory already under the cap is left untouched`() {
        val kept = writeFile("first.bin", FILE_BYTES)
        val other = writeFile("second.bin", FILE_BYTES)

        MediaCacheEvictor.evictOldestUntilUnderCap(temporaryFolder.root, keep = kept, capBytes = CAP_BYTES)

        assertTrue("nothing may be removed below the cap", kept.exists() && other.exists())
    }

    @Test
    fun `writing past the cap leaves the directory at or under it`() {
        val newest = fillPastCap()

        MediaCacheEvictor.evictOldestUntilUnderCap(temporaryFolder.root, keep = newest, capBytes = CAP_BYTES)

        assertTrue("directory still over the cap", directorySize() <= CAP_BYTES)
    }

    /**
     * The one that matters at any cap: `keep` is the copy the user is waiting on, and it is also the
     * newest, so an evictor that only sorted by age would take it first the moment one file alone
     * exceeded the cap.
     */
    @Test
    fun `the file just written survives even when it is the oldest`() {
        val oldest = writeFile("oldest.bin", FILE_BYTES).also { it.setLastModified(OLDEST_STAMP_MILLIS) }
        repeat(FILES_PAST_CAP) { index -> writeFile("later-$index.bin", FILE_BYTES) }

        MediaCacheEvictor.evictOldestUntilUnderCap(temporaryFolder.root, keep = oldest, capBytes = CAP_BYTES)

        assertTrue("the spared file was evicted", oldest.exists())
        assertTrue("directory still over the cap", directorySize() <= CAP_BYTES)
    }

    @Test
    fun `eviction removes the oldest first`() {
        val oldest = writeFile("oldest.bin", FILE_BYTES).also { it.setLastModified(OLDEST_STAMP_MILLIS) }
        val newest = fillPastCap()

        MediaCacheEvictor.evictOldestUntilUnderCap(temporaryFolder.root, keep = newest, capBytes = CAP_BYTES)

        assertEquals("the oldest file must be the first to go", false, oldest.exists())
    }

    /** Writes enough equally sized files to exceed the cap and returns the last one written. */
    private fun fillPastCap(): File {
        var last: File? = null
        repeat(FILES_PAST_CAP) { index -> last = writeFile("copy-$index.bin", FILE_BYTES) }
        return requireNotNull(last)
    }

    private fun writeFile(name: String, bytes: Int): File =
        temporaryFolder.newFile(name).apply { writeBytes(ByteArray(bytes)) }

    private fun directorySize(): Long = temporaryFolder.root.listFiles().orEmpty().sumOf { it.length() }

    private companion object {
        /** Small stand-ins for the real megabyte cap: the evictor compares sizes, not their scale. */
        const val CAP_BYTES = 1000L
        const val FILE_BYTES = 400

        /** Three of [FILE_BYTES] is 1200, which is over [CAP_BYTES] by exactly one file. */
        const val FILES_PAST_CAP = 3

        /** Long before any file this test writes, so age ordering is unambiguous. */
        const val OLDEST_STAMP_MILLIS = 1_000_000L
    }
}
