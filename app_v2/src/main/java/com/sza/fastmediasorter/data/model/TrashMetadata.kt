package com.sza.fastmediasorter.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Metadata for files stored in .trash folder.
 * Stores information needed to restore deleted files.
 */
data class TrashMetadata(
    val originalPath: String,           // Full path where file was located
    val resourceId: Long,                // ID of the resource
    val resourceType: String,            // "Local", "SMB", "SFTP", "FTP", "Cloud"
    val deletedFiles: List<String>,      // Names of files in .trash
    val deletionTimestamp: Long,         // When files were deleted (System.currentTimeMillis())
    val isDirectory: Boolean = false     // Whether this was a directory or single file(s)
) {
    /**
     * Serialize metadata to JSON string.
     */
    fun toJson(): String {
        val json = JSONObject()
        json.put("originalPath", originalPath)
        json.put("resourceId", resourceId)
        json.put("resourceType", resourceType)
        json.put("deletionTimestamp", deletionTimestamp)
        json.put("isDirectory", isDirectory)
        
        val filesArray = JSONArray()
        deletedFiles.forEach { filesArray.put(it) }
        json.put("deletedFiles", filesArray)
        
        return json.toString(2) // Pretty print with indent
    }

    companion object {
        /**
         * Deserialize metadata from JSON string.
         */
        fun fromJson(json: String): TrashMetadata {
            val jsonObject = JSONObject(json)
            
            val filesArray = jsonObject.getJSONArray("deletedFiles")
            val filesList = mutableListOf<String>()
            for (i in 0 until filesArray.length()) {
                filesList.add(filesArray.getString(i))
            }
            
            return TrashMetadata(
                originalPath = jsonObject.getString("originalPath"),
                resourceId = jsonObject.getLong("resourceId"),
                resourceType = jsonObject.getString("resourceType"),
                deletedFiles = filesList,
                deletionTimestamp = jsonObject.getLong("deletionTimestamp"),
                isDirectory = jsonObject.optBoolean("isDirectory", false)
            )
        }
    }
}
