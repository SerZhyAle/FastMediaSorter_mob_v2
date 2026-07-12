package com.sza.fastmediasorter.data.companion

import com.google.gson.annotations.SerializedName

/**
 * S0421: mirror of the Windows companion `CompanionResourceConfig` schema.
 * Authoritative contract: companion repo `docs/CONFIG_FORMAT.md`.
 * The canonical test vector is frozen on both ends - see CompanionConfigParserTest.
 *
 * S1002: schemaVersion 2 adds optional per-root resource params ([CompanionRootDto]). A v2 field
 * absent == v1 behavior; a v1 file (schemaVersion 1) still parses unchanged.
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

/**
 * One shared root == one imported resource. `virtualPath` + `label` are the frozen v1 fields;
 * every field below is an optional S1002 v2 addition (null == v1 default applied at import).
 *
 * Positional order is contract-frozen: new fields are appended after `label` so existing
 * positional constructor calls stay valid.
 */
data class CompanionRootDto(
    @SerializedName("virtualPath") val virtualPath: String?,
    @SerializedName("label") val label: String?,
    /** Resource profile/type token, e.g. "audio_library"; see [CompanionResourceTokens]. */
    @SerializedName("profile") val profile: String? = null,
    /** Explicit media-type tokens; overrides the profile-derived set when present. */
    @SerializedName("mediaTypes") val mediaTypes: List<String>? = null,
    @SerializedName("scanSubdirectories") val scanSubdirectories: Boolean? = null,
    @SerializedName("showSubfoldersAsItems") val showSubfoldersAsItems: Boolean? = null,
    @SerializedName("showHiddenFiles") val showHiddenFiles: Boolean? = null,
    @SerializedName("allFiles") val allFiles: Boolean? = null,
    @SerializedName("isDestination") val isDestination: Boolean? = null,
    @SerializedName("destinationColor") val destinationColor: Int? = null,
    @SerializedName("comment") val comment: String? = null,
    @SerializedName("accessPin") val accessPin: String? = null,
    @SerializedName("slideshowInterval") val slideshowInterval: Int? = null
)

/** Typed parse/validation failure so the UI can map reasons to distinct messages. */
class CompanionConfigException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    enum class Reason {
        /** Payload is not JSON / not the FMSCFG1 wrapper / undecodable. */
        MALFORMED,

        /** schemaVersion is newer than this app understands - user must update the app. */
        UNSUPPORTED_VERSION,

        /** Structurally valid JSON but required fields are missing or invalid. */
        INVALID_CONTENT
    }
}
