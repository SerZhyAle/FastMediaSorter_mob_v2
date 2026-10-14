package com.sza.fastmediasorter.wear.data.exchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S4228: the `sftp-share` records of a `directory` answer, as the watch reads them for its account list. */
class WearExchangeDirectoryClientTest {

    @Test
    fun `an open and a protected share are read and an unreadable one is dropped alone`() {
        val directory = WearExchangeEnvelope.decode(
            """{"schemaVersion":2,"type":"directory","revision":3,"resources":[""" +
                """{"resourceId":"r-open","kind":"sftp-share","name":"Photos",""" +
                """"access":{"descriptor":"$CODE","root":"/photos/"}},""" +
                """{"resourceId":"r-pin","kind":"sftp-share","name":"Vault","protection":{"scheme":"pin-v1"},""" +
                """"access":{"descriptor":"$CODE","root":"vault"}},""" +
                """{"resourceId":"r-bad","kind":"sftp-share","name":"Broken","access":{"descriptor":"FMSSFTP1:x"}},""" +
                """{"resourceId":"r-future","kind":"sftp-share","name":"Future","protection":{"scheme":"pin-v9"},""" +
                """"access":{"descriptor":"$CODE"}},""" +
                """{"resourceId":"r-other","kind":"smb-share","name":"Other","access":{"descriptor":"$CODE"}}""" +
                """]}"""
        )

        val folders = WearExchangeDirectoryClient.foldersOf(directory)

        assertEquals(listOf("r-open", "r-pin"), folders.map { it.resourceId })
        assertEquals("photos", folders[0].root)
        assertFalse(folders[0].pinProtected)
        assertTrue(folders[1].pinProtected)
        assertEquals("SHA256:abc", folders[1].code.hostKeyFingerprint)
    }

    @Test
    fun `a directory without resources lists nothing`() {
        val directory = WearExchangeEnvelope.decode("""{"schemaVersion":2,"type":"directory","revision":1}""")

        assertTrue(WearExchangeDirectoryClient.foldersOf(directory).isEmpty())
    }

    private companion object {
        const val CODE = "FMSSFTP1:h=192.168.1.20&p=2222&u=fms&pw=secret&fp=SHA256%3Aabc"
    }
}
