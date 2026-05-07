package com.sza.fastmediasorter.ui.common.permissions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.repository.PermissionRegistryRepository
import com.sza.fastmediasorter.domain.usecase.MarkContextualShownUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PermissionRationaleBottomSheet : BottomSheetDialogFragment() {

    interface PermissionRationaleCallback {
        fun onPermissionRationaleResult(permissionId: String, granted: Boolean)
    }

    @Inject lateinit var registry: PermissionRegistryRepository
    @Inject lateinit var markShownUseCase: MarkContextualShownUseCase

    private var callback: PermissionRationaleCallback? = null

    fun setCallback(cb: PermissionRationaleCallback) {
        callback = cb
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.bottom_sheet_permission_rationale, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val permissionId = arguments?.getString(ARG_PERMISSION_ID) ?: return
        val entry = registry.getEntries().firstOrNull { it.id == permissionId } ?: run {
            dismiss()
            return
        }
        if (entry.iconRes != 0) view.findViewById<ImageView>(R.id.iv_perm_icon).setImageResource(entry.iconRes)
        if (entry.titleRes != 0) view.findViewById<TextView>(R.id.tv_perm_title).setText(entry.titleRes)
        if (entry.descriptionRes != 0) view.findViewById<TextView>(R.id.tv_perm_desc).setText(entry.descriptionRes)

        view.findViewById<Button>(R.id.btn_perm_grant).setOnClickListener {
            markShownUseCase.invoke(permissionId)
            callback?.onPermissionRationaleResult(permissionId, true)
            dismiss()
        }
        view.findViewById<Button>(R.id.btn_perm_skip).setOnClickListener {
            markShownUseCase.invoke(permissionId)
            callback?.onPermissionRationaleResult(permissionId, false)
            dismiss()
        }
    }

    companion object {
        private const val ARG_PERMISSION_ID = "permission_id"

        fun newInstance(permissionId: String): PermissionRationaleBottomSheet =
            PermissionRationaleBottomSheet().apply {
                arguments = Bundle().apply { putString(ARG_PERMISSION_ID, permissionId) }
            }
    }
}
