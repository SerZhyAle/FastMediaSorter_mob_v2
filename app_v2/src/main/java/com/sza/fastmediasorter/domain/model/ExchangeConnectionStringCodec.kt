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
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale

/**
 * Reader and writer of the connection string of contract DEVICE-EXCHANGE 5.5, conformance artifact
 * `vectors/connection-string-v1.json`.
 *
 * The writer escapes values with `URLEncoder`, which is the WHATWG `application/x-www-form-urlencoded`
 * serializer the `FMSSFTP2` pairing code is written with ([SftpPairingPayload]). The reader is strict and
 * whole: the first broken rule refuses the string and nothing is partially read. Runs on API 23, so base64
 * is decoded here rather than through `java.util.Base64` (API 26).
 */
object ExchangeConnectionStringCodec {

    const val PREFIX = "FMSEXCH1:"
    const val FILE_EXTENSION = "fmsexch"
    const val MAX_TEXT = 512
    const val MAX_FILE_BYTES = 4096
    const val MAX_NAME = 64

    private const val KEY_X = "x"
    private const val KEY_FP = "fp"
    private const val KEY_N = "n"
    private const val KEY_U = "u"
    private const val KEY_I = "i"
    private val KNOWN_KEYS = setOf(KEY_X, KEY_FP, KEY_N, KEY_U, KEY_I)
    private const val FIELD_SEPARATOR = '&'
    private const val BYTE_ORDER_MARK = "\uFEFF"
    private const val FINGERPRINT_PREFIX = "SHA256:"
    private const val FINGERPRINT_CHARS = 43
    private const val FINGERPRINT_BYTES = 32
    private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val BASE64URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private const val INVITATION_CHARS = 22

    // 22 sextets carry 132 bits for 16 bytes (128 bits): the last digit's low four bits must be zero.
    private const val INVITATION_STRAY_BITS_MASK = 0b1111
    private const val SEXTET_BITS = 6
    private const val BYTE_BITS = 8
    private const val BYTE_MASK = 0xFF
    private const val BIT_BUFFER_MASK = 0xFFFF
    private const val STRAY_BITS_MASK = 0b11
    private const val HEX_RADIX = 16
    private const val LOGIN_MIN = 3
    private const val LOGIN_MAX = 64
    private const val FIRST_PRINTABLE = '!'
    private const val LAST_PRINTABLE = '~'
    private val FAMILY_PREFIX = Regex("^FMSEXCH([0-9]+):")
    private val SHARE_CODE_PREFIX = Regex("^FMSSFTP[0-9]+:")
    private val URL_PREFIX = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

    fun encode(value: ExchangeConnectionString): String = buildList {
        add(KEY_X to value.endpoint.toString())
        value.fingerprint?.let { add(KEY_FP to it) }
        value.name?.let { add(KEY_N to it) }
        value.loginHint?.let { add(KEY_U to it) }
        value.invitation?.let { add(KEY_I to it) }
    }.joinToString(FIELD_SEPARATOR.toString(), prefix = PREFIX) { (key, text) ->
        "$key=" + URLEncoder.encode(text, Charsets.UTF_8.name())
    }

    /** Pasted text, a QR payload or a shared text: one leading BOM and the white space around it are dropped. */
    fun decode(text: String): ExchangeConnectionStringResult = refusalAware {
        read(text.removePrefix(BYTE_ORDER_MARK).trim())
    }

    /** The bytes of a `.fmsexch` file: UTF-8, at most [MAX_FILE_BYTES], one non-empty line. */
    fun decodeFile(bytes: ByteArray): ExchangeConnectionStringResult = refusalAware {
        refuseIf(bytes.size > MAX_FILE_BYTES, FILE_TOO_LONG)
        val text = utf8(bytes).removePrefix(BYTE_ORDER_MARK).trim()
        refuseIf(text.any { it == '\n' || it == '\r' }, FILE_LINES)
        read(text)
    }

    /** Reads at most one byte past [MAX_FILE_BYTES], so an oversized file is refused without loading it whole. */
    fun readFile(input: InputStream): ExchangeConnectionStringResult {
        val buffer = ByteArray(MAX_FILE_BYTES + 1)
        var total = 0
        var count = 0
        while (total < buffer.size && count >= 0) {
            count = input.read(buffer, total, buffer.size - total)
            if (count > 0) total += count
        }
        return decodeFile(buffer.copyOf(total))
    }

    /** Recognition by content, never by a file name or a declared media type. */
    fun looksLikeConnectionString(text: String): Boolean =
        FAMILY_PREFIX.containsMatchIn(text.removePrefix(BYTE_ORDER_MARK).trim())

    /** The 32 bytes behind `SHA256:<base64>`, or null for any other text, including stray bits in the last digit. */
    fun fingerprintBytes(fingerprint: String): ByteArray? {
        val sextets = fingerprint.takeIf { it.startsWith(FINGERPRINT_PREFIX) }
            ?.removePrefix(FINGERPRINT_PREFIX)
            ?.takeIf { it.length == FINGERPRINT_CHARS }
            ?.map(BASE64_ALPHABET::indexOf)
            ?.takeIf { values -> values.none { it < 0 } && values.last() and STRAY_BITS_MASK == 0 }
            ?: return null
        val out = ByteArrayOutputStream(FINGERPRINT_BYTES)
        var buffer = 0
        var bits = 0
        sextets.forEach { value ->
            buffer = ((buffer shl SEXTET_BITS) or value) and BIT_BUFFER_MASK
            bits += SEXTET_BITS
            if (bits >= BYTE_BITS) {
                bits -= BYTE_BITS
                out.write((buffer shr bits) and BYTE_MASK)
            }
        }
        return out.toByteArray()
    }

