package com.sza.fastmediasorter.wear.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.sza.fastmediasorter.wear.data.onboarding.WearInstallInfoReader
import com.sza.fastmediasorter.wear.domain.permission.WearPermissionGroup
import com.sza.fastmediasorter.wear.domain.permission.WearPlannedPermissionGroup
import com.sza.fastmediasorter.wear.domain.usecase.BuildWearPermissionGroupsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** One row of the permissions screen: what it is for, and whether this watch has granted it. */
data class WearPermissionRow(
    val group: WearPermissionGroup,
    val permissions: List<String>,
    val granted: Boolean,
)

/**
 * S3226: the permissions the user can still turn on after the install, and their current answer.
 *
 * Which rows exist is fixed for the life of the process - it comes from the merged manifest - but the
 * answers are not: the user can change one in the system settings and come back, so [refresh] re-reads
 * them and the screen calls it on every resume rather than trusting what it drew last time.
 */
@HiltViewModel
class PermissionsSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    installInfo: WearInstallInfoReader,
    buildPermissionGroups: BuildWearPermissionGroupsUseCase
) : ViewModel() {

    private val planned: List<WearPlannedPermissionGroup> =
        buildPermissionGroups(installInfo.declaredPermissions, Build.VERSION.SDK_INT)

    private val _rows = MutableStateFlow(planned.map { it.toRow() })
    val rows: StateFlow<List<WearPermissionRow>> = _rows.asStateFlow()

    fun refresh() {
        _rows.value = planned.map { it.toRow() }
    }

    private fun WearPlannedPermissionGroup.toRow(): WearPermissionRow = WearPermissionRow(
        group = group,
        permissions = permissions,
        granted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    )
}
