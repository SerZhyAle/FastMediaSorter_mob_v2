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
    
    @Query("SELECT * FROM network_credentials WHERE credentialId = :credentialId")
    suspend fun getCredentialsById(credentialId: String): NetworkCredentialsEntity?
    
    @Query("SELECT * FROM network_credentials WHERE server = :server AND shareName = :shareName LIMIT 1")
    suspend fun getByServerAndShare(server: String, shareName: String): NetworkCredentialsEntity?
    
    @Query("SELECT * FROM network_credentials WHERE type = :type AND server = :server AND port = :port LIMIT 1")
    suspend fun getByTypeServerAndPort(type: String, server: String, port: Int): NetworkCredentialsEntity?
    
    @Query("SELECT * FROM network_credentials WHERE server = :host LIMIT 1")
    suspend fun getCredentialsByHost(host: String): NetworkCredentialsEntity?
    
    @Query("SELECT * FROM network_credentials WHERE type = :type")
    fun getCredentialsByType(type: String): Flow<List<NetworkCredentialsEntity>>
    
    @Query("SELECT * FROM network_credentials")
    fun getAllCredentials(): Flow<List<NetworkCredentialsEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(credentials: NetworkCredentialsEntity): Long
    
    @Update
    suspend fun update(credentials: NetworkCredentialsEntity)
    
    @Query("DELETE FROM network_credentials WHERE credentialId = :credentialId")
    suspend fun deleteByCredentialId(credentialId: String)
    
    @Query("DELETE FROM network_credentials")
    suspend fun deleteAll()
}
