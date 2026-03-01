package com.sza.fastmediasorter.domain.usecase

object DeletePathPolicy {
    fun canUseSoftDelete(path: String): Boolean {
        val normalizedPath = path.replace('\\', '/')
        val isContentUri = normalizedPath.startsWith("content:/", ignoreCase = true) ||
            normalizedPath.startsWith("content://", ignoreCase = true)
        val isNetworkOrCloud = normalizedPath.startsWith("smb://", ignoreCase = true) ||
            normalizedPath.startsWith("sftp://", ignoreCase = true) ||
            normalizedPath.startsWith("ftp://", ignoreCase = true) ||
            normalizedPath.startsWith("cloud://", ignoreCase = true)
        val isProtectedAndroidMediaPath = normalizedPath.contains("/Android/media/", ignoreCase = true)

        return !isContentUri && !isNetworkOrCloud && !isProtectedAndroidMediaPath
    }

    fun canUseSoftDelete(paths: List<String>): Boolean {
        return paths.all(::canUseSoftDelete)
    }
}
