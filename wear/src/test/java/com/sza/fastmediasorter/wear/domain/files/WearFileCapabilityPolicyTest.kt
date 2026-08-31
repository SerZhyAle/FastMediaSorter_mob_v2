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

    private val policy = policyWithConsent(available = false)

    @Test
    fun `network files are offered nothing`() {
        assertEquals(emptySet<WearFileOperationKind>(), policy.allowedOperations(WearFileStorageClass.NETWORK))
    }

    /**
     * S2142: the API 28-29 band, which the owner's watch (Wear OS 5) cannot reproduce at all. The
     * system has no write confirmation to show there, so the operations that need one are withheld
     * rather than offered and refused - and this test is the only place that behaviour is observable.
     */
    @Test
    fun `media store files are offered send to phone only without a write confirmation`() {
        assertEquals(
            setOf(WearFileOperationKind.SEND_TO_PHONE),
            policy.allowedOperations(WearFileStorageClass.MEDIA_STORE)
        )
    }

    @Test
    fun `media store files are offered every local operation once a write confirmation exists`() {
        assertEquals(
            setOf(
                WearFileOperationKind.SEND_TO_PHONE,
                WearFileOperationKind.MOVE_TO_PHONE,
                WearFileOperationKind.DELETE,
                WearFileOperationKind.RENAME
            ),
            policyWithConsent(available = true).allowedOperations(WearFileStorageClass.MEDIA_STORE)
        )
    }

    /** A read-only share allows nothing whether or not the device can confirm a write (S1863). */
    @Test
    fun `network files are offered nothing even with a write confirmation`() {
        assertEquals(
            emptySet<WearFileOperationKind>(),
            policyWithConsent(available = true).allowedOperations(WearFileStorageClass.NETWORK)
        )
    }

    @Test
    fun `app owned files are offered every local operation`() {
        assertEquals(
            setOf(
                WearFileOperationKind.SEND_TO_PHONE,
                WearFileOperationKind.MOVE_TO_PHONE,
                WearFileOperationKind.DELETE,
                WearFileOperationKind.RENAME
            ),
            policy.allowedOperations(WearFileStorageClass.APP_OWNED)
        )
    }

    /**
     * S2004: the phone still holds the original this copy was fetched from, so this class alone may
     * be asked to open it there - and it keeps everything a watch-owned file allows besides.
     */
    @Test
    fun `a paired phone copy is offered opening on the phone as well`() {
        assertEquals(
            WearFileOperationKind.entries.toSet(),
            policy.allowedOperations(WearFileStorageClass.PHONE_COPY)
        )
    }

    @Test
    fun `a file in the paired phone cache directory is a phone copy`() {
        val dirs = appDirs()
        val fetched = File(File(dirs.cache, WEAR_PHONE_FILE_CACHE_DIR), "clip.mp4")

        assertEquals(
            WearFileStorageClass.PHONE_COPY,
            policyFor(dirs).classify(mediaFile(fetched), isNetworkSource = false)
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
        return WearFileCapabilityPolicy(context, consentThatIs(available = false))
    }

    /** Classification never consults the confirmation, so these cases fix it either way. */
    private fun policyWithConsent(available: Boolean): WearFileCapabilityPolicy =
        WearFileCapabilityPolicy(mockk<Context>(relaxed = true), consentThatIs(available))

    private fun consentThatIs(available: Boolean): WearMediaStoreConsent {
        val consent = mockk<WearMediaStoreConsent>()
        every { consent.isAvailable() } returns available
        return consent
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
