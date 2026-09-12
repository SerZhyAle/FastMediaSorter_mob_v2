package com.sza.fastmediasorter.wear.domain.model

/**
 * S2462: why one contract key of a settings payload did not reach the apply step.
 *
 * Three reasons rather than a count, because they call for opposite reactions: a key the peer never
 * sent is the normal shape of a version difference, a key of the wrong type between two builds that
 * agree on the contract is a format defect, and a key this side does not know means the peer is newer.
 * A single "n fields dropped" number answers none of those questions.
 *
 * Mirrored verbatim from the phone copy in
 * `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearSettingsDecodeResult.kt` - the two
 * modules share no source artifact, so the contract exists twice and the copies must not drift.
 */
enum class WearSettingsFieldIssue {
    /** The peer did not carry this key at all, or carried it as JSON null. */
    MISSING,

    /** The peer carried this key as a different JSON kind than the contract declares. */
    WRONG_TYPE,

    /** The peer carried a key this build's contract does not name. */
    UNKNOWN_KEY
}

/**
 * S2462: one rejected key paired with the reason it was rejected.
 */
data class WearSettingsDivergence(
    val field: String,
    val issue: WearSettingsFieldIssue
)

/**
 * S2462: the outcome of reading one settings payload off the wire.
 *
 * [presentFields] is the contract, not a convenience: a key absent from it must never be read off
 * [payload]. Gson's reflective construction bypasses the Kotlin constructor, so the six fields that
 * predate nullability carry a fabricated JVM default rather than a real value when the peer omitted
 * them, and the value alone cannot say which happened.
 *
 * [payload] is null only when nothing could be decoded at all - the text was not a JSON object, or the
 * object was rejected wholesale. [presentFields] is then empty, so the two agree.
 */
data class WearSettingsDecodeResult(
    val payload: WearSettingsPayload?,
    val presentFields: Set<String>,
    val divergences: List<WearSettingsDivergence>
)
