package com.sza.fastmediasorter.domain.delivery

/**
 * S1483: what the mirror currently serves for the artwork payloads.
 *
 * The artwork sets are published under stable names, so an installed copy cannot tell a rebuild
 * happened by looking at a URL, and their SHA-256 is deliberately not compiled into the build - a pin
 * frozen at build time is exactly what made every earlier rebuild invisible to installed apps. This
 * manifest is the freshness signal instead: it is published by the same run that uploads the payload,
 * so the two cannot disagree.
 *
 * Absence is not an error. A manifest that cannot be fetched or parsed means "nothing new" - see
 * [ArtworkManifestSource].
 */
data class ArtworkManifest(
    val schemaVersion: Int,
    val generatedAt: String,
    val sets: Map<DeliverableSet, ArtworkManifestSet>,
) {
    companion object {
        /** The only schema this build understands; a newer one is ignored rather than guessed at. */
        const val SUPPORTED_SCHEMA_VERSION = 1

        /** Manifest member names, fixed by the publisher (`collect-stream-candidates.ps1`). */
        const val SET_CHANNEL_PREVIEW = "channelPreview"
        const val SET_STREAM_LOGO = "streamLogo"
    }
}

/**
 * One payload's live state. [stamp] is the tile pack's own SHA-256: it changes exactly when the
 * artwork changes, which is the only event a client needs to notice. [totalBytes] is what the offer
 * dialog shows so a user is told the size before an 8-11 MB download starts.
 */
data class ArtworkManifestSet(
    val stamp: String,
    val totalBytes: Long,
)

/** Reads the published artwork manifest. Every failure path yields null - never an exception. */
interface ArtworkManifestSource {

    /**
     * The live stamp for [set], or null when this set is not an artwork payload, the manifest is
     * unreachable/unparseable, or its schema is one this build does not know.
     */
    suspend fun stampOf(set: DeliverableSet): String?

    /** Total download size for [set] in bytes, or null when the manifest cannot supply it. */
    suspend fun sizeOf(set: DeliverableSet): Long?
}
