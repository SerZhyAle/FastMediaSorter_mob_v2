package com.sza.fastmediasorter.domain.repository

/**
 * Records that the system permission dialog was fired for a permission entry. The platform answers
 * identically before the first request and after a permanent denial, so this marker is the only way
 * to tell a never-requested permission from a permanently denied one.
 */
interface PermissionRequestMarkerRepository {
    fun wasRequested(permissionId: String): Boolean
    fun markRequested(permissionId: String)
}
