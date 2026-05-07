package com.sza.fastmediasorter.domain.usecase

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.domain.model.PermissionEntry
import com.sza.fastmediasorter.domain.model.PermissionStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckPermissionStatusUseCase @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    operator fun invoke(context: Context, entry: PermissionEntry): PermissionStatus {
        if (entry.minSdk > Build.VERSION.SDK_INT || entry.maxSdk < Build.VERSION.SDK_INT) {
            return PermissionStatus.NOT_APPLICABLE
        }
        return when (entry.manifestName) {
            Manifest.permission.MANAGE_EXTERNAL_STORAGE ->
                if (PermissionHelper.hasAllFilesAccessPermission(appContext)) PermissionStatus.GRANTED
                else PermissionStatus.DENIED
            Manifest.permission.MANAGE_MEDIA ->
                if (PermissionHelper.hasManageMediaPermission(appContext)) PermissionStatus.GRANTED
                else PermissionStatus.DENIED
            Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -> {
                val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (pm.isIgnoringBatteryOptimizations(appContext.packageName)) PermissionStatus.GRANTED
                else PermissionStatus.DENIED
            }
            else -> {
                if (ContextCompat.checkSelfPermission(appContext, entry.manifestName) == PackageManager.PERMISSION_GRANTED) {
                    PermissionStatus.GRANTED
                } else {
                    val activity = context as? Activity
                    // PERMANENTLY_DENIED is indistinguishable from never-requested without caller tracking
                    if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, entry.manifestName)) {
                        PermissionStatus.PERMANENTLY_DENIED
                    } else {
                        PermissionStatus.DENIED
                    }
                }
            }
        }
    }
}
