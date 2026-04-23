package com.sza.fastmediasorter.data.cloud

import androidx.annotation.Keep
import com.microsoft.identity.client.IAccount
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
/**
 * Pure helpers for OneDriveRestClient: MSAL account JSON serialization, Graph DriveItem JSON
 * parsing, and stored cloud-item-reference normalization (`cloud://onedrive/...` prefix strip).
 *
 * Extracted to keep OneDriveRestClient below the 1000-line cap.
 */
object OneDriveRestClientUtils {

/** Serialize an MSAL [IAccount] to a stable JSON envelope for persistence. */
    fun serializeAccount(account: IAccount): String =
        JSONObject().apply {
            put("username", account.username)
            put("id", account.id)
            put("authority", account.authority)
        }.toString()

    /**
     * Read the username from a serialized account JSON. Returns empty for legacy/malformed JSON
     * (treated as "no account stored" by callers).
     */
    fun deserializeAccount(json: String): String =
        try {
            val obj = JSONObject(json)
            if (obj.has("username")) obj.getString("username") else ""
        } catch (e: Exception) {
            Timber.e(e, "Failed to deserialize account")
            ""
        }

    /**
     * Strip the optional `cloud://onedrive/` scheme prefix from a stored reference. The DB
     * may persist either the bare item id ("01ABCDE..") or a fully scoped URI; downstream
     * Graph URL building expects the bare reference.
     */
    fun normalizeCloudItemReference(fileId: String): String =
        if (fileId.startsWith("cloud://onedrive/")) fileId.substringAfter("cloud://onedrive/") else fileId

    /** Map a Graph `value` array of DriveItems to [CloudFile]s under [parentPath]. */
    fun parseItems(items: JSONArray, parentPath: String): List<CloudFile> {
        val cloudFiles = mutableListOf<CloudFile>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            cloudFiles.add(parseItem(item, parentPath))
        }
        return cloudFiles
    }

    /** Map a single Graph DriveItem JSON to a [CloudFile]. */
    fun parseItem(item: JSONObject, parentPath: String): CloudFile {
        val id = item.getString("id")
        val name = item.getString("name")
        val isFolder = item.has("folder")
        val size = item.optLong("size", 0L)
        val modifiedTime = item.optString("lastModifiedDateTime", "")
        val mimeType: String? = item.optString("mimeType").takeIf { it.isNotEmpty() }

        // ISO 8601 → epoch ms (e.g. 2024-11-17T12:00:00Z); fall back to 0 if missing/malformed.
        val modifiedDate = try {
            if (modifiedTime.isNotEmpty()) java.time.Instant.parse(modifiedTime).toEpochMilli() else 0L
        } catch (_: Exception) {
            0L
        }

        // $expand=thumbnails returns a top-level array; we want item.thumbnails[0].large.url
        val thumbnailUrl = item.optJSONArray("thumbnails")
            ?.optJSONObject(0)
            ?.optJSONObject("large")
            ?.optString("url")
            ?.takeIf { it.isNotEmpty() }

        val webViewUrl: String? = item.optString("webUrl").takeIf { it.isNotEmpty() }

        return CloudFile(
            id = id,
            name = name,
            path = parentPath,
            isFolder = isFolder,
            size = size,
            modifiedDate = modifiedDate,
            mimeType = mimeType,
            thumbnailUrl = thumbnailUrl,
            webViewUrl = webViewUrl
        )
    }

    /** Internal envelope used by makeAuthenticatedRequest (kept here so callers can return it). */
    @Keep
    data class ApiResponse(
        val isSuccess: Boolean,
        val data: String?,
        val errorMessage: String?
    )
}
