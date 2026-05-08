package com.sza.fastmediasorter.wear.ui.player.image

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.data.network.smb.SmbDataSource
import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearMediaRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.SendFavoritesDeltaUseCase
import com.sza.fastmediasorter.wear.ui.slideshow.ImageSlideshowController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * ViewModel for the image viewer screen.
 * Manages image loading, navigation, and slideshow functionality.
 */
@HiltViewModel
class ImageViewerViewModel @Inject constructor(
    private val mediaRepository: WearMediaRepository,
    private val preferencesRepository: WearPreferencesRepository,
    private val selectedMediaManager: SelectedMediaManager,
    private val smbDataSource: SmbDataSource,
    private val favoritesRepository: WearFavoritesRepository,
    private val sendFavoritesDeltaUseCase: SendFavoritesDeltaUseCase,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ImageViewerUiState())
    val uiState: StateFlow<ImageViewerUiState> = _uiState.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()
    
    private val fileId: Long = savedStateHandle.get<Long>("fileId") ?: -1L
    private var allImages: List<WearMediaFile> = emptyList()
    
    private var slideshowController: ImageSlideshowController? = null
    
    // Track if this is an SMB source
    private var isNetworkSource: Boolean = false
    
    init {
        Timber.d("ImageViewerViewModel initialized with fileId: $fileId")
        
        // Auto-load if fileId is valid (from SavedStateHandle)
        if (fileId != -1L) {
            loadImageFile()
        }
    }
    
    private fun loadImageFile() {
        Timber.d("Loading image file with fileId: $fileId")
        
        // First, check if we have a selected file from SelectedMediaManager (SMB source)
        val selectedMedia = selectedMediaManager.getSelectedFileById(fileId)
        
        if (selectedMedia != null && selectedMedia.isNetworkSource) {
            // SMB file - load it
            Timber.d("Loading SMB image: ${selectedMedia.file.name}")
            isNetworkSource = true
            loadSmbImage(selectedMedia.streamUri, selectedMedia.file)
        } else {
            // Local file - use MediaStore
            loadImages()
        }
    }
    
    /**
     * Load an image from SMB by downloading it to cache.
     */
    private fun loadSmbImage(filePath: String, file: WearMediaFile) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            withContext(Dispatchers.IO) {
                try {
                    Timber.d("Downloading SMB image: $filePath")
                    
                    // Get file stream from SMB
                    val streamResult = smbDataSource.getFileStream(filePath)
                    
                    streamResult.fold(
                        onSuccess = { inputStream ->
                            // Create temp file in cache directory
                            val cacheDir = File(context.cacheDir, "smb_images")
                            cacheDir.mkdirs()
                            
                            // Use hash of file path for unique filename
                            val tempFileName = "${filePath.hashCode()}_${file.name}"
                            val tempFile = File(cacheDir, tempFileName)
                            
                            Timber.d("Saving to temp file: ${tempFile.absolutePath}")
                            
                            // Copy stream to file
                            inputStream.use { input ->
                                FileOutputStream(tempFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            
                            Timber.d("SMB image downloaded, size: ${tempFile.length()} bytes")
                            
                            // Create a WearMediaFile with the local temp file URI
                            val localFile = file.copy(uri = Uri.fromFile(tempFile))
                            
                            withContext(Dispatchers.Main) {
                                allImages = listOf(localFile) // For SMB, we only show one image
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        mediaFile = localFile,
                                        currentIndex = 0,
                                        totalCount = 1
                                    )
                                }
                                checkFavoriteState(sourceId = "network", filePath = filePath)
                            }
                        },
                        onFailure = { e ->
                            Timber.e(e, "Failed to download SMB image")
                            withContext(Dispatchers.Main) {
                                _uiState.update { 
                                    it.copy(isLoading = false, error = "Failed to load: ${e.message}") 
                                }
                            }
                        }
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Exception loading SMB image")
                    withContext(Dispatchers.Main) {
                        _uiState.update { 
                            it.copy(isLoading = false, error = "Error: ${e.message}") 
                        }
                    }
                }
            }
        }
    }
    
    private fun loadImages() {
        viewModelScope.launch {
            try {
                // Load all images to enable navigation
                val result = mediaRepository.getMediaFiles(MediaType.PHOTO).first()
                result.fold(
                    onSuccess = { files ->
                        allImages = files
                        val currentIndex = files.indexOfFirst { it.id == fileId }
                        if (currentIndex >= 0) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    mediaFile = files[currentIndex],
                                    currentIndex = currentIndex,
                                    totalCount = files.size
                                )
                            }
                            checkFavoriteState(sourceId = "local", filePath = files[currentIndex].uri.toString())

                            // Initialize slideshow controller
                            initializeSlideshowController(currentIndex)
                        } else {
                            _uiState.update { 
                                it.copy(isLoading = false, error = "Image not found") 
                            }
                        }
                    },
                    onFailure = { e ->
                        _uiState.update { 
                            it.copy(isLoading = false, error = e.message ?: "Unknown error") 
                        }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load images")
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
    
    private fun initializeSlideshowController(startIndex: Int) {
        viewModelScope.launch {
            val intervalSeconds = preferencesRepository.slideshowIntervalSeconds.first()
            
            slideshowController = ImageSlideshowController(
                scope = viewModelScope,
                intervalSeconds = intervalSeconds,
                totalItems = allImages.size,
                onIndexChanged = { newIndex ->
                    navigateToIndex(newIndex)
                }
            )
            
            // Auto-start if slideshow is enabled in settings
            val isSlideshowEnabled = preferencesRepository.isSlideshowEnabled.first()
            if (isSlideshowEnabled) {
                startSlideshow()
            }
        }
    }
    
    fun startSlideshow() {
        Timber.d("Starting slideshow")
        slideshowController?.start()
        _uiState.update { it.copy(isSlideshowActive = true) }
    }
    
    fun stopSlideshow() {
        Timber.d("Stopping slideshow")
        slideshowController?.stop()
        _uiState.update { it.copy(isSlideshowActive = false) }
    }
    
    fun toggleSlideshow() {
        if (_uiState.value.isSlideshowActive) {
            stopSlideshow()
        } else {
            startSlideshow()
        }
    }
    
    fun navigateToNext() {
        if (allImages.isEmpty()) return
        
        val nextIndex = (_uiState.value.currentIndex + 1) % allImages.size
        navigateToIndex(nextIndex)
        
        // Notify slideshow controller about manual navigation
        slideshowController?.next()
    }
    
    fun navigateToPrevious() {
        if (allImages.isEmpty()) return
        
        val prevIndex = if (_uiState.value.currentIndex > 0) {
            _uiState.value.currentIndex - 1
        } else {
            allImages.size - 1
        }
        navigateToIndex(prevIndex)
        
        // Notify slideshow controller about manual navigation
        slideshowController?.previous()
    }
    
    private fun navigateToIndex(index: Int) {
        if (index in allImages.indices) {
            _uiState.update { 
                it.copy(
                    mediaFile = allImages[index],
                    currentIndex = index
                ) 
            }
        }
    }
    
    fun toggleFavorite() {
        val selected = selectedMediaManager.getSelectedFileById(fileId)
        val sourceId = if (selected?.isNetworkSource == true) "network" else "local"
        val filePath = if (selected?.isNetworkSource == true) {
            selected.streamUri
        } else {
            _uiState.value.mediaFile?.uri?.toString() ?: return
        }
        viewModelScope.launch {
            if (_isFavorite.value) {
                favoritesRepository.removeFavorite(sourceId, filePath)
            } else {
                favoritesRepository.addFavorite(sourceId, filePath)
            }
            _isFavorite.value = !_isFavorite.value
            sendFavoritesDeltaUseCase()
        }
    }

    private fun checkFavoriteState(sourceId: String, filePath: String) {
        viewModelScope.launch {
            _isFavorite.value = favoritesRepository.isFavorite(sourceId, filePath)
        }
    }

    override fun onCleared() {
        super.onCleared()
        slideshowController?.stop()
    }
}
