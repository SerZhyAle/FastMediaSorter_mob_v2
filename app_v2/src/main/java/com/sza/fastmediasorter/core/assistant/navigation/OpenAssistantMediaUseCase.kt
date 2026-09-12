package com.sza.fastmediasorter.core.assistant.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.core.assistant.model.OpenMediaParams
import com.sza.fastmediasorter.core.assistant.model.OpenMediaResult
import com.sza.fastmediasorter.ui.player.PlayerActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Assistant navigation launcher to open a media item in the player (S2920).
 */
class OpenAssistantMediaUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    operator fun invoke(params: OpenMediaParams): OpenMediaResult {
        return try {
            val intent = PlayerActivity.createIntent(
                context = context,
                resourceId = params.resourceId,
                initialFilePath = params.filePath
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            OpenMediaResult(success = true, message = "Player launched")
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "Activity not found for assistant player launch")
            OpenMediaResult(success = false, message = e.message)
        } catch (e: SecurityException) {
            Timber.w(e, "Security error launching player for assistant action")
            OpenMediaResult(success = false, message = e.message)
        } catch (e: IllegalStateException) {
            Timber.w(e, "State error launching player for assistant action")
            OpenMediaResult(success = false, message = e.message)
        }
    }
}
