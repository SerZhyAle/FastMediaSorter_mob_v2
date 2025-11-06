package com.sza.fastmediasorter.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.data.AppDatabase
import com.sza.fastmediasorter.data.ConnectionConfig
import com.sza.fastmediasorter.data.ConnectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ConnectionViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository: ConnectionRepository
    val allConfigs: LiveData<List<ConnectionConfig>>
    val sortDestinations: LiveData<List<ConnectionConfig>>
    val localCustomFolders: LiveData<List<ConnectionConfig>>

    init {
        val dao = AppDatabase.getDatabase(application).connectionConfigDao()
        repository = ConnectionRepository(dao)
        allConfigs = repository.allConfigs.asLiveData()
        sortDestinations = repository.sortDestinations.asLiveData()
        localCustomFolders = repository.localCustomFolders.asLiveData()
    }

    fun insertConfig(config: ConnectionConfig) =
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertConfig(config)
        }

    suspend fun insertConfigAndGetId(config: ConnectionConfig): Long {
        return repository.insertConfig(config)
    }

    fun updateConfig(config: ConnectionConfig) =
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateConfig(config)
        }

    fun deleteConfig(config: ConnectionConfig) =
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteConfig(config)
        }

    fun updateLastUsed(id: Long) =
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateLastUsed(id)
        }

    fun updateConfigInterval(
        id: Long,
        interval: Int,
    ) = viewModelScope.launch(Dispatchers.IO) {
        repository.updateConfigInterval(id, interval)
    }

    suspend fun getLastUsedConfig(): ConnectionConfig? {
        return repository.getLastUsedConfig()
    }

    suspend fun getConfigByName(name: String): ConnectionConfig? {
        return repository.getConfigByName(name)
    }

    suspend fun getConfigByFolderAddress(
        server: String,
        folder: String,
    ): ConnectionConfig? {
        return repository.getConfigByFolderAddress(server, folder)
    }

    suspend fun getConfigByFolderAddress(folderAddress: String): ConnectionConfig? {
        val parts = folderAddress.split("\\")
        if (parts.size < 2) return null
        val server = parts[0]
        val folder = parts.drop(1).joinToString("\\")
        return repository.getConfigByFolderAddress(server, folder)
    }

    suspend fun getConfigById(id: Long): ConnectionConfig? = repository.getConfigById(id)

    // Sort destinations methods
    fun addSortDestination(
        configId: Long,
        sortName: String,
    ) = viewModelScope.launch(Dispatchers.IO) {
        repository.addSortDestination(configId, sortName)
    }

    fun removeSortDestination(configId: Long) =
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeSortDestination(configId)
        }

    fun moveSortDestination(
        config: ConnectionConfig,
        direction: Int,
    ) = viewModelScope.launch(Dispatchers.IO) {
        repository.moveSortDestination(config, direction)
    }

    suspend fun getSortDestinationsCount(): Int = repository.getSortDestinationsCount()

    // Local folders methods
    fun addLocalCustomFolder(
        folderName: String,
        folderUri: String,
    ) = viewModelScope.launch(Dispatchers.IO) {
        repository.addLocalCustomFolder(folderName, folderUri)
    }

    // Auto-add local folders to sort destinations
    fun autoAddLocalFoldersAsSortDestinations() =
        viewModelScope.launch(Dispatchers.IO) {
            repository.autoAddLocalFoldersAsSortDestinations()
        }

    // Ensure standard local folders are in database
    fun ensureStandardLocalFoldersInDatabase() =
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureStandardLocalFoldersInDatabase()
        }

    // Fix SMB write permissions for existing connections
    fun fixSmbWritePermissions() =
        viewModelScope.launch(Dispatchers.IO) {
            repository.fixSmbWritePermissions()
        }
}
