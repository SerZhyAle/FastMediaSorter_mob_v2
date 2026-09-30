package com.sza.fastmediasorter.data.cloud

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure helpers for GoogleDriveRestClient: Drive `files.list` JSON → [CloudFile] mapping
 * (RFC 3339 modifiedTime → epoch ms, thumbnail/webView extraction, MIME-folder detection).
 *
 * Extracted to keep GoogleDriveRestClient below the 1000-line cap.
 */
object GoogleDriveRestClientUtils {

    private const val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"

    /**
     * The content fallback reads the whole body into the heap, so it is bounded far below the
     * LOW memory tier's 512 MB heap; a larger image simply shows no preview.
     */
    const val THUMBNAIL_CONTENT_FALLBACK_MAX_BYTES = 16L * 1024 * 1024

    /**
     * Whether [file]'s own content may stand in for a thumbnail Drive did not generate.
     * Only a decodable image of known, bounded size qualifies: a video would be downloaded
     * whole for nothing, and a native Google document reports size 0.
     */
    fun shouldDownloadContentAsThumbnail(file: CloudFile): Boolean {
        val isImage = file.mimeType?.startsWith("image/") == true
        return isImage && file.size in 1..THUMBNAIL_CONTENT_FALLBACK_MAX_BYTES
    }

    /** Map a Drive `files` JSON array to [CloudFile]s under [parentPath]. */
    fun parseItems(items: JSONArray, parentPath: String): List<CloudFile> {
        val cloudFiles = mutableListOf<CloudFile>()
        for (i in 0 until items.length()) {
            cloudFiles.add(parseItem(items.getJSONObject(i), parentPath))
        }
        return cloudFiles
    }

    /** Map a single Drive File JSON to a [CloudFile]. */
    fun parseItem(item: JSONObject, parentPath: String): CloudFile {
        val id = item.getString("id")
        val name = item.getString("name")
        val mimeType: String? = item.optString("mimeType").takeIf { it.isNotEmpty() }
        val isFolder = mimeType == MIME_TYPE_FOLDER
        val size = item.optLong("size", 0L)
        val modifiedTime = item.optString("modifiedTime", "")

        // RFC 3339 → epoch ms (e.g. 2024-11-17T12:00:00.000Z); fall back to 0 if missing/malformed.
        val modifiedDate = try {
            if (modifiedTime.isNotEmpty()) java.time.Instant.parse(modifiedTime).toEpochMilli() else 0L
        } catch (_: Exception) {
            0L
        }

        val thumbnailUrl: String? = item.optString("thumbnailLink").takeIf { it.isNotEmpty() }
        val webViewUrl: String? = item.optString("webViewLink").takeIf { it.isNotEmpty() }
        // Present only when the request asked for `parents`; a file shared with the account has none.
        val parentId: String? = item.optJSONArray("parents")?.optString(0)?.takeIf { it.isNotEmpty() }

        return CloudFile(
            id = id,
            name = name,
            path = "$parentPath/$name",
            isFolder = isFolder,
            size = size,
            modifiedDate = modifiedDate,
            mimeType = mimeType,
            thumbnailUrl = thumbnailUrl,
            webViewUrl = webViewUrl,
            parentId = parentId
        )
    }
}
