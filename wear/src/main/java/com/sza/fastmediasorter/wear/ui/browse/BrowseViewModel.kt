package com.sza.fastmediasorter.wear.ui.browse

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.data.network.ftp.FtpDataSource
import com.sza.fastmediasorter.wear.data.network.sftp.SftpDataSource
import com.sza.fastmediasorter.wear.data.network.smb.SmbDataSource
import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.model.NetworkBasePath
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearMediaRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.util.MediaMimeTypes
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
 * A screen title the view model can name without holding a Context: either text that came from the
 * user's own data, or a resource the screen resolves.
 */
sealed interface ScreenTitle {
    data class Text(val value: String) : ScreenTitle
    data class Resource(@param:StringRes val id: Int) : ScreenTitle
}

/**
 * ViewModel for the browse screen.
 * Handles loading media files from both local MediaStore and SMB network sources.
 */
@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val mediaRepository: WearMediaRepository,
    private val preferencesRepository: WearPreferencesRepository,
    private val smbDataSource: SmbDataSource,
    private val ftpDataSource: FtpDataSource,
    private val sftpDataSource: SftpDataSource,
    private val networkSourceRepository: NetworkSourceRepository,
    private val selectedMediaManager: SelectedMediaManager,
    private val playbackSetManager: PlaybackSetManager
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
                
                // S1556: the source's own path, not the server root. The watch's SFTP connection
                // test already asserts this path is reachable, so listing the root instead showed
                // the user something other than what the same screen had just verified.
                // Normalised again here, idempotently: a source stored before S1556's import fix
                // still holds the phone's URL form, and no phone has to be nearby to correct it.
                val currentPath = NetworkBasePath
                    .normalize(source.basePath, source.type, source.shareName)
                    .ifBlank { "/" }
                Timber.d("Connecting to ${source.type} source: ${source.server}")

                val mediaFiles: List<WearMediaFile> = when (source.type) {
                    NetworkSourceType.SMB -> {
                        val connectResult = smbDataSource.connect(source)
                        if (connectResult.isFailure) {
                            val error = connectResult.exceptionOrNull()?.message ?: "Connection failed"
                            Timber.e("Failed to connect to SMB: $error")
                            withContext(Dispatchers.Main) {
                                _uiState.value = BrowseUiState.Error("Connection failed: $error")
                            }
                            return@withContext
                        }
                        val result = smbDataSource.listFiles(currentPath)
                        if (result.isFailure) {
                            error(result.exceptionOrNull()?.message ?: "SMB list failed")
                        }
                        result.getOrDefault(emptyList()).mapIndexed { index, fileName ->
                            val fullPath = if (currentPath == "/" || currentPath.isEmpty()) {
                                fileName
                            } else {
                                "${currentPath.trimEnd('/')}/$fileName"
                            }
                            WearMediaFile(
                                id = index.toLong(),
                                name = fileName,
                                uri = android.net.Uri.parse(fullPath),
                                mimeType = MediaMimeTypes.fromFileName(fileName),
                                size = 0,
                                dateModified = 0,
                                duration = 0
                            )
                        }
                    }
                    NetworkSourceType.FTP -> ftpDataSource.listDirectory(source, currentPath)
                    NetworkSourceType.SFTP -> sftpDataSource.listDirectory(source, currentPath)
                    NetworkSourceType.GOOGLE_DRIVE -> error("Google Drive not supported on Wear")
                }.filter { matchesMediaType(it.mimeType, mediaType) }
                Timber.d("S1690: network kept ${mediaFiles.size} $mediaType file(s)")
                Timber.d("S1556: listed $currentPath on ${source.type}")

                Timber.d("Loaded ${mediaFiles.size} media files from ${source.type}")
                withContext(Dispatchers.Main) {
                    _uiState.value = if (mediaFiles.isEmpty()) {
                        BrowseUiState.Empty("No media files found")
                    } else {
                        BrowseUiState.Success(mediaFiles)
                    }
                }
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

        // The set the player will page through is fixed here, from the list on screen, because a
        // later re-query would answer in a different order than the user saw (S1683 ADR-2).
        val displayed = (_uiState.value as? BrowseUiState.Success)?.files
        if (displayed != null) {
            playbackSetManager.publish(displayed, displayed.indexOfFirst { it.id == file.id })
        }

        // Save to SelectedMediaManager for player access.
        // The uri shape differs per protocol and the player has to know which it got: SMB files
        // carry a path relative to the share, FTP and SFTP files carry a full ftp:// / sftp:// URI.
        // S1687: the source id travels with them, because it is the only way the player can reach
        // the protocol and the credentials this file actually needs.
        Timber.d("S1687: selection handover sourceId=$_sourceId network=$isNetworkSource")
        selectedMediaManager.selectFile(
            file = file,
            isNetworkSource = isNetworkSource,
            streamUri = file.uri.toString(),
            sourceId = _sourceId
        )
    }
    
    fun clearSelection() {
        _selectedFile.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // The manager is a singleton and would otherwise outlive this screen, leaving a player
        // opened by any later route paging through a list the user has already left.
        playbackSetManager.clear()
    }
    
    /**
     * S1683: the title is either a source's own name, which no dictionary can translate, or one of
     * four resources. It used to be four English literals, so the browse list stayed English under a
     * Russian interface - the defect that put the localization constraint in the spec at all.
     */
    fun getScreenTitle(): ScreenTitle {
        if (isNetworkSource) {
            val name = sourceName
            return if (name.isNullOrBlank()) {
                ScreenTitle.Resource(R.string.network_storage)
            } else {
                ScreenTitle.Text(name)
            }
        }
        return ScreenTitle.Resource(
            when (mediaType) {
                MediaType.MUSIC -> R.string.music
                MediaType.VIDEO -> R.string.videos
                MediaType.PHOTO -> R.string.photos
            }
        )
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
}
