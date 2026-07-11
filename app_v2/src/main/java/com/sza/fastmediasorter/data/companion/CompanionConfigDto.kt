package com.sza.fastmediasorter.data.companion

import com.google.gson.annotations.SerializedName

/**
 * S0421: mirror of the Windows companion `CompanionResourceConfig` schema.
 * Authoritative contract: companion repo `docs/CONFIG_FORMAT.md` (schemaVersion 1).
 * The canonical test vector is frozen on both ends - see CompanionConfigParserTest.
 */
data class CompanionConfigDto(
    @SerializedName("schemaVersion") val schemaVersion: Int,
    @SerializedName("resourceName") val resourceName: String?,
    @SerializedName("protocol") val protocol: String?,
    /** Ordered: LAN first, then port-forward - try in order. */
    @SerializedName("accessPaths") val accessPaths: List<CompanionAccessPathDto>?,
    @SerializedName("username") val username: String?,
    @SerializedName("password") val password: String?,
    @SerializedName("hostKeyFingerprintSha256") val hostKeyFingerprintSha256: String?,
    @SerializedName("roots") val roots: List<CompanionRootDto>?,
    @SerializedName("createdAt") val createdAt: String?
)

data class CompanionAccessPathDto(
    @SerializedName("kind") val kind: String?,
    @SerializedName("host") val host: String?,
    @SerializedName("port") val port: Int?
) {
    companion object {
        const val KIND_LAN = "lan"
        const val KIND_PORT_FORWARD = "portforward"
    }
}

data class CompanionRootDto(
    @SerializedName("virtualPath") val virtualPath: String?,
    @SerializedName("label") val label: String?
)

/** Typed parse/validation failure so the UI can map reasons to distinct messages. */
class CompanionConfigException(
    val reason: Reason,
    message: String
) : Exception(message) {
    enum class Reason {
        /** Payload is not JSON / not the FMSCFG1 wrapper / undecodable. */
        MALFORMED,
        /** schemaVersion is newer than this app understands - user must update the app. */
        UNSUPPORTED_VERSION,
        /** Structurally valid JSON but required fields are missing or invalid. */
        INVALID_CONTENT
    }
}
