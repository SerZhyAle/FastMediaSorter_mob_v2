package com.sza.fastmediasorter.domain.usecase

import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.domain.model.PermissionEntry
import com.sza.fastmediasorter.ui.common.permissions.PermissionRationaleBottomSheet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RequestContextualPermissionUseCase @Inject constructor(
    private val markContextualShownUseCase: MarkContextualShownUseCase
) {
    fun invoke(fragment: Fragment, entry: PermissionEntry, onResult: (Boolean) -> Unit) {
        if (markContextualShownUseCase.isShown(entry.id)) {
            onResult(false)
            return
        }
        val sheet = PermissionRationaleBottomSheet.newInstance(entry.id)
        sheet.setCallback(object : PermissionRationaleBottomSheet.PermissionRationaleCallback {
            override fun onPermissionRationaleResult(permissionId: String, granted: Boolean) {
                onResult(granted)
            }
        })
        sheet.show(fragment.parentFragmentManager, "perm_rationale_${entry.id}")
    }
}
