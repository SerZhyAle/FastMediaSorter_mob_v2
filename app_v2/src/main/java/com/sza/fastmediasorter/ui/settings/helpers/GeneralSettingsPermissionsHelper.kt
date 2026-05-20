package com.sza.fastmediasorter.ui.settings.helpers

import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.domain.model.PermissionEntry
import com.sza.fastmediasorter.domain.repository.PermissionRegistryRepository
import com.sza.fastmediasorter.domain.usecase.RequestContextualPermissionUseCase
import com.sza.fastmediasorter.ui.common.permissions.PermissionDenialHandler
import com.sza.fastmediasorter.ui.settings.fragments.PermissionsManagementFragment

class GeneralSettingsPermissionsHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val fragment: Fragment,
    private val mediaPermissionsLauncher: ActivityResultLauncher<Array<String>>,
    private val notificationPermissionLauncher: ActivityResultLauncher<String>,
    private val requestContextualPermission: RequestContextualPermissionUseCase,
    private val permissionRegistry: PermissionRegistryRepository,
) {
    // Permission buttons removed - section replaced by btnPermissionsManagement (TonalButton)
    fun updatePermissionButtonsState() = Unit

    fun handleLocalFilesPermissionAction() = navigateToPermissionsManagement()

    fun handleNetworkPermissionAction() {
        val entry = permissionRegistry.getEntries().find { it.id == "access_local_network" }
        if (entry != null) {
            requestContextualPermission.invoke(fragment, entry) { navigateToPermissionsManagement() }
        } else {
            navigateToPermissionsManagement()
        }
    }

    fun handlePermissionPermanentlyDenied(entry: PermissionEntry) =
        PermissionDenialHandler.handle(fragment, entry)

    fun navigateToPermissionsManagement() {
        fragment.parentFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, PermissionsManagementFragment())
            .addToBackStack(null)
            .commit()
    }
}
