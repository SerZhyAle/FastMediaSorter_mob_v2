package com.sza.fastmediasorter.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.core.util.DestinationColors
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.ExportSettingsUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.ImportSettingsUseCase
import com.sza.fastmediasorter.domain.usecase.UpdateResourceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val settingsRepository: SettingsRepository,
    val resourceRepository: ResourceRepository,
    val credentialsRepository: NetworkCredentialsRepository,
    private val getDestinationsUseCase: GetDestinationsUseCase,
    val getResourcesUseCase: GetResourcesUseCase,
    private val updateResourceUseCase: UpdateResourceUseCase,
    val exportSettingsUseCase: ExportSettingsUseCase,
    val importSettingsUseCase: ImportSettingsUseCase
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.getSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings()
        )

    val destinations: StateFlow<List<MediaResource>> = getDestinationsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateSettings(settings: AppSettings) {
        viewModelScope.launch {
            try {
                settingsRepository.updateSettings(settings)
                // Settings updated
            } catch (e: Exception) {
                Timber.e(e, "Error updating settings")
            }
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            try {
                settingsRepository.resetToDefaults()
                // Settings reset
            } catch (e: Exception) {
                Timber.e(e, "Error resetting settings")
            }
        }
    }
    
    fun resetPlayerFirstRun() {
        viewModelScope.launch {
            try {
                settingsRepository.setPlayerFirstRun(true)
                // Player first-run flag reset
            } catch (e: Exception) {
                Timber.e(e, "Error resetting player first-run flag")
            }
        }
    }

    fun moveDestination(resource: MediaResource, direction: Int) {
        viewModelScope.launch {
            try {
                val allDestinations = destinations.value
                val currentIndex = allDestinations.indexOfFirst { it.id == resource.id }
                if (currentIndex == -1) return@launch
                
                val targetIndex = currentIndex + direction
                if (targetIndex < 0 || targetIndex >= allDestinations.size) return@launch
                
                val targetResource = allDestinations[targetIndex]
                
                // Swap destination orders
                val currentOrder = resource.destinationOrder ?: return@launch
                val targetOrder = targetResource.destinationOrder ?: return@launch
                
                // Wait for both updates to complete before continuing
                val result1 = updateResourceUseCase(resource.copy(destinationOrder = targetOrder))
                val result2 = updateResourceUseCase(targetResource.copy(destinationOrder = currentOrder))
                
                if (result1.isSuccess && result2.isSuccess) {
                    Timber.d("Destination moved successfully: ${resource.name} order $currentOrder→$targetOrder, ${targetResource.name} order $targetOrder→$currentOrder")
                } else {
                    Timber.e("Error moving destination: result1=${result1.exceptionOrNull()}, result2=${result2.exceptionOrNull()}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error moving destination")
            }
        }
    }

    fun removeDestination(resource: MediaResource) {
        viewModelScope.launch {
            try {
                updateResourceUseCase(resource.copy(
                    isDestination = false,
                    destinationOrder = null
                ))
                Timber.d("Destination removed successfully")
            } catch (e: Exception) {
                Timber.e(e, "Error removing destination")
            }
        }
    }

    suspend fun getWritableNonDestinationResources(): List<MediaResource> {
        return try {
            // Get fresh data from database, not from cached stateIn()
            val allResources = withContext(Dispatchers.IO) {
                getResourcesUseCase().first()
            }
            allResources.filter { it.isWritable && !it.isDestination }
        } catch (e: Exception) {
            Timber.e(e, "Error getting writable resources")
            emptyList()
        }
    }

    fun addDestination(resource: MediaResource) {
        viewModelScope.launch {
            try {
                // Double-check resource is writable before adding to destinations
                if (!resource.isWritable) {
                    Timber.w("Cannot add non-writable resource as destination: ${resource.name}")
                    return@launch
                }
                
                val nextOrder = getDestinationsUseCase.getNextAvailableOrder()
                if (nextOrder == -1) {
                    Timber.w("Cannot add destination: all 10 slots are full")
                    return@launch
                }
                
                val color = DestinationColors.getColorForDestination(nextOrder)
                updateResourceUseCase(resource.copy(
                    isDestination = true,
                    destinationOrder = nextOrder,
                    destinationColor = color
                ))
                Timber.d("Destination added successfully with order $nextOrder and color $color")
            } catch (e: Exception) {
                Timber.e(e, "Error adding destination")
            }
        }
    }

    fun updateDestinationColor(resource: MediaResource, color: Int) {
        viewModelScope.launch {
            try {
                updateResourceUseCase(resource.copy(destinationColor = color))
                Timber.d("Destination color updated successfully")
            } catch (e: Exception) {
                Timber.e(e, "Error updating destination color")
            }
        }
    }

    // Methods for importing test credentials/resources
    fun addCredentials(credentials: com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity) {
        viewModelScope.launch {
            try {
                credentialsRepository.insert(credentials)
                Timber.d("Added credentials: ${credentials.username}@${credentials.server}")
            } catch (e: Exception) {
                Timber.e(e, "Error adding credentials")
            }
        }
    }

    fun getAllCredentials() = credentialsRepository.getAllCredentials()

    fun addResourceDirectly(resource: MediaResource) {
        viewModelScope.launch {
            try {
                resourceRepository.addResource(resource)
                Timber.d("Added resource: ${resource.name}")
            } catch (e: Exception) {
                Timber.e(e, "Error adding resource")
            }
        }
    }
    fun importSzaResources(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Get XML parser
                val parser = context.resources.getXml(com.sza.fastmediasorter.R.xml.sza_resources)
                var eventType = parser.eventType
                
                // Get existing data to prevent duplicates
                val existingResources = resourceRepository.getAllResourcesSync()
                val existingCredentials = credentialsRepository.getAllCredentials().first()
                
                while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "resource") {
                        try {
                            // Extract attributes
                            val name = parser.getAttributeValue(null, "name") ?: "Unknown"
                            val path = parser.getAttributeValue(null, "path") ?: ""
                            val typeStr = parser.getAttributeValue(null, "type") ?: "LOCAL"
                            val username = parser.getAttributeValue(null, "username")
                            val password = parser.getAttributeValue(null, "password")
                            val pin = parser.getAttributeValue(null, "pin")
                            val addToDestinations = parser.getAttributeValue(null, "addToDestinations")?.toBoolean() ?: false
                            val allFiles = parser.getAttributeValue(null, "allFiles")?.toBoolean() ?: false
                            val supportedTypesStr = parser.getAttributeValue(null, "supportedMediaTypes")
                            val sortModeStr = parser.getAttributeValue(null, "sortMode")
                            val displayModeStr = parser.getAttributeValue(null, "displayMode")
                            val scanSubdirs = parser.getAttributeValue(null, "scanSubdirectories")?.toBoolean() ?: false
                            val disableThumbnails = parser.getAttributeValue(null, "disableThumbnails")?.toBoolean() ?: false
                            val showHidden = parser.getAttributeValue(null, "showHiddenFiles")?.toBoolean() ?: false
                            
                            // Check if resource exists
                            if (existingResources.any { it.path == path }) {
                                Timber.d("Resource already exists: $path")
                            } else {
                                val type = try {
                                    com.sza.fastmediasorter.domain.model.ResourceType.valueOf(typeStr)
                                } catch (e: Exception) {
                                    com.sza.fastmediasorter.domain.model.ResourceType.LOCAL
                                }
                                
                                // Handle Credentials
                                var credId: String? = null
                                if (!username.isNullOrEmpty() && (type == com.sza.fastmediasorter.domain.model.ResourceType.SMB || type == com.sza.fastmediasorter.domain.model.ResourceType.SFTP || type == com.sza.fastmediasorter.domain.model.ResourceType.FTP)) {
                                    // Parse server from URI
                                    // java.net.URI can be strict, so we parse manually or use utils
                                    val server = if (type == com.sza.fastmediasorter.domain.model.ResourceType.SMB) {
                                        com.sza.fastmediasorter.utils.SmbPathUtils.extractServer(path) ?: ""
                                    } else if (type == com.sza.fastmediasorter.domain.model.ResourceType.FTP) {
                                        com.sza.fastmediasorter.utils.FtpPathUtils.parseFtpPath(path)?.host ?: ""
                                    } else if (type == com.sza.fastmediasorter.domain.model.ResourceType.SFTP) {
                                        com.sza.fastmediasorter.utils.SftpPathUtils.parseSftpPath(path)?.host ?: ""
                                    } else {
                                        // Fallback
                                        try {
                                            java.net.URI(path).host ?: ""
                                        } catch (e: Exception) { "" }
                                    }
                                    
                                    // Check existing credentials
                                    // Filter existing credentials list for match
                                    val existingCred = existingCredentials.find { 
                                        it.server == server && it.username == username && it.type == typeStr
                                    }
                                    
                                    if (existingCred != null) {
                                        credId = existingCred.credentialId
                                    } else {
                                        // Create new
                                        val newCredId = java.util.UUID.randomUUID().toString()
                                        val port = if (type == com.sza.fastmediasorter.domain.model.ResourceType.SFTP) 22 else if (type == com.sza.fastmediasorter.domain.model.ResourceType.FTP) 21 else 445
                                        val newCred = com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity.create(
                                            credentialId = newCredId,
                                            type = typeStr,
                                            server = server,
                                            port = port,
                                            username = username,
                                            plaintextPassword = password ?: ""
                                        )
                                        credentialsRepository.insert(newCred)
                                        credId = newCredId
                                        Timber.d("Created credential for $username@$server")
                                    }
                                }
                                
                                // Parse other fields
                                val mediaTypes = if (supportedTypesStr != null) {
                                    supportedTypesStr.split(",").mapNotNull { 
                                        try {
                                            com.sza.fastmediasorter.domain.model.MediaType.valueOf(it.trim())
                                        } catch (e: Exception) { null }
                                    }.toSet()
                                } else {
                                    setOf(com.sza.fastmediasorter.domain.model.MediaType.IMAGE, com.sza.fastmediasorter.domain.model.MediaType.VIDEO)
                                }
                                
                                val sortMode = try {
                                    com.sza.fastmediasorter.domain.model.SortMode.valueOf(sortModeStr ?: "NAME_ASC")
                                } catch (e: Exception) { com.sza.fastmediasorter.domain.model.SortMode.NAME_ASC }
                                
                                val displayMode = try {
                                    com.sza.fastmediasorter.domain.model.DisplayMode.valueOf(displayModeStr ?: "LIST")
                                } catch (e: Exception) { com.sza.fastmediasorter.domain.model.DisplayMode.LIST }
                                
                                // Destination logic
                                var isDest = false
                                var destOrder: Int? = null
                                var destColor = 0
                                
                                if (addToDestinations) {
                                    val nextOrder = getDestinationsUseCase.getNextAvailableOrder()
                                    if (nextOrder != -1) {
                                        isDest = true
                                        destOrder = nextOrder
                                        destColor = DestinationColors.getColorForDestination(nextOrder)
                                    }
                                }
                                
                                val newResource = MediaResource(
                                    name = name,
                                    path = path,
                                    type = type,
                                    credentialsId = credId,
                                    supportedMediaTypes = mediaTypes,
                                    sortMode = sortMode,
                                    displayMode = displayMode,
                                    scanSubdirectories = scanSubdirs,
                                    disableThumbnails = disableThumbnails,
                                    allFiles = allFiles,
                                    showHiddenFiles = showHidden,
                                    accessPin = pin,
                                    isDestination = isDest,
                                    destinationOrder = destOrder,
                                    destinationColor = destColor,
                                    isWritable = true,
                                    isAvailable = true
                                )
                                
                                resourceRepository.addResource(newResource)
                                Timber.d("Added SZA resource: $name")
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error parsing resource entry")
                        }
                    }
                    eventType = parser.next()
                }
                
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "SZA resources imported", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error importing SZA resources")
                 withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Error importing resources", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