    /** 1 to [MAX_NAME] Unicode scalar values with no control character (category Cc, all in the BMP). */
    fun isName(name: String): Boolean =
        name.isNotEmpty() && name.codePointCount(0, name.length) <= MAX_NAME && name.none(Char::isISOControl)

    /** A one-time invitation of 19 items AP and AR: 22 base64url characters, no padding, decoding to 16 bytes. */
    fun isInvitation(text: String): Boolean =
        text.length == INVITATION_CHARS &&
            text.all { it in BASE64URL_ALPHABET } &&
            BASE64URL_ALPHABET.indexOf(text.last()) and INVITATION_STRAY_BITS_MASK == 0

    /** The login form of contract 5.1, compared case-insensitively, so upper case is tolerated on reading. */
    fun isLogin(login: String): Boolean =
        login.length in LOGIN_MIN..LOGIN_MAX &&
            login.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == '_' || it == '-' }

    private fun read(text: String): ExchangeConnectionStringResult.Read {
        if (!text.startsWith(PREFIX)) throw Refusal(classifyForeign(text))
        refuseIf(text.length > MAX_TEXT, TOO_LONG)
        refuseIf(text.any(Char::isWhitespace), WHITESPACE)
        refuseIf(text.any { it !in FIRST_PRINTABLE..LAST_PRINTABLE }, NOT_ASCII)
        val fields = parseFields(text.substring(PREFIX.length))
        val endpoint = ExchangeEndpoint.parse(fields[KEY_X] ?: refuse(MISSING_X)) ?: refuse(BAD_X)
        val fingerprint = fields[KEY_FP]?.also { refuseIf(fingerprintBytes(it) == null, BAD_FP) }
        val name = fields[KEY_N]?.also { refuseIf(!isName(it), BAD_N) }
        val login = fields[KEY_U]?.also { refuseIf(!isLogin(it), BAD_U) }?.lowercase(Locale.ROOT)
        val invitation = fields[KEY_I]?.also { refuseIf(!isInvitation(it), BAD_I) }
        // A value every field of which passed can still overflow when re-written; refuse rather than throw.
        val value = try {
            ExchangeConnectionString(endpoint, fingerprint, name, login, invitation)
        } catch (_: IllegalArgumentException) {
            refuse(TOO_LONG)
        }
        return ExchangeConnectionStringResult.Read(value)
    }

    private fun classifyForeign(text: String): ExchangeConnectionStringResult {
        val digits = FAMILY_PREFIX.find(text)?.groupValues?.get(1)
        val looks = when {
            SHARE_CODE_PREFIX.containsMatchIn(text) -> ExchangeConnectionStringLookalike.SHARE_CODE
            URL_PREFIX.containsMatchIn(text) -> ExchangeConnectionStringLookalike.URL
            else -> null
        }
        // `FMSEXCH0:` and `FMSEXCH01:` are no version of this format; only a major above 1 asks for an update.
        return if (digits != null && digits.first() != '0' && digits != "1") {
            ExchangeConnectionStringResult.NewerMajor(digits.toIntOrNull() ?: Int.MAX_VALUE)
        } else {
            ExchangeConnectionStringResult.Refused(NOT_A_STRING, looks)
        }
    }

    // Only a known key's value is decoded: an unknown key is ignored whatever it holds, even a broken escape.
    private fun parseFields(body: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        if (body.isNotEmpty()) {
            body.split(FIELD_SEPARATOR).forEach { field ->
                val separator = field.indexOf('=')
                refuseIf(separator <= 0, MALFORMED_FIELD)
                val key = field.substring(0, separator)
                if (key in KNOWN_KEYS) {
                    refuseIf(key in fields, REPEATED_KEY)
                    fields[key] = formDecode(field.substring(separator + 1))
                }
            }
        }
        return fields
    }

    // A raw `+` is a space here, so a raw `+` inside `fp` decodes to a space and refuses the string.
    private fun formDecode(value: String): String {
        val bytes = ByteArrayOutputStream(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when (char) {
                '+' -> bytes.write(' '.code)
                '%' -> {
                    val high = value.getOrNull(index + 1)?.let { Character.digit(it, HEX_RADIX) } ?: -1
                    val low = value.getOrNull(index + 2)?.let { Character.digit(it, HEX_RADIX) } ?: -1
                    refuseIf(high < 0 || low < 0, BAD_ESCAPE)
                    bytes.write(high * HEX_RADIX + low)
                    index += 2
                }
                else -> bytes.write(char.code)
            }
            index++
        }
        return utf8(bytes.toByteArray())
    }

    private fun utf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        refuse(BAD_UTF8)
    }

    private inline fun refusalAware(block: () -> ExchangeConnectionStringResult): ExchangeConnectionStringResult =
        try {
            block()
        } catch (refusal: Refusal) {
            refusal.result
        }

    private fun refuseIf(condition: Boolean, reason: ExchangeConnectionStringRefusal) {
        if (condition) refuse(reason)
    }

    private fun refuse(reason: ExchangeConnectionStringRefusal): Nothing =
        throw Refusal(ExchangeConnectionStringResult.Refused(reason))

    // Carries the first broken rule out of the nested reading steps; never escapes this object.
    private class Refusal(val result: ExchangeConnectionStringResult) : Exception() {
        override fun fillInStackTrace(): Throwable = this
    }
}
