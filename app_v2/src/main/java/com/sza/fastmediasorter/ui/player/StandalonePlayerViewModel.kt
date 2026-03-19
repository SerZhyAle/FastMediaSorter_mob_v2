package com.sza.fastmediasorter.ui.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseViewModel
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class StandalonePlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context
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
    }
}
