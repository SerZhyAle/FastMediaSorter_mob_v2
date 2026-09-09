package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearStreamCollection
import kotlinx.coroutines.flow.Flow

/**
 * S2669: the curated collections delivered with the stream catalog, as the watch stores them -
 * one JSON file beside the channel file, no database. Whole-list read and whole-list save only:
 * the collections are catalog-owned (the user authors nothing in them), so an import replaces the
 * stored set and no per-collection write exists (strategic ADR-3).
 */
interface WearStreamCollectionRepository {

    fun observeCollections(): Flow<List<WearStreamCollection>>

    suspend fun saveAll(collections: List<WearStreamCollection>)
}
