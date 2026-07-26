package com.sza.fastmediasorter.domain.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * S1200: the payload stamp is what decides whether an installed copy is the one this build pins, so
 * it must depend on the pins and on nothing else - not on the order the files happen to be listed in.
 */
class DeliverableSourceDescriptorStampTest {

    private fun file(name: String, sha: String, size: Long = 100L) =
        PayloadFile(fileName = name, sources = listOf("https://mirror/$name"), sha256 = sha, minSize = size)

    private fun descriptor(vararg files: PayloadFile) =
        DeliverableSourceDescriptor(set = DeliverableSet.CHANNEL_PREVIEW_ATLAS, files = files.toList())

    @Test
    fun `file order does not change the stamp`() {
        val a = file("atlas.webp", "aaa")
        val b = file("coords.json", "bbb")

        assertEquals(descriptor(a, b).stamp, descriptor(b, a).stamp)
    }

    @Test
    fun `a changed hash changes the stamp`() {
        val before = descriptor(file("atlas.webp", "aaa"), file("coords.json", "bbb"))
        val after = descriptor(file("atlas.webp", "ccc"), file("coords.json", "bbb"))

        assertNotEquals(before.stamp, after.stamp)
    }

    @Test
    fun `an added file changes the stamp`() {
        val before = descriptor(file("atlas.webp", "aaa"))
        val after = descriptor(file("atlas.webp", "aaa"), file("coords.json", "bbb"))

        assertNotEquals(before.stamp, after.stamp)
    }

    @Test
    fun `unhashed files fall back to size instead of colliding on an empty hash`() {
        // Set C ships pure-resource files with a blank sha256; without the size fallback every such
        // file would stamp identically and a replaced payload would read as unchanged.
        val before = descriptor(file("clip.mp4", sha = "", size = 1_000L))
        val after = descriptor(file("clip.mp4", sha = "", size = 2_000L))

        assertNotEquals(before.stamp, after.stamp)
    }

    @Test
    fun `sources are not part of the identity`() {
        // A mirror may legitimately move; that is not a different payload.
        val one = DeliverableSourceDescriptor(
            set = DeliverableSet.CHANNEL_PREVIEW_ATLAS,
            files = listOf(PayloadFile("atlas.webp", listOf("https://a/x"), "aaa", 100L))
        )
        val two = DeliverableSourceDescriptor(
            set = DeliverableSet.CHANNEL_PREVIEW_ATLAS,
            files = listOf(PayloadFile("atlas.webp", listOf("https://b/y"), "aaa", 100L))
        )

        assertEquals(one.stamp, two.stamp)
    }
}
