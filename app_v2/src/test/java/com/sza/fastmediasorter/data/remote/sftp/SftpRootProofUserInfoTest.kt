package com.sza.fastmediasorter.data.remote.sftp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SftpRootProofUserInfoTest {

    private val proof = SftpRootProof("Music", "4711")

    @Test
    fun `three hidden fields are answered root, password, PIN in that order`() {
        val answers = SftpRootProofUserInfo.answers(proof, "shared", 3, booleanArrayOf(false, false, false))

        assertArrayEquals(arrayOf("Music", "shared", "4711"), answers)
    }

    @Test
    fun `any other shape of the exchange is not answered`() {
        assertNull(SftpRootProofUserInfo.answers(proof, "shared", 2, booleanArrayOf(false, false)))
        assertNull(SftpRootProofUserInfo.answers(proof, "shared", 4, booleanArrayOf(false, false, false, false)))
        assertNull(SftpRootProofUserInfo.answers(proof, "shared", 3, booleanArrayOf(false, true, false)))
        assertNull(SftpRootProofUserInfo.answers(proof, "shared", 3, null))
        assertNull(SftpRootProofUserInfo.answers(proof, "shared", 0, booleanArrayOf()))
    }

    @Test
    fun `the interactive callback delegates to the same rule`() {
        val info = SftpRootProofUserInfo(proof, "shared")

        val answers = info.promptKeyboardInteractive(
            "host",
            "",
            "",
            arrayOf("Resource", "Password", "PIN"),
            booleanArrayOf(false, false, false),
        )

        assertEquals(listOf("Music", "shared", "4711"), answers?.toList())
        assertNull(info.promptKeyboardInteractive("host", "", "", arrayOf("Password:"), booleanArrayOf(false)))
    }
}
