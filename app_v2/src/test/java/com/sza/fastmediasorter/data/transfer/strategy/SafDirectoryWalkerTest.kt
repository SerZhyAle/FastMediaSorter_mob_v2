package com.sza.fastmediasorter.data.transfer.strategy

import android.content.ContentResolver
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S1378: traversal contract of [SafDirectoryWalker].
 *
 * The tree is served by a fake `ContentResolver` that answers a children query per parent document
 * id, so the assertions are about the walk itself and not about a device's document provider.
 *
 * Robolectric because `DocumentsContract` URI building and `MatrixCursor` are framework code.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class SafDirectoryWalkerTest {

    private val contentResolver: ContentResolver = mockk()
    private val walker = SafDirectoryWalker(contentResolver)

    /**
     * root/
     *   a.txt
     *   sub/
     *     b.txt
     *     deeper/
     *       c.txt
     */
    private val tree = mapOf(
        "root" to listOf(child("a.txt", isDir = false, size = 10), child("sub", isDir = true)),
        "sub" to listOf(child("b.txt", isDir = false, size = 20), child("deeper", isDir = true)),
        "deeper" to listOf(child("c.txt", isDir = false, size = 30)),
    )

    private fun child(name: String, isDir: Boolean, size: Long = 0L) =
        arrayOf<Any>(name, name, if (isDir) DocumentsContract.Document.MIME_TYPE_DIR else "text/plain", size)

    private fun rootUri(): Uri = DocumentsContract.buildDocumentUriUsingTree(
        Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADCIM"),
        "root"
    )

    private fun serveTree() {
        every { contentResolver.query(any(), any(), any(), any(), any()) } answers {
            val queried = firstArg<Uri>()
            // buildChildDocumentsUriUsingTree ends the path with ../document/<parentId>/children,
            // so the level being asked for is the segment before the trailing "children".
            val segments = queried.pathSegments
            val parentId = segments.getOrNull(segments.lastIndex - 1).orEmpty()
            val cursor = MatrixCursor(
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                )
            )
            tree[parentId].orEmpty().forEach { cursor.addRow(it) }
            cursor
        }
    }

    @Test
    fun `walk yields a nested tree parents-first with relative paths`() = runTest {
        serveTree()
        val seen = mutableListOf<String>()

        walker.walk(rootUri()) { seen += it.relativePath }

        assertEquals(listOf("a.txt", "sub", "sub/b.txt", "sub/deeper", "sub/deeper/c.txt"), seen)
    }

    @Test
    fun `leaves-first walk reverses only the parent ordering`() = runTest {
        serveTree()
        val seen = mutableListOf<String>()

        walker.walkDepthFirstLeavesFirst(rootUri()) { seen += it.relativePath }

        // Every directory must appear after everything it contains - what a recursive delete needs.
        assertTrue(seen.indexOf("sub/deeper/c.txt") < seen.indexOf("sub/deeper"))
        assertTrue(seen.indexOf("sub/deeper") < seen.indexOf("sub"))
        assertTrue(seen.indexOf("sub/b.txt") < seen.indexOf("sub"))
    }

    @Test
    fun `measure counts every entry and sums only file bytes`() = runTest {
        serveTree()

        val totals = walker.measure(rootUri())

        assertEquals("three files and two directories", 5, totals.entries)
        assertEquals(60L, totals.bytes)
    }

    @Test
    fun `directories report no size of their own`() = runTest {
        serveTree()
        val sizes = mutableMapOf<String, Long>()

        walker.walk(rootUri()) { sizes[it.relativePath] = it.size }

        assertEquals(0L, sizes["sub"])
        assertEquals(10L, sizes["a.txt"])
    }

    /**
     * The walk must stop inside the tree, not after it: leaving the screen during a large folder
     * operation is the case strategic §3.2 budgets for. This asserts propagation - that a
     * cancellation raised mid-walk is neither swallowed nor deferred to the end of the tree. The
     * `ensureActive()` call that makes an *externally* cancelled scope stop is checked by Step 04.1's
     * grep predicate; asserting it here would test the coroutines runtime rather than this walker.
     */
    @Test
    fun `a cancellation raised mid-walk stops it there`() = runTest {
        serveTree()
        val seen = mutableListOf<String>()

        val thrown = runCatching {
            walker.walk(rootUri()) { entry ->
                seen += entry.relativePath
                if (seen.size == 2) throw CancellationException("left the screen")
            }
        }.exceptionOrNull()

        assertTrue("cancellation must propagate, not be swallowed", thrown is CancellationException)
        assertEquals("the walk stops where it was cancelled", 2, seen.size)
    }
}
