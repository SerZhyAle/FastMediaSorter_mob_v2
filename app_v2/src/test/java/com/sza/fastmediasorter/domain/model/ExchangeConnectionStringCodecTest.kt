package com.sza.fastmediasorter.domain.model

import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringRefusal.BAD_ESCAPE
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringRefusal.BAD_FP
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringRefusal.BAD_I
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringRefusal.BAD_N
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringRefusal.BAD_U
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringRefusal.BAD_UTF8
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringRefusal.BAD_X
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringRefusal.FILE_LINES
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringRefusal.FILE_TOO_LONG
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringRefusal.MALFORMED_FIELD
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringRefusal.MISSING_X
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringRefusal.NOT_ASCII
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringRefusal.NOT_A_STRING
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringRefusal.REPEATED_KEY
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringRefusal.TOO_LONG
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringRefusal.WHITESPACE
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/** Each rule of contract DEVICE-EXCHANGE 5.5 on its own; the catalog vectors replay in the sibling test. */
class ExchangeConnectionStringCodecTest {

    private fun read(text: String): ExchangeConnectionString =
        (ExchangeConnectionStringCodec.decode(text) as ExchangeConnectionStringResult.Read).value

    private fun refusal(text: String): ExchangeConnectionStringRefusal =
        (ExchangeConnectionStringCodec.decode(text) as ExchangeConnectionStringResult.Refused).reason

    private fun fileRefusal(bytes: ByteArray): ExchangeConnectionStringRefusal =
        (ExchangeConnectionStringCodec.decodeFile(bytes) as ExchangeConnectionStringResult.Refused).reason

    @Test
    fun `the smallest string reads its endpoint`() {
        val value = read("$PREFIX${"x=192.0.2.10%3A22022"}")
        assertEquals(ExchangeEndpoint("192.0.2.10", PORT), value.endpoint)
        assertNull(value.fingerprint)
        assertNull(value.name)
        assertNull(value.loginHint)
    }

    @Test
    fun `the prefix is case-sensitive and a foreign text names what it resembles`() {
        assertEquals(NOT_A_STRING, refusal("fmsexch1:x=192.0.2.10%3A22022"))
        assertEquals(NOT_A_STRING, refusal(""))
        assertEquals(NOT_A_STRING, refusal("FMSEXCH0:x=192.0.2.10%3A22022"))
        val share = ExchangeConnectionStringCodec.decode("FMSSFTP2:AbCdEf") as ExchangeConnectionStringResult.Refused
        assertEquals(ExchangeConnectionStringLookalike.SHARE_CODE, share.looks)
        val url = ExchangeConnectionStringCodec.decode("https://a.example:1/") as ExchangeConnectionStringResult.Refused
        assertEquals(ExchangeConnectionStringLookalike.URL, url.looks)
    }

    @Test
    fun `a later major asks for an update and reads nothing`() {
        assertEquals(
            ExchangeConnectionStringResult.NewerMajor(2),
            ExchangeConnectionStringCodec.decode("FMSEXCH2:x=192.0.2.10%3A22022&k=v"),
        )
        assertTrue(ExchangeConnectionStringCodec.looksLikeConnectionString(" FMSEXCH7:anything"))
    }

    @Test
    fun `white space and one byte-order mark around the text are dropped, inside they refuse`() {
        assertEquals(ENDPOINT, read("﻿  $MINIMAL\r\n").endpoint)
        assertEquals(WHITESPACE, refusal("$MINIMAL &n=a"))
        assertEquals(WHITESPACE, refusal("$MINIMAL&n=a\tb"))
        assertEquals(NOT_ASCII, refusal("$MINIMAL&n=café"))
        assertEquals(NOT_ASCII, refusal("$MINIMAL&n=a\u0001b"))
    }

    @Test
    fun `512 characters are the limit`() {
        val filler = "&z=" + "a".repeat(MAX_TEXT - MINIMAL.length - 3)
        assertEquals(ENDPOINT, read(MINIMAL + filler).endpoint)
        assertEquals(TOO_LONG, refusal(MINIMAL + filler + "a"))
    }

