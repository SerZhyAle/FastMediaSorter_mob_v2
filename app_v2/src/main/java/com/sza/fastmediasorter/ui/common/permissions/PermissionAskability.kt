package com.sza.fastmediasorter.ui.common.permissions

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.domain.repository.PermissionRequestMarkerRepository
import dagger.hilt.android.EntryPointAccessors

/**
 * Whether firing the system dialog for [permission] would show the user anything at all.
 *
 * False once the permission is granted, and false again once it is permanently denied - the platform
 * then returns from the request instantly with nothing on screen, so an explanation shown beforehand
 * would be a dialog asking for something that can no longer be granted here. The platform reports the
 * same "no rationale" answer before the very first request, so the request marker rather than the
 * platform is what tells those two apart, exactly as
 * [com.sza.fastmediasorter.domain.usecase.CheckPermissionStatusUseCase] does for the settings list.
 */
fun Activity.canRequestPermission(permission: String): Boolean {
    val granted = ContextCompat.checkSelfPermission(this, permission) ==
        PackageManager.PERMISSION_GRANTED
    val everAsked = permissionEntryId(permission)?.let { permissionMarker().wasRequested(it) } == true
    return !granted &&
        (!everAsked || ActivityCompat.shouldShowRequestPermissionRationale(this, permission))
}

/**
 * Records that this call site fired the system dialog for [permission].
 *
 * Mandatory for every place that asks, not only the settings list: the marker is shared, so a request
 * that skips it leaves [canRequestPermission] believing the user was never asked.
 */
fun Context.markPermissionRequested(permission: String) {
    permissionEntryId(permission)?.let { permissionMarker().markRequested(it) }
}

/** Null in a build whose gates keep the permission out of the registry - then nothing is recorded. */
private fun Context.permissionEntryId(permission: String): String? =
    askability().permissionRegistry().getEntries()
        .firstOrNull { it.manifestName == permission }
        ?.id

private fun Context.permissionMarker(): PermissionRequestMarkerRepository =
    askability().permissionRequestMarker()

private fun Context.askability(): PermissionAskabilityEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, PermissionAskabilityEntryPoint::class.java)
