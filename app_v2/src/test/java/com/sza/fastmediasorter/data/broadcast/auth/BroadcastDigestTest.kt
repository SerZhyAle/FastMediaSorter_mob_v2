package com.sza.fastmediasorter.data.broadcast.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastDigestTest {

    private val mufasa = BroadcastCredentialPair("Mufasa", "Circle of Life")

    private fun rfc7616Answer(algorithm: String, response: String) =
        """Digest username="Mufasa", realm="http-auth@example.org", uri="/dir/index.html", algorithm=$algorithm, """ +
            """nonce="7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v", nc=00000001, """ +
            """cnonce="f2/wE4q74E6zIJEtWaHKaf5wv/H5QzzpXusqGemxURZJ", qop=auth, response="$response""""

    @Test
    fun `RFC 7616 SHA-256 example verifies`() {
        val header = rfc7616Answer("SHA-256", "753927fa0e85d155564e2e272a28d1802ca10daf4496794697cf8db5856cb6c1")
        assertEquals(mufasa, BroadcastDigest.verify(header, "GET", listOf(mufasa)) { true })
    }

    @Test
    fun `RFC 7616 MD5 example verifies`() {
        val header = rfc7616Answer("MD5", "8ca523f5e9506fed4657c9700eebdbec")
        assertEquals(mufasa, BroadcastDigest.verify(header, "GET", listOf(mufasa)) { true })
    }

    @Test
    fun `RFC 2069 answer without qop verifies`() {
        val pair = BroadcastCredentialPair("Mufasa", "Circle Of Life")
        val header = """Digest username="Mufasa", realm="testrealm@host.com", """ +
            """nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093", uri="/dir/index.html", """ +
            """response="670fd8c2df070c60b045671b8b24ff02""""
        assertEquals(pair, BroadcastDigest.verify(header, "GET", listOf(pair)) { true })
    }

    @Test
    fun `wrong password, unknown user, stale nonce and Basic are refused`() {
        val header = rfc7616Answer("MD5", "8ca523f5e9506fed4657c9700eebdbec")
        val wrong = BroadcastCredentialPair("Mufasa", "circle of life")
        assertNull(BroadcastDigest.verify(header, "GET", listOf(wrong)) { true })
        assertNull(BroadcastDigest.verify(header, "GET", listOf(BroadcastCredentialPair("Simba", "x"))) { true })
        assertNull(BroadcastDigest.verify(header, "GET", listOf(mufasa)) { false })
        assertNull(BroadcastDigest.verify(BroadcastDigest.basic(mufasa), "GET", listOf(mufasa)) { true })
        assertNull(BroadcastDigest.verify(null, "GET", listOf(mufasa)) { true })
    }

    @Test
    fun `the client answer to the HTTP challenge prefers SHA-256 and verifies`() {
        val nonce = BroadcastDigest.newNonce()
        val answer = BroadcastDigest.answer(BroadcastDigest.httpChallenge(nonce), "GET", "/live-audio.aac", mufasa)
        assertNotNull(answer)
        assertTrue(answer!!.contains("algorithm=SHA-256"))
        assertEquals(mufasa, BroadcastDigest.verify(answer, "GET", listOf(mufasa)) { it == nonce })
    }

    @Test
    fun `the client answer to the RTSP challenge is MD5 without qop and verifies`() {
        val nonce = BroadcastDigest.newNonce()
        val uri = "rtsp://192.168.1.5:8554/live"
        val answer = BroadcastDigest.answer(BroadcastDigest.rtspChallenge(nonce), "DESCRIBE", uri, mufasa)
        assertNotNull(answer)
        assertTrue(answer!!.contains("algorithm=MD5"))
        assertTrue(!answer.contains("qop="))
        assertEquals(mufasa, BroadcastDigest.verify(answer, "DESCRIBE", listOf(mufasa)) { it == nonce })
    }

    @Test
    fun `a pair's string form hides the password`() {
        assertTrue(!mufasa.toString().contains("Circle"))
    }
}
