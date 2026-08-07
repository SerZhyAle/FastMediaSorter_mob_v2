package com.sza.fastmediasorter.ui.player.dispatch

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S1455: the office-format half of [MediaFamilyResolverTest], split out because it is the only part
 * that depends on which flavor compiled it. `MediaTypeUtils` builds its office mime/extension sets from
 * `OfficeDocumentFamilyCatalog`, an object with one name and six per-flavor copies - empty on lite and
 * photos. Asserting these formats in the shared `src/test` therefore failed on precisely the builds that
 * are specified not to open them.
 *
 * The class name deliberately differs from the shared one: AGP merges `src/test` with every mounted set
 * into a single compilation, so a second file under the same FQCN would be a duplicate-class error.
 */
class MediaFamilyResolverDocumentsTest {

    @Test
    fun `office documents resolve to the document family where the flavor ships them`() {
        assertEquals(MediaFamily.DOCUMENT, MediaFamilyResolver.resolve("application/msword", "test.doc"))
        assertEquals(MediaFamily.DOCUMENT, MediaFamilyResolver.resolve("text/rtf", "test.rtf"))
    }

    @Test
    fun `an office extension resolves with no mime type supplied`() {
        // The dispatcher often gets a bare file name from an external intent, so the extension fallback
        // in MediaTypeUtils has to reach the same catalog the mime branch does.
        assertEquals(MediaFamily.DOCUMENT, MediaFamilyResolver.resolve(null, "test.doc"))
    }
}