    @Test
    fun `an unknown key is ignored whatever its value, a known one may not repeat`() {
        assertEquals(ENDPOINT, read("$MINIMAL&zz=%ZZ%&q=").endpoint)
        assertEquals(REPEATED_KEY, refusal("$MINIMAL&x=192.0.2.11%3A22022"))
        assertEquals(REPEATED_KEY, refusal("$MINIMAL&n=a&n=b"))
        assertEquals(MISSING_X, refusal(PREFIX))
        assertEquals(MISSING_X, refusal("${PREFIX}z=1"))
    }

    @Test
    fun `an empty field, a field without equals or without key refuses`() {
        assertEquals(MALFORMED_FIELD, refusal("$MINIMAL&&n=a"))
        assertEquals(MALFORMED_FIELD, refusal("$MINIMAL&"))
        assertEquals(MALFORMED_FIELD, refusal("$MINIMAL&foo"))
        assertEquals(MALFORMED_FIELD, refusal("$MINIMAL&=v"))
    }

    @Test
    fun `a broken escape or non-UTF-8 bytes in a known value refuse`() {
        assertEquals(BAD_ESCAPE, refusal("$MINIMAL&n=%G1"))
        assertEquals(BAD_ESCAPE, refusal("$MINIMAL&n=a%"))
        assertEquals(BAD_ESCAPE, refusal("$MINIMAL&n=a%4"))
        assertEquals(BAD_UTF8, refusal("$MINIMAL&n=%FF%FE"))
        assertEquals("Home NAS", read("$MINIMAL&n=Home%20NAS").name)
    }

    @Test
    fun `the endpoint takes a DNS name, IPv4 or bracketed IPv6 and a port of 1 to 65535`() {
        assertEquals(
            ExchangeEndpoint("exchange.example.org", PORT),
            read("${PREFIX}x=Exchange.Example.ORG%3A22022").endpoint,
        )
        assertEquals(
            ExchangeEndpoint("2001:db8::7", PORT),
            read("${PREFIX}x=%5B2001%3A0DB8%3A0%3A0%3A0%3A0%3A0%3A7%5D%3A22022").endpoint,
        )
        assertEquals("example.org:80", read("${PREFIX}x=example.org%3A00080").endpoint.toString())
        listOf(
            "example.org", "example.org%3A0", "example.org%3A65536", "example.org%3A022022", "example.org%3A%2B80",
            "%3A22022", "example.org%2Fpath%3A22022", "bob%40example.org%3A22022", "example.org.%3A22022",
            "-a.example%3A22022", "a_b.example%3A22022", "12345%3A22022", "999.1.1.1%3A22022", "1.2.3%3A22022",
            "2001%3Adb8%3A%3A7%3A22022", "%5B2001%3Adb8%3A%3A7%3A22022", "%5Bfe80%3A%3A1%25eth0%5D%3A22022",
            "%5B192.0.2.10%5D%3A22022", "b%C3%BCcher.example%3A22022",
        ).forEach { x -> assertEquals(x, BAD_X, refusal("${PREFIX}x=$x")) }
    }

    @Test
    fun `an IPv6 literal is written in RFC 5952 form`() {
        assertEquals("2001:db8::1:0:0:1", ExchangeEndpoint.parse("[2001:db8:0:0:1:0:0:1]:1")?.host)
        assertEquals("2001:0:0:1::1", ExchangeEndpoint.parse("[2001:0:0:1:0:0:0:1]:1")?.host)
        assertEquals("::1", ExchangeEndpoint.parse("[0:0:0:0:0:0:0:1]:1")?.host)
        assertEquals("::ffff:192.0.2.1", ExchangeEndpoint.parse("[::FFFF:c000:0201]:1")?.host)
        assertEquals("2001:db8:0:1:1:1:1:1", ExchangeEndpoint.parse("[2001:db8::1:1:1:1:1]:1")?.host)
        assertNull(ExchangeEndpoint.parse("[1:2:3:4:5:6:7:8:9]:1"))
        assertNull(ExchangeEndpoint.parse("[1::2::3]:1"))
    }

