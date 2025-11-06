package com.sza.fastmediasorter.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ConnectionRepository(
    private val dao: ConnectionConfigDao,
) {
    val allConfigs: Flow<List<ConnectionConfig>> = dao.getAllConfigs()
    val sortDestinations: Flow<List<ConnectionConfig>> = dao.getSortDestinations()

    suspend fun getConfigById(id: Long): ConnectionConfig? {
        return dao.getConfigById(id)
    }

    suspend fun getConfigByName(name: String): ConnectionConfig? {
        return dao.getConfigByName(name)
    }

    suspend fun getConfigByFolderAddress(
        server: String,
        folder: String,
    ): ConnectionConfig? {
        return dao.getConfigByFolderAddress(server, folder)
    }

    suspend fun getLastUsedConfig(): ConnectionConfig? {
        return dao.getLastUsedConfig()
    }

    suspend fun insertConfig(config: ConnectionConfig): Long {
        return dao.insertConfig(config)
    }

    suspend fun updateConfig(config: ConnectionConfig) {
        dao.updateConfig(config)
    }

    suspend fun deleteConfig(config: ConnectionConfig) {
        dao.deleteConfig(config)
    }

    suspend fun deleteConfigById(id: Long) {
        dao.deleteConfigById(id)
    }

    suspend fun updateLastUsed(id: Long) {
        dao.updateLastUsed(id, System.currentTimeMillis())
    }

    suspend fun updateConfigInterval(
        id: Long,
        interval: Int,
    ) {
        dao.updateConfigInterval(id, interval)
    }

    // Sort destinations methods
    suspend fun addSortDestination(
        configId: Long,
        sortName: String,
    ) {
        // Validate that config is not a local folder
        val config = dao.getConfigById(configId)
        if (config?.type == "LOCAL_CUSTOM") {
            return // Silently ignore local folders
        }

        val maxOrder = dao.getMaxSortOrder() ?: -1
        val newOrder = maxOrder + 1
        if (newOrder <= 9) {
            dao.updateSortDestination(configId, newOrder, sortName)
        }
    }

    suspend fun removeSortDestination(configId: Long) {
        val config = dao.getConfigById(configId)
        config?.sortOrder?.let { removedOrder ->
            dao.removeSortDestination(configId)

            // Reorder remaining destinations
            val destinations = dao.getSortDestinations().first()
            destinations.filter { it.sortOrder!! > removedOrder }.forEach { dest ->
                dao.updateSortDestination(dest.id, dest.sortOrder!! - 1, dest.sortName)
            }
        }
    }

    suspend fun moveSortDestination(
        config: ConnectionConfig,
        direction: Int,
    ) {
        val currentOrder = config.sortOrder ?: return
        val newOrder = currentOrder + direction

        if (newOrder < 0) return

        val destinations = dao.getSortDestinations().first()
        if (newOrder >= destinations.size) return

        // Swap with target position
        val targetConfig = destinations.find { it.sortOrder == newOrder }
        if (targetConfig != null) {
            dao.updateSortDestination(config.id, newOrder, config.sortName)
            dao.updateSortDestination(targetConfig.id, currentOrder, targetConfig.sortName)
        }
    }

    suspend fun getSortDestinationsCount(): Int {
        return dao.getSortDestinations().first().size
    }

    // Local folders methods
    val localCustomFolders: Flow<List<ConnectionConfig>> = dao.getLocalCustomFolders()

    suspend fun addLocalCustomFolder(
        folderName: String,
        folderUri: String,
    ): Long {
        val existing = dao.getLocalFolderByName(folderName)
        if (existing != null) {
            return existing.id
        }
        val config =
            ConnectionConfig(
                id = 0,
                name = folderName,
                serverAddress = "",
                username = "",
                password = "",
                folderPath = "",
                interval = 10,
                lastUsed = System.currentTimeMillis(),
                type = "LOCAL_CUSTOM",
                localUri = folderUri,
                localDisplayName = folderName,
                writePermission = true,
            )
        return dao.insertConfig(config)
    }

    suspend fun ensureStandardLocalFoldersInDatabase(): List<Long> {
        val standardFolders = listOf("Camera", "Screenshots", "Pictures", "Download")
        val addedIds = mutableListOf<Long>()

        standardFolders.forEach { folderName ->
            // Check if folder already exists in database
            val existing = dao.getLocalFolderByName(folderName)
            if (existing == null) {
                // Add to database
                val config =
                    ConnectionConfig(
                        id = 0,
                        name = folderName,
                        serverAddress = "",
                        username = "",
                        password = "",
                        folderPath = "",
                        interval = 10,
                        lastUsed = 0,
                        type = "LOCAL_STANDARD",
                        localUri = "",
                        localDisplayName = folderName,
                        writePermission = true,
                    )
                val newId = dao.insertConfig(config)
                addedIds.add(newId)
            }
        }

        return addedIds
    }

    suspend fun autoAddLocalFoldersAsSortDestinations() {
        // Get current sort destinations
        val currentDestinations = dao.getSortDestinations().first()

        // Only auto-add if destinations list is empty (first initialization)
        if (currentDestinations.isNotEmpty()) {
            return
        }

        // Get Camera and Download folders only (both standard and custom) with write permission
        val allConfigs = dao.getAllConfigs().first()
        val localFolders =
            allConfigs.filter {
                (it.type == "LOCAL_STANDARD" || it.type == "LOCAL_CUSTOM") &&
                    it.writePermission &&
                    (it.localDisplayName == "Camera" || it.localDisplayName == "Download")
            }

        // Add local folders to destinations (max 10)
        var nextOrder = 0
        localFolders.take(10).forEach { folder ->
            dao.updateSortDestination(
                folder.id,
                nextOrder,
                folder.localDisplayName ?: folder.name,
            )
            nextOrder++
        }
    }

    suspend fun fixSmbWritePermissions() {
        dao.fixSmbWritePermissions()
    }
}
