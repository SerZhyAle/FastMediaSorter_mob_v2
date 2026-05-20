package com.sza.fastmediasorter.ui.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseViewModel
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.data.local.db.StereoFormatOverrideDao
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.FavoritesUseCase
import com.sza.fastmediasorter.ui.player.helpers.PlayerStereoModeCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class StandalonePlayerViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val favoritesUseCase: FavoritesUseCase,
    private val resourceRepository: ResourceRepository,
    stereoFormatOverrideDao: StereoFormatOverrideDao
) :
    BaseViewModel<StandalonePlayerViewModel.StandalonePlayerState, StandalonePlayerViewModel.StandalonePlayerEvent>() {

    data class StandalonePlayerState(
        val mediaFile: MediaFile? = null,
        val mediaType: MediaType? = null,
        val isLoading: Boolean = false,
        val errorMessage: String? = null
    )

    sealed class StandalonePlayerEvent {
        data class ShowError(val message: String) : StandalonePlayerEvent()
        object FinishActivity : StandalonePlayerEvent()
    }

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _messageFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messageFlow: SharedFlow<String> = _messageFlow.asSharedFlow()

    private val stereoCoordinator = PlayerStereoModeCoordinator(
        stereoFormatOverrideDao = stereoFormatOverrideDao,
        scope = viewModelScope,
        getCurrentFilePath = { state.value.mediaFile?.path }
    )

    val stereoMode: StateFlow<StereoMode> = stereoCoordinator.stereoMode
    val detectedStereoMode: StateFlow<StereoMode> = stereoCoordinator.detectedStereoMode

    fun setStereoMode(mode: StereoMode) = stereoCoordinator.setStereoMode(mode)

    fun rememberStereoModeForCurrentFile(mode: StereoMode) =
        stereoCoordinator.rememberStereoModeForCurrentFile(mode)

    fun setAutoDetectedStereoMode(mode: StereoMode) =
        stereoCoordinator.setAutoDetectedStereoMode(mode)

    fun resetStereoModeForNewFile() =
        stereoCoordinator.resetStereoModeForNewFile(state.value.mediaFile?.path)

    fun showMessage(message: String) {
        _messageFlow.tryEmit(message)
    }

    override fun getInitialState(): StandalonePlayerState = StandalonePlayerState()

    fun loadFromUri(uri: Uri, mimeType: String?, displayName: String?) {
        Timber.d("StandalonePlayer: loadFromUri uri=$uri mime=$mimeType name=$displayName")
        updateState { it.copy(isLoading = true, errorMessage = null) }

        val resolvedName = displayName ?: uri.lastPathSegment ?: "unknown"
        val detectedType = MediaTypeUtils.getMediaTypeFromMimeOrExtension(mimeType, resolvedName)

        if (detectedType == null) {
            Timber.w("StandalonePlayer: unsupported file type for $resolvedName (mime=$mimeType)")
            updateState {
                it.copy(
                    isLoading = false,
                    errorMessage = context.getString(R.string.error_opening_file, resolvedName)
                )
            }
            sendEvent(StandalonePlayerEvent.FinishActivity)
            return
        }

        val mediaFile = MediaFile(
            name = resolvedName,
            path = uri.toString(),
            type = detectedType,
            size = 0L,
            createdDate = System.currentTimeMillis(),
            contentUri = uri.toString()
        )

        Timber.d("StandalonePlayer: resolved type=$detectedType for $resolvedName")
        updateState {
            it.copy(
                mediaFile = mediaFile,
                mediaType = detectedType,
                isLoading = false,
                errorMessage = null
            )
        }

        // Trigger DB lookup for remembered stereo mode so it's applied before the dialog opens.
        stereoCoordinator.resetStereoModeForNewFile(uri.toString())

        checkFavoriteStatus(uri.toString())
    }

    fun checkFavoriteStatus(uri: String) {
        viewModelScope.launch {
            _isFavorite.value = favoritesUseCase.isFavoriteSync(uri)
        }
    }

    fun toggleFavorite() {
        val file = state.value.mediaFile ?: return
        viewModelScope.launch {
            favoritesUseCase.toggleFavorite(file, resourceId = 0L)
            _isFavorite.value = !_isFavorite.value
        }
    }

    fun onRenameComplete(newUri: Uri, newName: String) {
        updateState { state ->
            state.copy(mediaFile = state.mediaFile?.copy(
                name = newName,
                path = newUri.toString(),
                contentUri = newUri.toString()
            ))
        }
    }

    suspend fun findResourceForPath(folderPath: String?): Long? {
        if (folderPath == null) return null
        val resources = resourceRepository.getAllResourcesSync()
        return resources.firstOrNull { resource ->
            resource.type == ResourceType.LOCAL && folderPath.startsWith(resource.path)
        }?.id
    }
}