    @Test
    fun `an embedded IPv4 address must occupy the final IPv6 groups`() {
        assertNull(ExchangeEndpoint.parse("[192.0.2.1::]:1"))
        assertNull(ExchangeEndpoint.parse("[1:192.0.2.1::2]:1"))
        assertEquals("::ffff:192.0.2.1", ExchangeEndpoint.parse("[::ffff:192.0.2.1]:1")?.host)
        assertEquals("1:2:3:4:5:6:c000:201", ExchangeEndpoint.parse("[1:2:3:4:5:6:192.0.2.1]:1")?.host)
    }

    @Test
    fun `the fingerprint is SHA256 and 43 standard base64 digits worth 32 bytes`() {
        val value = read("$MINIMAL&fp=SHA256%3A%2B%2B%2B%2B%2F%2F%2F%2FNA%2BxR01JbXXB5CJm2pgMG7SRVTB8msjcGFw")
        val bytes = checkNotNull(value.fingerprintBytes())
        assertEquals(FINGERPRINT_BYTES, bytes.size)
        assertArrayEquals(byteArrayOf(-5, -17, -66, -1, -1, -1), bytes.copyOf(6))
        listOf(
            "SHA256%3A----____NA-xR01JbXXB5CJm2pgMG7SRVTB8msjcGFw",
            "sha256%3A$FP_BODY",
            "SHA256%3A${FP_BODY.dropLast(1)}",
            "SHA256%3A${FP_BODY.dropLast(1)}Z",
            "SHA256%3A$FP_BODY%3D",
            "SHA256%3A++++%2F%2F%2F%2FNA+xR01JbXXB5CJm2pgMG7SRVTB8msjcGFw",
            "",
        ).forEach { fp -> assertEquals(fp, BAD_FP, refusal("$MINIMAL&fp=$fp")) }
    }

    @Test
    fun `the name counts Unicode scalar values and refuses control characters`() {
        val scalars = "n".repeat(MAX_NAME - EMOJI_COUNT) + "😀".repeat(EMOJI_COUNT)
        val encoded = "n".repeat(MAX_NAME - EMOJI_COUNT) + "%F0%9F%98%80".repeat(EMOJI_COUNT)
        assertEquals(scalars, read("$MINIMAL&n=$encoded").name)
        assertEquals(BAD_N, refusal("$MINIMAL&n=n$encoded"))
        assertEquals(BAD_N, refusal("$MINIMAL&n=" + "n".repeat(MAX_NAME + 1)))
        assertEquals(BAD_N, refusal("$MINIMAL&n=a%0Ab"))
        assertEquals(BAD_N, refusal("$MINIMAL&n="))
    }

    @Test
    fun `unpaired surrogates cannot become a connection string name`() {
        listOf("\uD800", "\uDC00", "a\uD800b", "a\uDC00b", "\uDC00\uD800").forEach { name ->
            assertFalse(ExchangeConnectionStringCodec.isName(name))
            val construction = runCatching { ExchangeConnectionString(ENDPOINT, name = name) }
            assertTrue(construction.exceptionOrNull() is IllegalArgumentException)
        }
        val value = ExchangeConnectionString(ENDPOINT, name = "a\uD83D\uDE00b")
        assertEquals(value, read(value.encode()))
    }

    @Test
    fun `the login hint has the form of 5_1 and is written lower case`() {
        assertEquals("alice", read("$MINIMAL&u=Alice").loginHint)
        listOf("ab", "u".repeat(LOGIN_MAX + 1), "al%20ce", "alice%40example.org", "").forEach { u ->
            assertEquals(u, BAD_U, refusal("$MINIMAL&u=$u"))
        }
    }

