package com.sza.fastmediasorter.core.assistant.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.core.assistant.model.OpenFolderParams
import com.sza.fastmediasorter.core.assistant.model.OpenFolderResult
import com.sza.fastmediasorter.ui.main.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Assistant navigation launcher to open a source/folder in the browser (S2920).
 */
class OpenAssistantFolderUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    operator fun invoke(params: OpenFolderParams): OpenFolderResult {
        return try {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_BROWSE_RESOURCE
                putExtra(MainActivity.EXTRA_SHORTCUT_RESOURCE_ID, params.resourceId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            OpenFolderResult(success = true, message = "Browser opened for resource")
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "Activity not found for assistant browse action")
            OpenFolderResult(success = false, message = e.message)
        } catch (e: SecurityException) {
            Timber.w(e, "Security error launching browser for assistant action")
            OpenFolderResult(success = false, message = e.message)
        } catch (e: IllegalStateException) {
            Timber.w(e, "State error launching browser for assistant action")
            OpenFolderResult(success = false, message = e.message)
        }
    }
}
