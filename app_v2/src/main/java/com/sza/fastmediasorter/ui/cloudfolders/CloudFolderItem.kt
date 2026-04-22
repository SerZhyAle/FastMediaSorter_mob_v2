package com.sza.fastmediasorter.ui.cloudfolders

data class CloudFolderItem(
    val id: String,
    val name: String,
    val mimeType: String?,
    val isSelected: Boolean = false
)
