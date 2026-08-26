package com.sza.fastmediasorter.wear.domain.files

import android.content.Context
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationKind
import com.sza.fastmediasorter.wear.domain.model.WearFileStorageClass
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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

    private companion object {
        /** 42 seconds past the epoch, so the seconds component of the suffix is known and stable. */
        const val FIXED_SECONDS_MILLIS = 42_000L
    }
}