    @Test
    fun `the writer uses the one order and the WHATWG serializer`() {
        val value = read("${PREFIX}n=Home~NAS*+%C3%A9&u=alice&fp=SHA256%3A$FP_BODY&x=192.0.2.10:22022")
        val expected = "$MINIMAL&fp=SHA256%3A$FP_BODY&n=Home%7ENAS*+%C3%A9&u=alice"
        assertEquals(expected, value.encode())
        assertEquals(value, read(value.encode()))
        assertEquals(
            "${PREFIX}x=%5B2001%3Adb8%3A%3A7%5D%3A22022",
            ExchangeConnectionString(ExchangeEndpoint("2001:db8::7", PORT)).encode(),
        )
    }

    @Test
    fun `an invitation string carries i after u and makes the string an invitation`() {
        val value = read("${PREFIX}i=$INVITATION&u=alice&x=192.0.2.10:22022")
        assertEquals(INVITATION, value.invitation)
        assertTrue(value.isInvitation)
        assertEquals("$MINIMAL&u=alice&i=$INVITATION", value.encode())
        assertFalse(read(MINIMAL).isInvitation)
    }

    @Test
    fun `an invitation of the wrong length or with stray bits refuses the whole string`() {
        assertEquals(BAD_I, refusal("$MINIMAL&i=${INVITATION.dropLast(1)}"))
        assertEquals(BAD_I, refusal("$MINIMAL&i=${INVITATION.dropLast(1)}B"))
        assertEquals(REPEATED_KEY, refusal("$MINIMAL&i=$INVITATION&i=$INVITATION"))
    }

    @Test
    fun `toString never carries the invitation`() {
        assertFalse(read("$MINIMAL&i=$INVITATION").toString().contains(INVITATION))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a value the codec could not read back cannot be built`() {
        ExchangeConnectionString(ENDPOINT, loginHint = "Alice")
    }

    @Test
    fun `toString names the endpoint and never the login hint`() {
        val text = read("$MINIMAL&u=alice&n=Office").toString()
        assertTrue(text.contains("192.0.2.10:22022"))
        assertFalse(text.contains("alice"))
    }

    @Test
    fun `a file is one line of at most 4096 bytes, read by content`() {
        val line = MINIMAL.toByteArray()
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val padded = line + " ".repeat(MAX_FILE - line.size).toByteArray()
        assertEquals(ENDPOINT, readFile(bom + "\r\n\r\n".toByteArray() + line + "\r\n".toByteArray()).endpoint)
        assertEquals(ENDPOINT, readFile(padded).endpoint)
        assertEquals(FILE_TOO_LONG, fileRefusal(padded + " ".toByteArray()))
        assertEquals(FILE_LINES, fileRefusal(line + "\n".toByteArray() + line))
        assertEquals(BAD_UTF8, fileRefusal(line + byteArrayOf(0xFF.toByte())))
        assertEquals(NOT_A_STRING, fileRefusal(ByteArray(0)))
        val stream = ExchangeConnectionStringCodec.readFile(ByteArrayInputStream(padded + ByteArray(MAX_FILE)))
        assertEquals(FILE_TOO_LONG, (stream as ExchangeConnectionStringResult.Refused).reason)
    }

    private fun readFile(bytes: ByteArray): ExchangeConnectionString {
        val result = ExchangeConnectionStringCodec.readFile(ByteArrayInputStream(bytes))
        return (result as ExchangeConnectionStringResult.Read).value
    }

    private companion object {
        const val PREFIX = ExchangeConnectionStringCodec.PREFIX
        const val MINIMAL = "${PREFIX}x=192.0.2.10%3A22022"
        const val PORT = 22_022
        const val MAX_TEXT = ExchangeConnectionStringCodec.MAX_TEXT
        const val MAX_NAME = ExchangeConnectionStringCodec.MAX_NAME
        const val MAX_FILE = ExchangeConnectionStringCodec.MAX_FILE_BYTES
        const val LOGIN_MAX = 64
        const val FINGERPRINT_BYTES = 32
        const val EMOJI_COUNT = 4
        const val INVITATION = "q3Vb7YtK0xP2mN9s-_R4wA"
        const val FP_BODY = "Ul2rC03A44auS8B7ibLjJaAApHooIx%2F4R%2BSSgDTXUtY"
        val ENDPOINT = ExchangeEndpoint("192.0.2.10", PORT)
    }
}
