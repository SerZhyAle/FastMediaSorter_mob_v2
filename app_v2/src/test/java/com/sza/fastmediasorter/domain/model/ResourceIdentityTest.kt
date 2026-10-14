package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResourceIdentityTest {

    @Test
    fun `the same key and root on different hosts and ports is one identity`() {
        val lan = sftp("sftp://192.168.1.5:2222/Photos")
        val tunnel = sftp("sftp://AAAAAAAAAAAAAAAAAAAAAA@exchange.example:7443/Photos/")

        assertEquals(ResourceIdentity.of(lan), ResourceIdentity.of(tunnel))
        assertEquals(ResourceIdentity.of(lan), ResourceIdentity.of(FINGERPRINT, "Photos"))
    }

    @Test
    fun `a different root is a different resource`() {
        assertNotEquals(
            ResourceIdentity.of(sftp("sftp://192.168.1.5:2222/Photos")),
            ResourceIdentity.of(sftp("sftp://192.168.1.5:2222/Music")),
        )
    }

    @Test
    fun `an empty root and the server root are the same`() {
        assertEquals(ResourceIdentity.of(FINGERPRINT, null), ResourceIdentity.of(sftp("sftp://192.168.1.5:2222/")))
        assertEquals("/", ResourceIdentity.of(FINGERPRINT, "")?.root)
    }

    @Test
    fun `no fingerprint or a non SFTP resource has no identity`() {
        assertNull(ResourceIdentity.of(sftp("sftp://192.168.1.5:2222/Photos").copy(hostKeyFingerprint = null)))
        assertNull(ResourceIdentity.of(sftp("smb://192.168.1.5/Photos").copy(type = ResourceType.SMB)))
    }

    private fun sftp(path: String) = MediaResource(
        name = "Photos",
        path = path,
        type = ResourceType.SFTP,
        hostKeyFingerprint = FINGERPRINT,
    )

    private companion object {
        const val FINGERPRINT = "SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU"
    }
}
