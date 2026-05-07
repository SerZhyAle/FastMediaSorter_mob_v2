package com.sza.fastmediasorter.domain.repository

interface ContextualRationaleRepository {
    fun isShown(permissionId: String): Boolean
    fun markShown(permissionId: String)
}
