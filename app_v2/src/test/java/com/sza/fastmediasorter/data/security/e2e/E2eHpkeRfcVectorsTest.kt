package com.sza.fastmediasorter.data.security.e2e

import com.sza.fastmediasorter.data.security.e2e.E2eBytes.fromHex
import com.sza.fastmediasorter.data.security.e2e.E2eBytes.hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * RFC 9180 Appendix A.3.3 - DHKEM(P-256, HKDF-SHA256), HKDF-SHA256, AES-128-GCM in the Auth mode.
 * Contract item DH requires these to pass before the implementation seals anything.
 */
class E2eHpkeRfcVectorsTest {

    private val info = fromHex("4f6465206f6e2061204772656369616e2055726e")
    private val plaintext = fromHex("4265617574792069732074727574682c20747275746820626561757479")

    private val ephemeral = E2eHpke.deriveKeyPair(fromHex(IKM_E))
    private val recipient = E2eHpke.deriveKeyPair(fromHex(IKM_R))
    private val sender = E2eHpke.deriveKeyPair(fromHex(IKM_S))

    @Test
    fun `DeriveKeyPair reproduces the three published key pairs`() {
        assertEquals(SK_E, hex(ephemeral.privateKey))
        assertEquals(PK_E, hex(ephemeral.publicKey))
        assertEquals(SK_R, hex(recipient.privateKey))
        assertEquals(PK_R, hex(recipient.publicKey))
        assertEquals(SK_S, hex(sender.privateKey))
        assertEquals(PK_S, hex(sender.publicKey))
    }

    @Test
    fun `the sender's setup gives the published enc, shared secret, key and base nonce`() {
        val (enc, context) = E2eHpke.setupAuthSender(recipient.publicKey, info, sender, ephemeral)

        assertEquals(PK_E, hex(enc))
        assertEquals(SHARED_SECRET, hex(context.sharedSecret))
        assertEquals(KEY, hex(context.key))
        assertEquals(BASE_NONCE, hex(context.baseNonce))
    }

    @Test
    fun `every published encryption is reproduced and opened`() {
        val (enc, senderContext) = E2eHpke.setupAuthSender(recipient.publicKey, info, sender, ephemeral)
        val receiverContext = E2eHpke.setupAuthReceiver(enc, recipient, info, sender.publicKey)

        ENCRYPTIONS.forEach { (sequence, aadHex, ctHex) ->
            senderContext.skipTo(sequence)
            receiverContext.skipTo(sequence)
            val aad = fromHex(aadHex)

            assertEquals("seq $sequence", ctHex, hex(senderContext.seal(aad, plaintext)))
            assertArrayEquals("seq $sequence", plaintext, receiverContext.open(aad, fromHex(ctHex)))
        }
    }

    @Test
    fun `the receiver opens nothing under the wrong sender key`() {
        val (enc, senderContext) = E2eHpke.setupAuthSender(recipient.publicKey, info, sender, ephemeral)
        val ciphertext = senderContext.seal(fromHex(FIRST_AAD), plaintext)

        val opened = E2eHpke.openAuth(enc, recipient, info, fromHex(FIRST_AAD), ciphertext, recipient.publicKey)

        assertNull(opened)
    }

    private companion object {
        const val IKM_E = "798d82a8d9ea19dbc7f2c6dfa54e8a6706f7cdc119db0813dacf8440ab37c857"
        const val PK_E = "042224f3ea800f7ec55c03f29fc9865f6ee27004f818fcbdc6dc68932c1e52" +
            "e15b79e264a98f2c535ef06745f3d308624414153b22c7332bc1e691cb4af4d53454"
        const val SK_E = "6b8de0873aed0c1b2d09b8c7ed54cbf24fdf1dfc7a47fa501f918810642d7b91"
        const val IKM_R = "7bc93bde8890d1fb55220e7f3b0c107ae7e6eda35ca4040bb6651284bf0747ee"
        const val PK_R = "04423e363e1cd54ce7b7573110ac121399acbc9ed815fae03b72ffbd4c18b0" +
            "1836835c5a09513f28fc971b7266cfde2e96afe84bb0f266920e82c4f53b36e1a78d"
        const val SK_R = "d929ab4be2e59f6954d6bedd93e638f02d4046cef21115b00cdda2acb2a4440e"
        const val IKM_S = "874baa0dcf93595a24a45a7f042e0d22d368747daaa7e19f80a802af19204ba8"
        const val PK_S = "04a817a0902bf28e036d66add5d544cc3a0457eab150f104285df1e293b5c1" +
            "0eef8651213e43d9cd9086c80b309df22cf37609f58c1127f7607e85f210b2804f73"
        const val SK_S = "1120ac99fb1fccc1e8230502d245719d1b217fe20505c7648795139d177f0de9"
        const val SHARED_SECRET = "d4aea336439aadf68f9348880aa358086f1480e7c167b6ef15453ba69b94b44f"
        const val KEY = "19aa8472b3fdc530392b0e54ca17c0f5"
        const val BASE_NONCE = "b390052d26b67a5b8a8fcaa4"
        const val FIRST_AAD = "436f756e742d30"

        val ENCRYPTIONS = listOf(
            Triple(
                0L,
                FIRST_AAD,
                "82ffc8c44760db691a07c5627e5fc2c08e7a86979ee79b494a17cc3405446ac2bdb8f265db4a099ed3289ffe19",
            ),
            Triple(
                1L,
                "436f756e742d31",
                "b0a705a54532c7b4f5907de51c13dffe1e08d55ee9ba59686114b05945494d96725b239468f1229e3966aa1250",
            ),
            Triple(
                2L,
                "436f756e742d32",
                "8dc805680e3271a801790833ed74473710157645584f06d1b53ad439078d880b23e25256663178271c80ee8b7c",
            ),
            Triple(
                4L,
                "436f756e742d34",
                "04c8f7aae1584b61aa5816382cb0b834a5d744f420e6dffb5ddcec633a21b8b3472820930c1ea9258b035937a2",
            ),
            Triple(
                255L,
                "436f756e742d323535",
                "4a319462eaedee37248b4d985f64f4f863d31913fe9e30b6e13136053b69fe5d70853c84c60a84bb5495d5a678",
            ),
            Triple(
                256L,
                "436f756e742d323536",
                "28e874512f8940fafc7d06135e7589f6b4198bc0f3a1c64702e72c9e6abaf9f05cb0d2f11b03a517898815c934",
            ),
        )
    }
}
