package com.sza.fastmediasorter.domain.delivery

/**
 * Describes the on-demand payload of one [DeliverableSet]: which files to fetch, where from (in
 * priority order), and the app-pinned integrity anchors used to verify them before attach
 * (S0386 strategic §6.1 B1/B2). Pure data - no Android dependencies.
 *
 * Source ordering (B1):
 * - Module sets (A/B/C/D) have no vendor URL (Maven-AAR or first-party), so each file lists our
 *   GitHub mirror as the sole/primary source.
 * - Language-data items list the vendor URL first (e.g. `tessdata_best/4.1.0`,
 *   `paddlelite-demo.bj.bcebos.com`) and our mirror as the auto-failover fallback.
 *
 * Integrity (B2): [PayloadFile.sha256] and [PayloadFile.minSize] are compiled into the APK and pinned
 * to the shipping app version - the mirror cannot substitute a payload because the hash would not
 * match. A blank [PayloadFile.sha256] marks an unverified pure-resource file (Set C audio videos),
 * which still enforces [PayloadFile.minSize] but performs no `System.load`.
 */
data class DeliverableSourceDescriptor(
    val set: DeliverableSet,
    val files: List<PayloadFile>
) {
    /**
     * S1200: the payload's identity, used to notice that an installed copy is not the one this build
     * expects. Derived from the compiled pins on purpose - they are the only value the mirror is
     * forbidden to change (B2 above), so anything else would be an identity the server could forge.
     *
     * Sorted by file name so a reordered [files] list cannot read as a different payload. A file with
     * no hash (the unverified pure-resource case) contributes its size instead, otherwise two such
     * files would collide on an empty string.
     */
    val stamp: String
        get() = files
            .sortedBy { it.fileName }
            .joinToString(";") { file ->
                val identity = file.sha256.ifBlank { "size:${file.minSize}" }
                "${file.fileName}=$identity"
            }
}

/**
 * One downloadable file inside a set's payload (e.g. a single `.so` or `.mp4`).
 *
 * @property fileName destination name under `filesDir/delivery/<set>/`.
 * @property sources ordered candidate URLs, tried first-to-last until one responds and verifies.
 * @property sha256 app-pinned lowercase hex SHA-256; blank for unverified pure-resource files.
 * @property minSize lower size bound (bytes) guarding against truncated/empty downloads.
 */
data class PayloadFile(
    val fileName: String,
    val sources: List<String>,
    val sha256: String,
    val minSize: Long
)
