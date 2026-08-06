package com.sza.fastmediasorter.utils

import android.content.Context
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.core.util.StoragePermissionRule

object PermissionChecker {

    fun getRequiredMediaPermissions(): Array<String> = StoragePermissionRule.requiredPermissions()

    fun hasMediaPermissions(context: Context): Boolean {
        val permissions = getRequiredMediaPermissions()
        if (permissions.isEmpty()) {
            return true
        }

        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
}
