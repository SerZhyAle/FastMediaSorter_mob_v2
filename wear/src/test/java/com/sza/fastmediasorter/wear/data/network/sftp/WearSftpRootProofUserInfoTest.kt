package com.sza.fastmediasorter.wear.data.network.sftp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearSftpRootProofUserInfoTest {

    @Test
    fun `three hidden prompts get root password and pin in order`() {
        val hidden = booleanArrayOf(false, false, false)
        val answers = WearSftpRootProofUserInfo.answers("vault", "secret", "1234", 3, hidden)

        assertArrayEquals(arrayOf("vault", "secret", "1234"), answers)
    }

    @Test
    fun `an echoed prompt is not answered`() {
        assertNull(WearSftpRootProofUserInfo.answers("vault", "secret", "1234", 3, booleanArrayOf(false, true, false)))
    }

    @Test
    fun `a single password prompt is not answered with the pin`() {
        assertNull(WearSftpRootProofUserInfo.answers("vault", "secret", "1234", 1, booleanArrayOf(false)))
    }

    @Test
    fun `missing echo flags are not answered`() {
        assertNull(WearSftpRootProofUserInfo.answers("vault", "secret", "1234", 3, null))
    }
}
