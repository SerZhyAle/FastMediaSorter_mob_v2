package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for network credentials operations
 */
@Dao
interface NetworkCredentialsDao {

    @Query("SELECT * FROM network_credentials WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NetworkCredentialsEntity?
    
    @Query("SELECT * FROM network_credentials WHERE credentialId = :credentialId")
    suspend fun getCredentialsById(credentialId: String): NetworkCredentialsEntity?
    
    // Match server stored with or without port suffix (e.g. "192.168.1.110" or "192.168.1.110:445").
    @Query("SELECT * FROM network_credentials WHERE (server = :server OR server LIKE :server || ':%') AND shareName = :shareName LIMIT 1")
    suspend fun getByServerAndShare(server: String, shareName: String): NetworkCredentialsEntity?
    
    @Query("SELECT * FROM network_credentials WHERE type = :type COLLATE NOCASE AND server = :server AND port = :port LIMIT 1")
    suspend fun getByTypeServerAndPort(type: String, server: String, port: Int): NetworkCredentialsEntity?

    /** S0064: returns ALL credentials for a given type+server+port (for building manual-name history). */
    @Query("SELECT * FROM network_credentials WHERE type = :type COLLATE NOCASE AND server = :server AND port = :port")
    suspend fun getAllByTypeServerAndPort(type: String, server: String, port: Int): List<NetworkCredentialsEntity>
    
    @Query("SELECT * FROM network_credentials WHERE server = :host LIMIT 1")
    suspend fun getCredentialsByHost(host: String): NetworkCredentialsEntity?
    
    @Query("SELECT * FROM network_credentials WHERE type = :type COLLATE NOCASE AND accountId = :accountId LIMIT 1")
    suspend fun getByTypeAndAccountId(type: String, accountId: String): NetworkCredentialsEntity?
    
    @Query("SELECT * FROM network_credentials WHERE type = :type COLLATE NOCASE")
    fun getCredentialsByType(type: String): Flow<List<NetworkCredentialsEntity>>
    
    @Query("SELECT * FROM network_credentials")
    fun getAllCredentials(): Flow<List<NetworkCredentialsEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(credentials: NetworkCredentialsEntity): Long
    
    @Update
    suspend fun update(credentials: NetworkCredentialsEntity)
    
    @Query("DELETE FROM network_credentials WHERE credentialId = :credentialId")
    suspend fun deleteByCredentialId(credentialId: String)

    /** S0200 Phase 05: bulk delete credentials by [type]. Used by [S0200AuthStateWipe] for `GOOGLE_DRIVE`. */
    @Query("DELETE FROM network_credentials WHERE type = :type COLLATE NOCASE")
    suspend fun deleteByType(type: String)

    @Query("DELETE FROM network_credentials")
    suspend fun deleteAll()

    /**
     * Returns credentials that have no associated resources (B5: orphan audit).
     * Returns credential IDs not referenced by any resource's credentialsId.
     */
    @Query(
        """SELECT nc.* FROM network_credentials nc
        WHERE nc.credentialId NOT IN
            (SELECT DISTINCT credentialsId FROM resources WHERE credentialsId IS NOT NULL AND credentialsId != '')"""
    )
    suspend fun getOrphanedCredentials(): List<NetworkCredentialsEntity>
}
