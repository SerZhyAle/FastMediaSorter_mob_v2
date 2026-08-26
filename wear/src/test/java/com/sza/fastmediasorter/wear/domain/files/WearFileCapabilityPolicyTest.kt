package com.sza.fastmediasorter.wear.domain.files

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationKind
import com.sza.fastmediasorter.wear.domain.model.WearFileStorageClass
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId

/**
 * Pins the capability table of S1863 research artifact 04 and the mirrored suffix rule, because both
 * are duplicated knowledge - the table restates a scoped-storage constraint and the resolver restates
 * a class the `wear` module cannot import.
 */
class WearFileCapabilityPolicyTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val policy = WearFileCapabilityPolicy(mockk<Context>(relaxed = true))

    @Test
    fun `network files are offered nothing`() {
        assertEquals(emptySet<WearFileOperationKind>(), policy.allowedOperations(WearFileStorageClass.NETWORK))
    }

    @Test
    fun `media store files are offered send to phone only`() {
        assertEquals(
            setOf(WearFileOperationKind.SEND_TO_PHONE),
            policy.allowedOperations(WearFileStorageClass.MEDIA_STORE)
        )
    }

    @Test
    fun `app owned files are offered every operation`() {
        assertEquals(
            WearFileOperationKind.entries.toSet(),
            policy.allowedOperations(WearFileStorageClass.APP_OWNED)
        )
    }

    @Test
    fun `a file in the internal cache is app owned`() {
        val dirs = appDirs()
        val cached = File(dirs.cache, "preview.jpg")

        assertEquals(
            WearFileStorageClass.APP_OWNED,
            policyFor(dirs).classify(mediaFile(cached), isNetworkSource = false)
        )
    }

    @Test
    fun `a file in the internal files directory is app owned`() {
        val dirs = appDirs()
        val stored = File(dirs.files, "note.txt")

        assertEquals(
            WearFileStorageClass.APP_OWNED,
            policyFor(dirs).classify(mediaFile(stored), isNetworkSource = false)
        )
    }

    @Test
    fun `a file in the external sandbox is app owned`() {
        val dirs = appDirs()
        val sandboxed = File(dirs.externalFiles, "sent.mp4")

        assertEquals(
            WearFileStorageClass.APP_OWNED,
            policyFor(dirs).classify(mediaFile(sandboxed), isNetworkSource = false)
        )
    }

    @Test
    fun `a file outside every app directory is media store`() {
        val dirs = appDirs()
        val foreign = temporaryFolder.newFolder("camera")

        assertEquals(
            WearFileStorageClass.MEDIA_STORE,
            policyFor(dirs).classify(mediaFile(File(foreign, "shot.jpg")), isNetworkSource = false)
        )
    }

    @Test
    fun `a caller-declared network entry stays network wherever it points`() {
        val dirs = appDirs()
        val cached = File(dirs.cache, "share.mp3")

        assertEquals(
            WearFileStorageClass.NETWORK,
            policyFor(dirs).classify(mediaFile(cached), isNetworkSource = true)
        )
    }

    @Test
    fun `suffix lands before the extension`() {
        val suffixed = WearFileNameConflictResolver.applySecondsSuffix(
            originalName = "note.txt",
            now = FIXED_SECONDS_MILLIS,
            zone = ZoneId.of("UTC")
        )

        assertEquals("note-42.txt", suffixed)
    }

    @Test
    fun `an extensionless name is suffixed at its end`() {
        val suffixed = WearFileNameConflictResolver.applySecondsSuffix(
            originalName = "note",
            now = FIXED_SECONDS_MILLIS,
            zone = ZoneId.of("UTC")
        )

        assertEquals("note-42", suffixed)
    }

    @Test
    fun `a free name is returned untouched`() {
        val (name, renamed) = WearFileNameConflictResolver.resolveLocal(temporaryFolder.root, "note.txt")

        assertEquals("note.txt", name)
        assertTrue(!renamed)
    }

    @Test
    fun `an occupied name is renamed and reported as renamed`() {
        temporaryFolder.newFile("note.txt")

        val (name, renamed) = WearFileNameConflictResolver.resolveLocal(temporaryFolder.root, "note.txt")

        assertTrue(renamed)
        assertTrue(name.matches(Regex("""note-\d{2}\.txt""")))
    }

    /**
     * S1863: renaming a whole selection resolves several files inside one second, so the seconds
     * suffix alone repeats. `File.renameTo` replaces its destination instead of failing, so a
     * repeated answer here destroyed the file that got there first.
     */
    @Test
    fun `a name taken even after the seconds suffix resolves to a free one`() {
        temporaryFolder.newFile("note.txt")
        val suffixed = WearFileNameConflictResolver.applySecondsSuffix("note.txt")
        temporaryFolder.newFile(suffixed)

        val (name, renamed) = WearFileNameConflictResolver.resolveLocal(temporaryFolder.root, "note.txt")

        assertTrue(renamed)
        assertTrue(!File(temporaryFolder.root, name).exists())
    }

    @Test
    fun `every file of a batch renamed to one name gets a distinct free name`() {
        val taken = mutableSetOf<String>()

        repeat(BATCH_SIZE) {
            val (name, _) = WearFileNameConflictResolver.resolveLocal(temporaryFolder.root, "note.txt")
            assertTrue("resolveLocal returned an occupied name: $name", taken.add(name))
            temporaryFolder.newFile(name)
        }

        assertEquals(BATCH_SIZE, taken.size)
    }

    private class AppDirs(val cache: File, val files: File, val externalFiles: File)

    /** Real directories rather than mocks: `classify` canonicalises each root before comparing. */
    private fun appDirs(): AppDirs {
        val sandbox = temporaryFolder.newFolder("sandbox")
        return AppDirs(
            cache = temporaryFolder.newFolder("cache"),
            files = temporaryFolder.newFolder("files"),
            externalFiles = File(sandbox, "files").apply { mkdirs() }
        )
    }

    private fun policyFor(dirs: AppDirs): WearFileCapabilityPolicy {
        val context = mockk<Context>()
        every { context.cacheDir } returns dirs.cache
        every { context.filesDir } returns dirs.files
        every { context.getExternalFilesDir(null) } returns dirs.externalFiles
        return WearFileCapabilityPolicy(context)
    }

    /** A file URI mocked rather than parsed: `Uri.parse` is not available to a plain JVM test. */
    private fun mediaFile(file: File): WearMediaFile {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "file"
        every { uri.path } returns file.absolutePath
        return WearMediaFile(
            id = file.name.hashCode().toLong(),
            name = file.name,
            uri = uri,
            mimeType = null,
            size = 0L,
            dateModified = 0L
        )
    }

    private companion object {
        /** 42 seconds past the epoch, so the seconds component of the suffix is known and stable. */
        const val FIXED_SECONDS_MILLIS = 42_000L

        /** Enough files to force the seconds suffix to repeat within one run. */
        const val BATCH_SIZE = 5
    }
}
