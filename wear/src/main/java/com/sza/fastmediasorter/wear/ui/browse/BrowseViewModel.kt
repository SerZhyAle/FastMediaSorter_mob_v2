package com.sza.fastmediasorter.wear.ui.browse

import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.data.network.smb.SmbDataSource
import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearMediaRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the browse screen.
 * Handles loading media files from both local MediaStore and SMB network sources.
 */
@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val mediaRepository: WearMediaRepository,
    private val preferencesRepository: WearPreferencesRepository,
    private val smbDataSource: SmbDataSource,
    private val networkSourceRepository: NetworkSourceRepository,
    private val selectedMediaManager: SelectedMediaManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<BrowseUiState>(BrowseUiState.Loading)
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()
    
    private val _selectedFile = MutableStateFlow<WearMediaFile?>(null)
    val selectedFile: StateFlow<WearMediaFile?> = _selectedFile.asStateFlow()
    
    // Navigation arguments - to be set from UI layer
    private var _mediaType: MediaType = MediaType.MUSIC
    private var _sourceId: String? = null
    private var _sourceName: String? = null
    
    val mediaType: MediaType get() = _mediaType
    val isNetworkSource: Boolean get() = _sourceId != null
    val sourceName: String? get() = _sourceName
    
    /**
     * Initialize navigation arguments. Call this from the composable.
     */
    fun setNavigationArgs(mediaType: MediaType, sourceId: String? = null, sourceName: String? = null) {
        _mediaType = mediaType
        _sourceId = sourceId
        _sourceName = sourceName
    }
    
    init {
        Timber.d("BrowseViewModel initialized")
        // loadMediaFiles() will be called after setNavigationArgs() from UI
    }
    
    fun loadMediaFiles() {
        viewModelScope.launch {
            _uiState.value = BrowseUiState.Loading
            
            if (isNetworkSource && _sourceId != null) {
                // Load from network source
                loadNetworkFiles(_sourceId!!)
            } else {
                // Load from local storage
                loadLocalFiles()
            }
        }
    }
    
    private suspend fun loadLocalFiles() {
        // Check if media type is enabled in settings
        val isEnabled = when (mediaType) {
            MediaType.MUSIC -> preferencesRepository.isAudioEnabled.first()
            MediaType.VIDEO -> preferencesRepository.isVideoEnabled.first()
            MediaType.PHOTO -> preferencesRepository.isImagesEnabled.first()
        }
        
        if (!isEnabled) {
            _uiState.value = BrowseUiState.Empty("This media type is disabled in settings")
            return
        }
        
        mediaRepository.getMediaFiles(mediaType)
            .catch { e ->
                Timber.e(e, "Error loading local media files")
                _uiState.value = BrowseUiState.Error(e.message ?: "Unknown error")
            }
            .collect { result ->
                result.fold(
                    onSuccess = { files ->
                        _uiState.value = if (files.isEmpty()) {
                            BrowseUiState.Empty("No ${mediaType.name.lowercase()} files found")
                        } else {
                            BrowseUiState.Success(files)
                        }
                    },
                    onFailure = { e ->
                        _uiState.value = BrowseUiState.Error(e.message ?: "Unknown error")
                    }
                )
            }
    }
    
    private suspend fun loadNetworkFiles(sourceId: String) {
        withContext(Dispatchers.IO) {
            try {
                // First, get the saved NetworkSource by ID
                val source = networkSourceRepository.getSourceById(sourceId)
                if (source == null) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = BrowseUiState.Error("Network source not found")
                    }
                    return@withContext
                }
                
                Timber.d("Connecting to SMB source: ${source.server}")
                
                // Connect to SMB with saved credentials
                val connectResult = smbDataSource.connect(source)
                if (connectResult.isFailure) {
                    val error = connectResult.exceptionOrNull()?.message ?: "Connection failed"
                    Timber.e("Failed to connect to SMB: $error")
                    withContext(Dispatchers.Main) {
                        _uiState.value = BrowseUiState.Error("Connection failed: $error")
                    }
                    return@withContext
                }
                
                Timber.d("Connected to SMB, listing files...")
                
                // Current directory path relative to share root
                val currentPath = "/"
                
                val result = smbDataSource.listFiles(currentPath)
                result.fold(
                    onSuccess = { fileNames ->
                        // Convert file names to WearMediaFile objects and filter by media type
                        // Build full path by combining currentPath with fileName
                        val mediaFiles = fileNames
                            .mapIndexed { index, fileName -> 
                                // Build full path relative to share root
                                val fullPath = if (currentPath == "/" || currentPath.isEmpty()) {
                                    fileName
                                } else {
                                    "${currentPath.trimEnd('/')}/$fileName"
                                }
                                WearMediaFile(
                                    id = index.toLong(),
                                    name = fileName,
                                    uri = android.net.Uri.parse(fullPath),
                                    mimeType = getMimeTypeFromFileName(fileName),
                                    size = 0,
                                    dateModified = 0,
                                    duration = 0
                                ) 
                            }
                            .filter { file -> isSupportedMediaFile(file.mimeType) }
                        
                        Timber.d("Loaded ${mediaFiles.size} media files from SMB")
                        
                        withContext(Dispatchers.Main) {
                            _uiState.value = if (mediaFiles.isEmpty()) {
                                BrowseUiState.Empty("No media files found")
                            } else {
                                BrowseUiState.Success(mediaFiles)
                            }
                        }
                    },
                    onFailure = { e ->
                        Timber.e(e, "Error loading network media files")
                        withContext(Dispatchers.Main) {
                            _uiState.value = BrowseUiState.Error(e.message ?: "Failed to load network files")
                        }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Exception loading network files")
                withContext(Dispatchers.Main) {
                    _uiState.value = BrowseUiState.Error(e.message ?: "Network error")
                }
            }
        }
    }
    
    fun selectFile(file: WearMediaFile) {
        Timber.d("File selected: ${file.name}")
        _selectedFile.value = file
        
        // Save to SelectedMediaManager for player access
        // For SMB files, the uri field contains the file path relative to share
        selectedMediaManager.selectFile(
            file = file,
            isNetworkSource = isNetworkSource,
            streamUri = file.uri.toString()
        )
    }
    
    fun clearSelection() {
        _selectedFile.value = null
    }
    
    fun getScreenTitle(): String {
        return if (isNetworkSource) {
            sourceName ?: "Network Storage"
        } else {
            when (mediaType) {
                MediaType.MUSIC -> "Music"
                MediaType.VIDEO -> "Videos"
                MediaType.PHOTO -> "Photos"
            }
        }
    }
    
    /**
     * Get MIME type from filename using file extension.
     * Uses Android's MimeTypeMap with fallback for common media types.
     */
    private fun getMimeTypeFromFileName(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension.isEmpty()) return null
        
        // Use system MimeTypeMap first
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        if (mimeType != null) return mimeType
        
        // Fallback for common media types not in MimeTypeMap
        return when (extension) {
            // Images
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            "heic", "heif" -> "image/heic"
            // Videos
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/avi"
            "mov" -> "video/quicktime"
            "wmv" -> "video/x-ms-wmv"
            "flv" -> "video/x-flv"
            "3gp" -> "video/3gpp"
            // Audio
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "ogg", "oga" -> "audio/ogg"
            "flac" -> "audio/flac"
            "wma" -> "audio/x-ms-wma"
            // Documents (for filtering)
            "pdf" -> "application/pdf"
            "epub" -> "application/epub+zip"
            else -> null
        }
    }
    
    /**
     * Check if a MIME type matches the expected media type category.
     */
    private fun matchesMediaType(mimeType: String?, mediaType: MediaType): Boolean {
        if (mimeType == null) return false
        
        return when (mediaType) {
            MediaType.PHOTO -> mimeType.startsWith("image/")
            MediaType.VIDEO -> mimeType.startsWith("video/")
            MediaType.MUSIC -> mimeType.startsWith("audio/")
        }
    }
    
    /**
     * Check if this is a supported media file (image, video, or audio).
     */
    private fun isSupportedMediaFile(mimeType: String?): Boolean {
        if (mimeType == null) return false
        return mimeType.startsWith("image/") ||
               mimeType.startsWith("video/") ||
               mimeType.startsWith("audio/")
    }
}
