package com.sza.fastmediasorter.worker

import android.Manifest
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import com.sza.fastmediasorter.util.getServiceInfoCompat
import timber.log.Timber

/**
 * Decides whether a foreground start may carry [ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC].
 *
 * The answer comes from the merged manifest at run time, not from the flavor: the store release overlay
 * (`src/storeReleaseNoDataSync`) strips the dataSync type and its permission while every caller kept
 * requesting it, and `startForeground` with a type outside the manifest attribute throws inside the
 * service's `onStartCommand`, where no worker-side catch can reach it.
 */
object DataSyncForeground {

    // Addressed by name: the WorkManager service class is a restricted API.
    private const val WORK_FOREGROUND_SERVICE = "androidx.work.impl.foreground.SystemForegroundService"

    /** The type to pass, or null when the start must not carry dataSync. */
    fun resolveType(sdkInt: Int, manifestTypes: Int, permissionGranted: Boolean): Int? {
        val typed = sdkInt >= Build.VERSION_CODES.Q
        val declared = (manifestTypes and ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) != 0
        val permitted = sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || permissionGranted
        return if (typed && declared && permitted) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else null
    }

    fun isAvailable(context: Context, component: ComponentName): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val manifestTypes = try {
            context.packageManager.getServiceInfoCompat(component).foregroundServiceType
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.w(e, "DataSyncForeground: service %s not in manifest", component.className)
            0
        }
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC) ==
            PackageManager.PERMISSION_GRANTED
        Timber.d("S4537: dataSync check %s types=%d granted=%b", component.className, manifestTypes, granted)
        return resolveType(Build.VERSION.SDK_INT, manifestTypes, granted) != null
    }

    fun isAvailableForWorkers(context: Context): Boolean =
        isAvailable(context, ComponentName(context, WORK_FOREGROUND_SERVICE))

    /**
     * Below API 34 an untyped start is a subset of any manifest attribute, so it is the safe fallback.
     * From API 34 an untyped start is not accepted, so a worker without dataSync stays in the background.
     */
    fun canPromoteWorker(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || isAvailableForWorkers(context)

    fun workerForegroundInfo(context: Context, notificationId: Int, notification: Notification): ForegroundInfo =
        if (isAvailableForWorkers(context)) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
}

suspend fun CoroutineWorker.setDataSyncForegroundIfAllowed(info: ForegroundInfo) {
    if (DataSyncForeground.canPromoteWorker(applicationContext)) {
        setForeground(info)
    } else {
        Timber.i("%s: no dataSync foreground type in this build, running in background", javaClass.simpleName)
    }
}
