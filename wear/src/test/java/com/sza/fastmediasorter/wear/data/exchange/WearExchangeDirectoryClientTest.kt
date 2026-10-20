package com.sza.fastmediasorter.wear.data.exchange

import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.security.e2e.E2eDeviceKey
import com.sza.fastmediasorter.data.security.e2e.E2eRecipient
import com.sza.fastmediasorter.data.security.e2e.E2eSealedBox
import com.sza.fastmediasorter.data.security.e2e.E2eSelf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S4228: the `sftp-share` records of a `directory` answer, as the watch reads them for its account list. */
class WearExchangeDirectoryClientTest {

    private val phone = E2eSelf(PHONE_ID, E2eDeviceKey.generate())
    private val watch = E2eSelf(WATCH_ID, E2eDeviceKey.generate())
    private val sealing = WearSealing(watch, emptyList(), mapOf(PHONE_ID to phone.key.kem))

    private fun sealed(resourceId: String, root: String?, sender: E2eSelf = phone): String {
        val access = JsonObject().apply {
            addProperty("descriptor", CODE)
            root?.let { addProperty("root", it) }
        }
        val recipients = listOf(E2eRecipient(WATCH_ID, watch.key.publicKey))
        return E2eSealedBox.seal(access, E2eSealedBox.accessContext(resourceId), sender, recipients).toString()
    }

    private fun folders(directory: WearExchangeEnvelope) =
        WearExchangeDirectoryClient.foldersOf(directory) { record, id ->
            WearExchangeDirectoryClient.openSealedAccess(sealing, record, id)
        }

    @Test
    fun `an open and a protected sealed share are read and an unreadable one is dropped alone`() {
        val directory = WearExchangeEnvelope.decode(
            """{"schemaVersion":2,"type":"directory","revision":3,"resources":[""" +
                """{"resourceId":"r-open","kind":"sftp-share","name":"Photos",""" +
                """"sealedAccess":${sealed("r-open", "/photos/")}},""" +
                """{"resourceId":"r-pin","kind":"sftp-share","name":"Vault","protection":{"scheme":"pin-v1"},""" +
                """"sealedAccess":${sealed("r-pin", "vault")}},""" +
                """{"resourceId":"r-future","kind":"sftp-share","name":"Future","protection":{"scheme":"pin-v9"},""" +
                """"sealedAccess":${sealed("r-future", null)}},""" +
                """{"resourceId":"r-other","kind":"smb-share","name":"Other",""" +
                """"sealedAccess":${sealed("r-other", null)}}""" +
                """]}"""
        )

        val folders = folders(directory)

        assertEquals(listOf("r-open", "r-pin"), folders.map { it.resourceId })
        assertEquals("photos", folders[0].root)
        assertFalse(folders[0].pinProtected)
        assertTrue(folders[1].pinProtected)
        assertEquals("SHA256:abc", folders[1].code.hostKeyFingerprint)
    }

    @Test
    fun `a clear access through the server, a moved box and an untrusted sender are never used`() {
        val stranger = E2eSelf(PHONE_ID, E2eDeviceKey.generate())
        val directory = WearExchangeEnvelope.decode(
            """{"schemaVersion":2,"type":"directory","revision":3,"resources":[""" +
                """{"resourceId":"r-clear","kind":"sftp-share","name":"Clear","access":{"descriptor":"$CODE"}},""" +
                """{"resourceId":"r-moved","kind":"sftp-share","name":"Moved",""" +
                """"sealedAccess":${sealed("r-elsewhere", null)}},""" +
                """{"resourceId":"r-forged","kind":"sftp-share","name":"Forged",""" +
                """"sealedAccess":${sealed("r-forged", null, stranger)}}""" +
                """]}"""
        )

        assertTrue(folders(directory).isEmpty())
    }

    @Test
    fun `a record whose fingerprint differs from the opened descriptor is dropped`() {
        val directory = WearExchangeEnvelope.decode(
            """{"schemaVersion":2,"type":"directory","revision":3,"resources":[""" +
                """{"resourceId":"r-open","kind":"sftp-share","name":"Photos","hostKeyFingerprint":"SHA256:other",""" +
                """"sealedAccess":${sealed("r-open", null)}}""" +
                """]}"""
        )

        assertTrue(folders(directory).isEmpty())
    }

    @Test
    fun `a directory without resources lists nothing`() {
        val directory = WearExchangeEnvelope.decode("""{"schemaVersion":2,"type":"directory","revision":1}""")

        assertTrue(folders(directory).isEmpty())
    }

    private companion object {
        const val CODE = "FMSSFTP1:h=192.168.1.20&p=2222&u=fms&pw=secret&fp=SHA256%3Aabc"
        const val PHONE_ID = "phone-1"
        const val WATCH_ID = "watch-1"
    }
}
