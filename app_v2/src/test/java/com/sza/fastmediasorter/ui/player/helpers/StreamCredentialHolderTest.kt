package com.sza.fastmediasorter.ui.player.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamCredentialHolderTest {

    private val creds = StreamCredentials("user", "secret", "WORKGROUP", 445)

    @Test
    fun `returns the credentials stored for the same uri`() {
        val holder = StreamCredentialHolder()
        holder.put("smb://nas/music/a.mp3", creds)

        assertEquals(creds, holder.get("smb://nas/music/a.mp3"))
    }

    @Test
    fun `returns null for an address nothing was stored for`() {
        val holder = StreamCredentialHolder()
        holder.put("smb://nas/music/a.mp3", creds)

        assertNull(holder.get("smb://nas/music/b.mp3"))
    }

    @Test
    fun `evicts the least recently used entry past capacity`() {
        val holder = StreamCredentialHolder()
        holder.put("sftp://host/0.mp3", creds)
        (1..StreamCredentialHolder.CAPACITY).forEach { holder.put("sftp://host/$it.mp3", creds) }

        assertNull(holder.get("sftp://host/0.mp3"))
        assertEquals(creds, holder.get("sftp://host/${StreamCredentialHolder.CAPACITY}.mp3"))
    }
}
