package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity

/**
 * Repository для работы с учётными данными сетевых ресурсов (SMB/SFTP).
 */
interface NetworkCredentialsRepository {
    suspend fun insert(credentials: NetworkCredentialsEntity): Long
    suspend fun getById(id: Long): NetworkCredentialsEntity?
    suspend fun getByCredentialId(credentialId: String): NetworkCredentialsEntity?
    suspend fun getByTypeServerAndPort(type: String, server: String, port: Int): NetworkCredentialsEntity?
    suspend fun update(credentials: NetworkCredentialsEntity)
    suspend fun delete(credentials: NetworkCredentialsEntity)
    fun getAllCredentials(): kotlinx.coroutines.flow.Flow<List<NetworkCredentialsEntity>>
}
