package com.sza.fastmediasorter.ui.common.permissions

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.domain.repository.PermissionRequestMarkerRepository
import com.sza.fastmediasorter.util.getPackageInfoCompat
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
 *
 * Also false when this build does not declare [permission] in its merged manifest (S4030: the Play
 * rollback strips `READ_CONTACTS` from the standard build). The registry cannot decide that here: a
 * missing row also describes permissions that are asked for without being rows, so only the package
 * manager's own list of requested permissions tells "not declared" from "not a row".
 */
fun Activity.canRequestPermission(permission: String): Boolean =
    isPermissionRequestable(
        declared = isPermissionDeclared(permission),
        granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED,
        everAsked = permissionEntryId(permission)?.let { permissionMarker().wasRequested(it) } == true,
        shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, permission),
    )

/** The decision behind [canRequestPermission], kept free of framework calls so a plain unit test can drive it. */
internal fun isPermissionRequestable(
    declared: Boolean,
    granted: Boolean,
    everAsked: Boolean,
    shouldShowRationale: Boolean,
): Boolean = declared && !granted && (!everAsked || shouldShowRationale)

/**
 * Records that this call site fired the system dialog for [permission].
 *
 * Mandatory for every place that asks, not only the settings list: the marker is shared, so a request
 * that skips it leaves [canRequestPermission] believing the user was never asked.
 */
fun Context.markPermissionRequested(permission: String) {
    permissionEntryId(permission)?.let { permissionMarker().markRequested(it) }
}

private fun Context.isPermissionDeclared(permission: String): Boolean =
    try {
        packageManager.getPackageInfoCompat(packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.contains(permission) == true
    } catch (ignored: PackageManager.NameNotFoundException) {
        // The app's own package is always installed; if the lookup still fails, asking is the unsafe answer.
        false
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
