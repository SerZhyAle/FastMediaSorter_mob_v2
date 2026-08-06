package com.sza.fastmediasorter.domain.usecase

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.domain.model.PermissionEntry
import com.sza.fastmediasorter.domain.model.PermissionGrantKind
import com.sza.fastmediasorter.domain.model.PermissionStatus
import com.sza.fastmediasorter.domain.repository.PermissionRequestMarkerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckPermissionStatusUseCase @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val requestMarker: PermissionRequestMarkerRepository,
) {
    operator fun invoke(context: Context, entry: PermissionEntry): PermissionStatus = when {
        // S1436: a per-use consent has no stored grant to read, so it is answered before the SDK
        // window and before any ContextCompat check - both would report a state that does not exist.
        entry.grantKind == PermissionGrantKind.PER_USE_CONSENT -> PermissionStatus.ASKED_EACH_TIME
        entry.minSdk > Build.VERSION.SDK_INT || entry.maxSdk < Build.VERSION.SDK_INT ->
            PermissionStatus.NOT_APPLICABLE
        else -> resolveGrantedState(context, entry)
    }

    private fun resolveGrantedState(context: Context, entry: PermissionEntry): PermissionStatus =
        when (entry.manifestName) {
            Manifest.permission.MANAGE_EXTERNAL_STORAGE ->
                if (PermissionHelper.hasAllFilesAccessPermission(appContext)) PermissionStatus.GRANTED
                else PermissionStatus.DENIED
            Manifest.permission.MANAGE_MEDIA ->
                if (PermissionHelper.hasManageMediaPermission(appContext)) PermissionStatus.GRANTED
                else PermissionStatus.DENIED
            // S0429: a signature permission the app never holds, so checkSelfPermission below would
            // report DENIED to a user who had granted the access. The enabled-listener list is the only
            // thing that knows. Inlined rather than delegated: the launcher-side helper that answers the
            // same question lives in src/launcherEnabled, which src/main cannot see, and this is one call.
            Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE ->
                if (appContext.packageName in NotificationManagerCompat.getEnabledListenerPackages(appContext)) {
                    Timber.d("S0429: permission registry reports notification access GRANTED")
                    PermissionStatus.GRANTED
                } else {
                    Timber.d("S0429: permission registry reports notification access DENIED")
                    PermissionStatus.DENIED
                }
            // S1436: like the overlay below, an appop the generic arm cannot read. The SDK guard is for
            // lint only - the entry's minSdk already keeps the row off older levels.
            Manifest.permission.REQUEST_INSTALL_PACKAGES ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    appContext.packageManager.canRequestPackageInstalls()
                ) {
                    PermissionStatus.GRANTED
                } else {
                    PermissionStatus.DENIED
                }
            // S1436: an appop, not a runtime permission - checkSelfPermission below always reports
            // DENIED for it, however the user answered on the overlay screen.
            Manifest.permission.SYSTEM_ALERT_WINDOW ->
                if (Settings.canDrawOverlays(appContext)) PermissionStatus.GRANTED
                else PermissionStatus.DENIED
            Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -> {
                val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (pm.isIgnoringBatteryOptimizations(appContext.packageName)) PermissionStatus.GRANTED
                else PermissionStatus.DENIED
            }
            else -> {
                if (ContextCompat.checkSelfPermission(appContext, entry.manifestName) == PackageManager.PERMISSION_GRANTED) {
                    PermissionStatus.GRANTED
                } else if (!requestMarker.wasRequested(entry.id)) {
                    // The platform reports "no rationale" both before the first request and after a
                    // permanent denial; the request marker is what distinguishes the two.
                    PermissionStatus.NOT_YET_REQUESTED
                } else {
                    val activity = context as? Activity
                    if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, entry.manifestName)) {
                        PermissionStatus.PERMANENTLY_DENIED
                    } else {
                        PermissionStatus.DENIED
                    }
                }
            }
        }
}
