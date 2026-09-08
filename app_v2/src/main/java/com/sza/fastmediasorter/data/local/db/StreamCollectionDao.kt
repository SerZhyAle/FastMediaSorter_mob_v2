package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * S2669: curated collections and their membership.
 *
 * The only write path is [replaceAll]. Collections are catalog-owned and the user authors nothing in
 * them, so an import replaces the stored set whole rather than merging it - the merge in
 * `StreamSourceRepository` is complicated precisely because it protects user rows, and reproducing that
 * complexity here would add risk while protecting nothing. Individual inserts and deletes are therefore
 * deliberately absent from this interface rather than merely unused.
 */
@Dao
interface StreamCollectionDao {

    @Query("SELECT * FROM stream_collections ORDER BY sortOrder ASC, collectionId ASC")
    fun observeCollections(): Flow<List<StreamCollectionEntity>>

    @Query("SELECT COUNT(*) FROM stream_collections")
    suspend fun countCollections(): Int

    @Query("SELECT * FROM stream_collection_members WHERE collectionId = :collectionId ORDER BY sortOrder ASC")
    fun observeMembers(collectionId: String): Flow<List<StreamCollectionMemberEntity>>

    @Query("SELECT * FROM stream_collection_members WHERE collectionId = :collectionId ORDER BY sortOrder ASC")
    suspend fun membersOf(collectionId: String): List<StreamCollectionMemberEntity>

    @Transaction
    suspend fun replaceAll(
        collections: List<StreamCollectionEntity>,
        members: List<StreamCollectionMemberEntity>
    ) {
        deleteAllMembers()
        deleteAllCollections()
        insertCollections(collections)
        insertMembers(members)
    }

    @Query("DELETE FROM stream_collections")
    suspend fun deleteAllCollections()

    @Query("DELETE FROM stream_collection_members")
    suspend fun deleteAllMembers()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollections(collections: List<StreamCollectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<StreamCollectionMemberEntity>)
}
