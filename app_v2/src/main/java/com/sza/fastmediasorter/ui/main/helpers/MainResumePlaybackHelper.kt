package com.sza.fastmediasorter.ui.main.helpers

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.data.repository.ResumeStateRepositoryImpl
import com.sza.fastmediasorter.databinding.ActivityMainBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.ResumeStateRepository
import com.sza.fastmediasorter.domain.usecase.ClearResumeStateUseCase
import com.sza.fastmediasorter.domain.usecase.GetResumeStateUseCase
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.player.AudioPlaybackService
import com.sza.fastmediasorter.ui.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * Handles "Resume on next launch" flow: validates the persisted resume state, checks resource
 * availability with a hard timeout, and routes the user back to the player or browser.
 *
 * Extracted from MainActivity to keep the activity below the 1000-line cap.
 */
class MainResumePlaybackHelper(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: ResourceRepository,
    private val getResumeStateUseCase: GetResumeStateUseCase,
    private val clearResumeStateUseCase: ClearResumeStateUseCase
) {

    fun shouldAttemptResume(intent: Intent?): Boolean {
        // Only for standard launcher icon start (ACTION_MAIN)
        if (intent?.action != Intent.ACTION_MAIN) return false
        // Skip DB-based resume if AudioPlaybackService is still running (process wasn't killed).
        // The redirect to PlayerActivity is handled in MainActivity.onCreate above this call.
        if (AudioPlaybackService.isRunning) {
            Timber.d("MainResumePlaybackHelper: Skipping DB resume - AudioPlaybackService is running")
            return false
        }
        // Skip if storage permissions are missing
        if (!PermissionHelper.hasStoragePermission(activity)) {
            Timber.d("MainResumePlaybackHelper: Skipping resume - no storage permissions")
            return false
        }
        return true
    }

    fun attemptResumePlayback() {
        activity.lifecycleScope.launch {
            try {
                // Gate on user preference before any UI is shown
                val resumeEnabled = settingsRepository.getSettings().first().resumeOnNextLaunch
                if (!resumeEnabled) {
                    Timber.d("MainResumePlaybackHelper: Resume on next launch disabled by user setting")
                    return@launch
                }

                // Show loading overlay only when we will actually attempt resume
                binding.navigationProgressLayout.isVisible = true
                binding.tvNavigationMessage.text = activity.getString(R.string.resume_checking)

                val state = getResumeStateUseCase(ResumeStateRepository.WINDOW_ID_MAIN)
                if (state == null) {
                    Timber.d("MainResumePlaybackHelper: No resume state found")
                    dismissResumeLoading()
                    return@launch
                }

                // TTL check
                val elapsed = System.currentTimeMillis() - state.savedAt
                if (elapsed > ResumeStateRepositoryImpl.RESUME_TTL_MS) {
                    Timber.d("MainResumePlaybackHelper: Resume state expired (${elapsed}ms > TTL)")
                    clearResumeStateUseCase(ResumeStateRepository.WINDOW_ID_MAIN)
                    dismissResumeLoading()
                    Toast.makeText(activity, R.string.resume_unavailable, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Load resource to determine type
                val resource = resourceRepository.getResourceById(state.resourceId)
                if (resource == null) {
                    Timber.w("MainResumePlaybackHelper: Resume resource not found (id=${state.resourceId})")
                    clearResumeStateUseCase(ResumeStateRepository.WINDOW_ID_MAIN)
                    dismissResumeLoading()
                    return@launch
                }

                // Availability check timeout: TCP pre-check (3 s) + SMBJ negotiate/auth/tree-connect (~5 s)
                // = 8 s worst case. 12 s gives a safe margin without blocking the user too long.
                val isAvailable = try {
                    withTimeout(12_000L) {
                        checkResourceAvailability(resource, state.filePath)
                    }
                } catch (e: TimeoutCancellationException) {
                    Timber.w("MainResumePlaybackHelper: Resume availability check timed out")
                    false
                }

                if (!isAvailable) {
                    Timber.d("MainResumePlaybackHelper: Resume target unavailable")
                    clearResumeStateUseCase(ResumeStateRepository.WINDOW_ID_MAIN)
                    dismissResumeLoading()
                    Toast.makeText(activity, R.string.resume_unavailable, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Force isPlaying=false for video over network/cloud - autoplay is unreliable
                // and the user typically wants to confirm before streaming starts.
                val effectiveIsPlaying = when {
                    resource.type in setOf(ResourceType.SMB, ResourceType.FTP, ResourceType.SFTP)
                            && state.mediaType == MediaType.VIDEO -> false
                    resource.type == ResourceType.CLOUD
                            && state.mediaType == MediaType.VIDEO -> false
                    else -> state.isPlaying
                }

                dismissResumeLoading()

                when (state.screenType) {
                    com.sza.fastmediasorter.domain.model.ScreenType.PLAYER -> {
                        val playerIntent = PlayerActivity.createIntent(
                            context = activity,
                            resourceId = state.resourceId,
                            skipAvailabilityCheck = true,
                            initialFilePath = state.filePath,
                            isPlaying = effectiveIsPlaying,
                            isSlideshowEnabled = state.isSlideshowEnabled
                        )
                        activity.startActivity(playerIntent)
                        @Suppress("DEPRECATION")
                        activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    }
                    com.sza.fastmediasorter.domain.model.ScreenType.BROWSER -> {
                        val browseIntent = BrowseActivity.createIntent(
                            context = activity,
                            resourceId = state.resourceId,
                            skipAvailabilityCheck = true,
                            initialFolderPath = state.currentFolderPath,
                            initialFilePath = state.filePath,
                            isPlaying = effectiveIsPlaying
                        )
                        activity.startActivity(browseIntent)
                        @Suppress("DEPRECATION")
                        activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    }
                }
                Timber.d("MainResumePlaybackHelper: Resumed playback → ${state.screenType} for ${state.filePath}")
            } catch (e: Exception) {
                Timber.e(e, "MainResumePlaybackHelper: Resume playback failed")
                try { clearResumeStateUseCase(ResumeStateRepository.WINDOW_ID_MAIN) } catch (_: Exception) {}
                dismissResumeLoading()
            }
        }
    }

    private suspend fun checkResourceAvailability(
        resource: MediaResource,
        filePath: String
    ): Boolean = withContext(Dispatchers.IO) {
        when (resource.type) {
            ResourceType.LOCAL -> java.io.File(filePath).exists()
            ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP, ResourceType.CLOUD -> {
                resourceRepository.testConnection(resource).isSuccess
            }
            ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM -> true
        }
    }

    fun dismissResumeLoading() {
        binding.navigationProgressLayout.isVisible = false
    }
}
