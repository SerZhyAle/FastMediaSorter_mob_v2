package com.sza.fastmediasorter.data.companion

import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile

/**
 * S1002: wire-token vocabulary for the companion config v2 resource params. Kept as an explicit,
 * stable string<->enum mapping (NOT `enum.name`) so the cross-repo contract does not silently break
 * if a Kotlin enum constant is ever renamed. Unknown tokens resolve to null (soft) so a newer
 * companion token never rejects the whole import - the field just falls back to its v1 default.
 */
object CompanionResourceTokens {

    private val profileByToken: Map<String, ResourceProfile> = mapOf(
        "none" to ResourceProfile.NONE,
        "audio_library" to ResourceProfile.AUDIO_LIBRARY,
        "video_library" to ResourceProfile.VIDEO_LIBRARY,
        "photo_storage" to ResourceProfile.PHOTO_STORAGE,
        "documents" to ResourceProfile.DOCUMENTS,
        "all_files" to ResourceProfile.ALL_FILES
    )

    private val tokenByProfile: Map<ResourceProfile, String> =
        profileByToken.entries.associate { (token, profile) -> profile to token }

    private val mediaTypeByToken: Map<String, MediaType> = mapOf(
        "image" to MediaType.IMAGE,
        "video" to MediaType.VIDEO,
        "audio" to MediaType.AUDIO,
        "gif" to MediaType.GIF,
        "text" to MediaType.TEXT,
        "pdf" to MediaType.PDF,
        "epub" to MediaType.EPUB,
        "office" to MediaType.OFFICE_DOCUMENT
    )

    private val tokenByMediaType: Map<MediaType, String> =
        mediaTypeByToken.entries.associate { (token, type) -> type to token }

    /** Token -> profile; unknown/blank -> null. Case-insensitive on the token. */
    fun profileFromToken(token: String?): ResourceProfile? =
        token?.trim()?.lowercase()?.let { profileByToken[it] }

    /** Profile -> token (never null; every profile has a token). */
    fun profileToToken(profile: ResourceProfile): String =
        tokenByProfile.getValue(profile)

    /** Token -> media type; unknown/blank -> null. */
    fun mediaTypeFromToken(token: String?): MediaType? =
        token?.trim()?.lowercase()?.let { mediaTypeByToken[it] }

    /** Media types -> tokens, preserving only mappable types. */
    fun mediaTypesToTokens(types: Set<MediaType>): List<String> =
        types.mapNotNull { tokenByMediaType[it] }
}
