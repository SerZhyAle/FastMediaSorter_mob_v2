package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity

/**
 * Repository for handling network resource credentials (SMB/SFTP).
 */
interface NetworkCredentialsRepository {
    suspend fun insert(credentials: NetworkCredentialsEntity): Long
    suspend fun getById(id: Long): NetworkCredentialsEntity?
    suspend fun getByCredentialId(credentialId: String): NetworkCredentialsEntity?
    suspend fun getByTypeServerAndPort(type: String, server: String, port: Int): NetworkCredentialsEntity?
    suspend fun getByServerAndShare(server: String, shareName: String): NetworkCredentialsEntity?
    suspend fun getCredentialsByHost(host: String): NetworkCredentialsEntity?
    suspend fun getByTypeAndAccountId(type: String, accountId: String): NetworkCredentialsEntity?
    suspend fun update(credentials: NetworkCredentialsEntity)
    suspend fun delete(credentials: NetworkCredentialsEntity)
    fun getAllCredentials(): kotlinx.coroutines.flow.Flow<List<NetworkCredentialsEntity>>
    /** Returns credentials that have no associated resources (orphaned). */
    suspend fun getOrphanedCredentials(): List<NetworkCredentialsEntity>
    /**
     * S0064: Returns deduplicated list of share names previously used on [server]:[port].
     * Combines both auto-saved share names (from credentials) and manually entered names
     * stored in [NetworkCredentialsEntity.manualShareNames].
     */
    suspend fun getManualShareNamesForServer(server: String, port: Int): List<String>
    /**
     * S0064: Persists [shareName] to the manual-name history for [server]:[port].
     * Updates the first credential found for that server; silently no-ops if none exists yet.
     */
    suspend fun addManualShareName(server: String, port: Int, shareName: String)
}
