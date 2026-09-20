package com.sza.fastmediasorter.wear.domain.files

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.wear.data.files.WearWatchFilePublisher
import com.sza.fastmediasorter.wear.data.repository.WearSendToReceiversRepository
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationKind
import com.sza.fastmediasorter.wear.domain.model.WearFileStorageClass
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearSendToReceiverEntry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
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
     * S2004 put opening on the phone here, and S3359 took the two "to phone" errands away: the phone
     * still holds the original, so handing it back there is an errand with nothing to do, and the
     * copy onto the watch takes their place.
     */
    @Test
    fun `a paired phone copy trades the to-phone pair for a copy onto the watch`() {
        val dirs = appDirs()

        assertEquals(
            setOf(
                WearFileOperationKind.DELETE,
                WearFileOperationKind.RENAME,
                WearFileOperationKind.OPEN_ON_PHONE,
                WearFileOperationKind.COPY_TO_WATCH
            ),
            policyFor(dirs).allowedOperations(phoneCopy(dirs, "clip.mp4", VIDEO_MIME_TYPE), isNetworkSource = false)
        )
    }

    /** A document reaches none of the watch's three category lists, so it is offered no copy at all. */
    @Test
    fun `a document copy of a phone file is offered neither direction`() {
        val dirs = appDirs()
        val document = phoneCopy(dirs, "manual.pdf", DOCUMENT_MIME_TYPE)

        assertEquals(
            setOf(
                WearFileOperationKind.DELETE,
                WearFileOperationKind.RENAME,
                WearFileOperationKind.OPEN_ON_PHONE
            ),
            policyFor(dirs).allowedOperations(document, isNetworkSource = false)
        )
    }

    /** An undeclared type names no collection either, so it is withheld exactly as a document is. */
    @Test
    fun `a copy with no declared type is offered no copy onto the watch`() {
        val dirs = appDirs()

        assertTrue(
            WearFileOperationKind.COPY_TO_WATCH !in
                policyFor(dirs).allowedOperations(phoneCopy(dirs, "blob", null), isNetworkSource = false)
        )
    }

    /**
     * The one class that gains an operation rather than trading one: a share allows nothing of its
     * own (S1863), and the copy is about the watch's storage rather than the server's.
     */
    @Test
    fun `a network audio file is offered the copy onto the watch and nothing else`() {
        val dirs = appDirs()
        val remote = mediaFile(File(dirs.cache, "track.mp3"), AUDIO_MIME_TYPE)

        assertEquals(
            setOf(WearFileOperationKind.COPY_TO_WATCH),
            policyFor(dirs).allowedOperations(remote, isNetworkSource = true)
        )
    }

    /**
     * API 28: the publisher cannot insert a row without a storage permission this watch never asks
     * for, so the entry is withheld rather than drawn and refused (S2004 ADR-4).
     */
    @Test
    fun `a watch that cannot publish is offered no copy onto the watch`() {
        val dirs = appDirs()
        val policy = policyFor(dirs, watchAvailable = false)
        val remote = mediaFile(File(dirs.cache, "track.mp3"), AUDIO_MIME_TYPE)

        assertTrue(
            WearFileOperationKind.COPY_TO_WATCH !in
                policy.allowedOperations(phoneCopy(dirs, "clip.mp4", VIDEO_MIME_TYPE), isNetworkSource = false)
        )
        assertEquals(
            emptySet<WearFileOperationKind>(),
            policy.allowedOperations(remote, isNetworkSource = true)
        )
    }

    /**
     * S2142: an entry opening a dialog with nothing in it is the offer-that-ends-in-a-refusal ADR-3
     * forbids, and an empty list is the normal state twice over - before the phone's first push, and
     * after the owner switched the last receiver off there.
     */
    @Test
    fun `send to is withheld while this watch holds no receivers`() {
        WearFileStorageClass.entries.forEach { storageClass ->
            assertTrue(
                "$storageClass offered SEND_TO_RECEIVER with an empty receiver list",
                WearFileOperationKind.SEND_TO_RECEIVER !in
                    policyWith(available = true, receivers = emptyList()).allowedOperations(storageClass)
            )
        }
    }

    /** A network share stays at nothing whatever the phone published (S1863, strategic 11 criterion 12). */
    @Test
    fun `send to reaches every class the watch can read and never the network one`() {
        val policy = policyWith(available = true, receivers = listOf(receiver("email")))

        assertEquals(
            setOf(
                WearFileStorageClass.APP_OWNED,
                WearFileStorageClass.PHONE_COPY,
                WearFileStorageClass.MEDIA_STORE
            ),
            WearFileStorageClass.entries
                .filter { WearFileOperationKind.SEND_TO_RECEIVER in policy.allowedOperations(it) }
                .toSet()
        )
    }

    /**
     * Sending is a read, so it survives the missing write confirmation on the 28-29 band: the
     * confirmation guards writing to someone else's row, and handing the bytes over changes nothing.
     */
    @Test
    fun `send to survives a media store row with no write confirmation`() {
        assertEquals(
            setOf(WearFileOperationKind.SEND_TO_PHONE, WearFileOperationKind.SEND_TO_RECEIVER),
            policyWith(available = false, receivers = listOf(receiver("email")))
                .allowedOperations(WearFileStorageClass.MEDIA_STORE)
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

    /**
     * The whole direction table in one case, because strategic §11 criteria 6, 7 and 9 are statements
     * about every class and every type at once: a later change that drops one cell - a document that
     * starts offering a copy, a phone copy that gets its "to phone" pair back - fails here rather
     * than on the one surface someone happens to open.
     */
    @Test
    fun `the direction table holds for every storage class and every type`() {
        val dirs = appDirs()
        val policy = policyFor(dirs)
        val types = listOf(IMAGE_MIME_TYPE, AUDIO_MIME_TYPE, VIDEO_MIME_TYPE, DOCUMENT_MIME_TYPE)

        WearFileStorageClass.entries.forEach { storageClass ->
            types.forEach { mimeType ->
                val allowed = policy.allowedOperations(
                    fileIn(dirs, storageClass, mimeType),
                    isNetworkSource = storageClass == WearFileStorageClass.NETWORK
                )
                assertDirection("$storageClass/$mimeType", storageClass, mimeType, allowed)
            }
        }
    }

    private fun assertDirection(
        case: String,
        storageClass: WearFileStorageClass,
        mimeType: String,
        allowed: Set<WearFileOperationKind>
    ) {
        assertTrue(
            "$case offered a move onto the watch, which nothing performs yet",
            WearFileOperationKind.MOVE_TO_WATCH !in allowed
        )
        assertEquals(
            "$case answered the wrong way about copying onto the watch",
            storageClass in TO_WATCH_CLASSES && mimeType != DOCUMENT_MIME_TYPE,
            WearFileOperationKind.COPY_TO_WATCH in allowed
        )
        if (storageClass == WearFileStorageClass.PHONE_COPY) {
            assertTrue(
                "$case offered back to the phone the file the phone still holds",
                allowed.none { it in TO_PHONE_KINDS }
            )
        } else if (storageClass != WearFileStorageClass.NETWORK) {
            assertTrue(
                "$case stopped offering the watch's own file to the phone",
                WearFileOperationKind.SEND_TO_PHONE in allowed
            )
        }
    }

    /** A path the classifier reads as [storageClass]: the directory is what decides the class. */
    private fun fileIn(
        dirs: AppDirs,
        storageClass: WearFileStorageClass,
        mimeType: String
    ): WearMediaFile = when (storageClass) {
        WearFileStorageClass.APP_OWNED -> mediaFile(File(dirs.cache, TABLE_FILE_NAME), mimeType)
        WearFileStorageClass.PHONE_COPY -> phoneCopy(dirs, TABLE_FILE_NAME, mimeType)
        WearFileStorageClass.MEDIA_STORE ->
            mediaFile(File(temporaryFolder.root, "camera/$TABLE_FILE_NAME"), mimeType)
        // The caller declares this one, so the path it happens to point at changes nothing.
        WearFileStorageClass.NETWORK -> mediaFile(File(dirs.cache, TABLE_FILE_NAME), mimeType)
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

    private fun policyFor(dirs: AppDirs, watchAvailable: Boolean = true): WearFileCapabilityPolicy {
        val context = mockk<Context>()
        every { context.cacheDir } returns dirs.cache
        every { context.filesDir } returns dirs.files
        every { context.getExternalFilesDir(null) } returns dirs.externalFiles
        return WearFileCapabilityPolicy(
            context,
            consentThatIs(available = false),
            receiversThatAre(emptyList()),
            publisherThatIs(available = watchAvailable)
        )
    }

    /** Classification never consults the confirmation, so these cases fix it either way. */
    private fun policyWithConsent(available: Boolean): WearFileCapabilityPolicy =
        policyWith(available, receivers = emptyList())

    private fun policyWith(
        available: Boolean,
        receivers: List<WearSendToReceiverEntry>
    ): WearFileCapabilityPolicy = WearFileCapabilityPolicy(
        mockk<Context>(relaxed = true),
        consentThatIs(available),
        receiversThatAre(receivers),
        publisherThatIs(available = true)
    )

    private fun consentThatIs(available: Boolean): WearMediaStoreConsent {
        val consent = mockk<WearMediaStoreConsent>()
        every { consent.isAvailable() } returns available
        return consent
    }

    private fun receiversThatAre(entries: List<WearSendToReceiverEntry>): WearSendToReceiversRepository {
        val repository = mockk<WearSendToReceiversRepository>()
        every { repository.observe() } returns MutableStateFlow(entries)
        return repository
    }

    private fun receiver(id: String) = WearSendToReceiverEntry(id = id, title = id)

    private fun publisherThatIs(available: Boolean): WearWatchFilePublisher {
        val publisher = mockk<WearWatchFilePublisher>()
        every { publisher.isAvailable() } returns available
        return publisher
    }

    /** A path under the one directory the phone browser writes to, which is what makes it a phone copy. */
    private fun phoneCopy(dirs: AppDirs, name: String, mimeType: String?): WearMediaFile =
        mediaFile(File(File(dirs.cache, WEAR_PHONE_FILE_CACHE_DIR), name), mimeType)

    /** A file URI mocked rather than parsed: `Uri.parse` is not available to a plain JVM test. */
    private fun mediaFile(file: File, mimeType: String? = null): WearMediaFile {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "file"
        every { uri.path } returns file.absolutePath
        return WearMediaFile(
            id = file.name.hashCode().toLong(),
            name = file.name,
            uri = uri,
            mimeType = mimeType,
            size = 0L,
            dateModified = 0L
        )
    }

    private companion object {
        /** 42 seconds past the epoch, so the seconds component of the suffix is known and stable. */
        const val FIXED_SECONDS_MILLIS = 42_000L

        /** Enough files to force the seconds suffix to repeat within one run. */
        const val BATCH_SIZE = 5

        const val AUDIO_MIME_TYPE = "audio/mpeg"
        const val VIDEO_MIME_TYPE = "video/mp4"
        const val IMAGE_MIME_TYPE = "image/jpeg"
        const val DOCUMENT_MIME_TYPE = "application/pdf"

        /** One name for every cell of the table: the directory decides the class, never the name. */
        const val TABLE_FILE_NAME = "item.bin"

        /** The two classes whose original lives somewhere other than this watch. */
        val TO_WATCH_CLASSES = setOf(WearFileStorageClass.PHONE_COPY, WearFileStorageClass.NETWORK)

        val TO_PHONE_KINDS = setOf(
            WearFileOperationKind.SEND_TO_PHONE,
            WearFileOperationKind.MOVE_TO_PHONE
        )
    }
}
