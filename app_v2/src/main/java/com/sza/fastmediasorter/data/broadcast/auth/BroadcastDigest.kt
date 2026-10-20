package com.sza.fastmediasorter.data.broadcast.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale

/**
 * HTTP and RTSP Digest for a protected broadcast (contract LIVE-BROADCAST item M): the producer's check and challenge,
 * and the listener's answer. HTTP offers `SHA-256` first and `MD5` beside it (RFC 7616), each in its own header field;
 * [answer] reads either that list joined or one field alone. RTSP offers `MD5` alone,
 * because the only RTSP client this app plays with (Media3) answers nothing else. Both the RFC 7616 form with
 * `qop=auth` and the RFC 2069 form without it are accepted, since RTSP clients send the older one.
 */
object BroadcastDigest {

    const val REALM = "fms-broadcast"
    const val ALGORITHM_SHA256 = "SHA-256"
    const val ALGORITHM_MD5 = "MD5"
    const val HEADER_AUTHORIZATION = "Authorization"
    const val HEADER_CHALLENGE = "WWW-Authenticate"
    private const val QOP_AUTH = "auth"
    private const val SCHEME_DIGEST = "Digest"
    private const val SCHEME_BASIC = "Basic"
    private const val NONCE_BYTES = 16
    private const val FIRST_NC = "00000001"

    private val PARAM = Regex("""([A-Za-z0-9_-]+)\s*=\s*(?:"((?:[^"\\]|\\.)*)"|([^\s,]+))""")
    private val CHALLENGE_SPLIT = Regex("""(?i)(?:^|,)\s*(?=(?:Digest|Basic)\s)""")
    private val random = SecureRandom()

    /** A fresh server nonce, base64url without padding. */
    fun newNonce(): String {
        val bytes = ByteArray(NONCE_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * The HTTP challenge values, one per header field and `SHA-256` first (RFC 7616 section 3.7). One challenge per
     * field is the form curl, browsers and ffmpeg read; a single field holding both is a list they fail to parse.
     */
    fun httpChallenges(nonce: String): List<String> = listOf(ALGORITHM_SHA256, ALGORITHM_MD5).map {
        """$SCHEME_DIGEST realm="$REALM", qop="$QOP_AUTH", algorithm=$it, nonce="$nonce""""
    }

    /** The RTSP challenge value: one `MD5` challenge without `qop`, the form RTSP clients answer. */
    fun rtspChallenge(nonce: String): String = """$SCHEME_DIGEST realm="$REALM", nonce="$nonce""""

    /**
     * Returns the pair whose Digest answer in [authorization] is right for [method], or null. [nonceValid] decides
     * whether the answered nonce is one this server issued, so a stale answer gets a new challenge.
     */
    fun verify(
        authorization: String?,
        method: String,
        pairs: List<BroadcastCredentialPair>,
        nonceValid: (String) -> Boolean,
    ): BroadcastCredentialPair? {
        val params = authorization?.let(::digestParams)
        val pair = params?.get("username")?.let { user -> pairs.firstOrNull { it.user == user } }
        val nonce = params?.get("nonce")
        val answered = params?.get("response")?.lowercase(Locale.ROOT)
        val accepted = pair != null && nonce != null && answered != null && nonceValid(nonce) &&
            constantTimeEquals(expectedResponse(params, method, pair), answered)
        return pair.takeIf { accepted }
    }

    /**
     * The listener's `Authorization` value for [challengeHeader], or null when it holds no Digest challenge this
     * code can answer. `SHA-256` is preferred when offered.
     */
    fun answer(
        challengeHeader: String,
        method: String,
        uri: String,
        pair: BroadcastCredentialPair,
        cnonce: String = newNonce(),
    ): String? {
        val challenges = challenges(challengeHeader)
        val chosen = challenges.firstOrNull { algorithmOf(it) == ALGORITHM_SHA256 }
            ?: challenges.firstOrNull { algorithmOf(it) == ALGORITHM_MD5 }
        return chosen?.let { buildAnswer(it, method, uri, pair, cnonce) }
    }

    /** `Basic` credentials for the relay, which checks them inside TLS (DEVICE-EXCHANGE item BF). */
    fun basic(pair: BroadcastCredentialPair): String {
        val raw = "${pair.user}:${pair.password}".toByteArray(Charsets.UTF_8)
        return "$SCHEME_BASIC ${Base64.getEncoder().encodeToString(raw)}"
    }

    private fun buildAnswer(
        challenge: Map<String, String>,
        method: String,
        uri: String,
        pair: BroadcastCredentialPair,
        cnonce: String,
    ): String {
        val algorithm = algorithmOf(challenge)
        val realm = challenge["realm"].orEmpty()
        val nonce = challenge["nonce"].orEmpty()
        val qop = challenge["qop"]?.split(',')?.map { it.trim() }?.firstOrNull { it == QOP_AUTH }
        val params = buildMap {
            put("username", pair.user)
            put("realm", realm)
            put("nonce", nonce)
            put("uri", uri)
            put("algorithm", algorithm)
            if (qop != null) {
                put("qop", qop)
                put("nc", FIRST_NC)
                put("cnonce", cnonce)
            }
        }
        val response = expectedResponse(params, method, pair)
        val quoted = listOf("username", "realm", "nonce", "uri", "cnonce")
        val fields = (params + ("response" to response)).map { (key, value) ->
            if (key in quoted || key == "response") """$key="$value"""" else "$key=$value"
        }
        return "$SCHEME_DIGEST ${fields.joinToString(", ")}"
    }

    private fun expectedResponse(params: Map<String, String>, method: String, pair: BroadcastCredentialPair): String {
        val algorithm = algorithmOf(params)
        val realm = params["realm"].orEmpty()
        val ha1 = hash(algorithm, "${pair.user}:$realm:${pair.password}")
        val ha2 = hash(algorithm, "$method:${params["uri"].orEmpty()}")
        val nonce = params["nonce"].orEmpty()
        val qop = params["qop"]
        val material = if (qop == null) {
            "$ha1:$nonce:$ha2"
        } else {
            "$ha1:$nonce:${params["nc"].orEmpty()}:${params["cnonce"].orEmpty()}:$qop:$ha2"
        }
        return hash(algorithm, material)
    }

    private fun algorithmOf(params: Map<String, String>): String =
        if (params["algorithm"].equals(ALGORITHM_SHA256, ignoreCase = true)) ALGORITHM_SHA256 else ALGORITHM_MD5

    private fun hash(algorithm: String, text: String): String =
        MessageDigest.getInstance(algorithm).digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun constantTimeEquals(expected: String, actual: String): Boolean =
        MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), actual.toByteArray(Charsets.US_ASCII))

    private fun digestParams(header: String): Map<String, String>? {
        val trimmed = header.trim()
        val isDigest = trimmed.length > SCHEME_DIGEST.length &&
            trimmed.regionMatches(0, SCHEME_DIGEST, 0, SCHEME_DIGEST.length, ignoreCase = true) &&
            trimmed[SCHEME_DIGEST.length].isWhitespace()
        return if (isDigest) params(trimmed.substring(SCHEME_DIGEST.length)) else null
    }

    private fun challenges(header: String): List<Map<String, String>> = header.split(CHALLENGE_SPLIT)
        .map { it.trim() }
        .mapNotNull(::digestParams)

    private fun params(text: String): Map<String, String> = PARAM.findAll(text).associate { match ->
        val (name, quotedValue, bareValue) = match.destructured
        val value = if (match.groups[2] != null) quotedValue.replace("\\\"", "\"") else bareValue
        name.lowercase(Locale.ROOT) to value
    }
}
