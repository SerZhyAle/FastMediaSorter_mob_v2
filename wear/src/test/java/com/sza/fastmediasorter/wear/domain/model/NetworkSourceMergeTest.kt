package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * S1734: the merge rule is the defect, so it is tested where it can be tested - as a pure decision,
 * not through the preferences store the repository writes to.
 *
 * The captured symptom was fifteen pushed sources becoming ten stored ones, and counts that never
 * converged afterwards. Both halves are pinned here.
 */
class NetworkSourceMergeTest {

    private fun source(
        id: String,
        server: String = "192.168.1.100",
        shareName: String? = "Common",
        basePath: String = "/"
    ) = NetworkSource(
        id = id,
        type = NetworkSourceType.SMB,
        name = "n-$id",
        server = server,
        port = 445,
        username = "user",
        password = "pw",
        shareName = shareName,
        basePath = basePath
    )

    @Test
    fun `two folders of one share are two sources, not one`() {
        val stored = listOf(source(id = "phone-a", basePath = "/mark common"))
        val incoming = source(id = "phone-b", basePath = "/Common")

        assertEquals(
            "a different folder on the same share must not match an existing row",
            -1,
            NetworkSourceMerge.indexOfMatch(stored, incoming)
        )
    }

    @Test
    fun `the same phone resource matches its stored row by id`() {
        val stored = listOf(source(id = "phone-a", basePath = "/one"), source(id = "phone-b", basePath = "/two"))
        val incoming = source(id = "phone-b", basePath = "/two")

        assertEquals(1, NetworkSourceMerge.indexOfMatch(stored, incoming))
    }

    @Test
    fun `an id match wins even when the connection details changed`() {
        val stored = listOf(source(id = "phone-a", server = "192.168.1.100"))
        val incoming = source(id = "phone-a", server = "10.0.0.5")

        assertEquals(
            "the phone renamed or moved the resource; it is still the same resource",
            0,
            NetworkSourceMerge.indexOfMatch(stored, incoming)
        )
    }

    @Test
    fun `a watch-local source is not overwritten by a different phone resource`() {
        val stored = listOf(source(id = "watch-local", basePath = "/local only"))
        val incoming = source(id = "phone-a", basePath = "/something else")

        assertEquals(-1, NetworkSourceMerge.indexOfMatch(stored, incoming))
    }

    @Test
    fun `a watch-local source describing the very same folder is matched by the tuple`() {
        val stored = listOf(source(id = "watch-local", basePath = "/shared"))
        val incoming = source(id = "phone-a", basePath = "/shared")

        val index = NetworkSourceMerge.indexOfMatch(stored, incoming)

        assertEquals("the same folder must not become a duplicate row", 0, index)
        assertNotEquals(
            "the fallback must let the incoming id replace the local one, or the next sync " +
                "counts it as new again",
            stored[index].id,
            incoming.id
        )
    }

    private fun folder(id: String, server: String, basePath: String, fingerprint: String? = "SHA256:abc") =
        NetworkSource(
            id = id,
            type = NetworkSourceType.SFTP,
            name = "n-$id",
            server = server,
            port = 2222,
            username = "user",
            password = "pw",
            basePath = basePath,
            hostKeyFingerprint = fingerprint
        )

    @Test
    fun `one folder that arrived under two ids is one record`() {
        val stored = listOf(folder(id = "watch-made", server = "10.0.0.20", basePath = "/Music"))
        val incoming = folder(id = "17", server = "192.168.1.20", basePath = "/Music/")

        assertEquals(0, NetworkSourceMerge.indexOfMatch(stored, incoming))
    }

    @Test
    fun `two roots behind one host key stay two records`() {
        val stored = listOf(folder(id = "watch-made", server = "10.0.0.20", basePath = "/Music"))
        val incoming = folder(id = "17", server = "192.168.1.20", basePath = "/Photos")

        assertEquals(-1, NetworkSourceMerge.indexOfMatch(stored, incoming))
    }

    @Test
    fun `a folder without a host key is never matched by identity`() {
        val stored = listOf(folder(id = "watch-made", server = "10.0.0.20", basePath = "/Music", fingerprint = null))
        val incoming = folder(id = "17", server = "192.168.1.20", basePath = "/Music", fingerprint = null)

        assertEquals(-1, NetworkSourceMerge.indexOfMatch(stored, incoming))
    }
}
