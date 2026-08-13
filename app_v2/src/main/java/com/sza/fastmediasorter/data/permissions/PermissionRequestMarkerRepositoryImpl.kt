package com.sza.fastmediasorter.data.permissions

import android.content.Context
import android.content.SharedPreferences
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import com.sza.fastmediasorter.domain.repository.PermissionRequestMarkerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedPreferences-backed request marker. Reads stay synchronous because the status use case runs
 * on the main thread while building permission rows and cannot suspend.
 */
@Singleton
class PermissionRequestMarkerRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context
) : PermissionRequestMarkerRepository {

    private val prefs: SharedPreferences =
        StrictModeHelper.allowDiskReads {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

    override fun wasRequested(permissionId: String): Boolean =
        StrictModeHelper.allowDiskReads { prefs.getBoolean(permissionId, false) }

    override fun markRequested(permissionId: String) {
        StrictModeHelper.allowDiskWrites { prefs.edit().putBoolean(permissionId, true).apply() }
    }

    private companion object {
        const val PREFS_NAME = "perm_request_marker_prefs"
    }
}
