package com.sza.fastmediasorter.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sza.fastmediasorter.databinding.DialogResourceTypeSelectorBinding
import com.sza.fastmediasorter.domain.model.ResourceType

/**
 * Bottom sheet dialog for selecting resource type when creating a new resource.
 * Shows cards: Local / SMB / SFTP / FTP / Cloud.
 */
class ResourceTypeSelectorDialog : BottomSheetDialogFragment() {

    private var _binding: DialogResourceTypeSelectorBinding? = null
    private val binding get() = _binding!!

    var onTypeSelected: ((ResourceType) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogResourceTypeSelectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardLocal.setOnClickListener {
            onTypeSelected?.invoke(ResourceType.LOCAL)
            dismiss()
        }

        binding.cardSmb.setOnClickListener {
            onTypeSelected?.invoke(ResourceType.SMB)
            dismiss()
        }

        binding.cardSftp.setOnClickListener {
            onTypeSelected?.invoke(ResourceType.SFTP)
            dismiss()
        }

        binding.cardFtp.setOnClickListener {
            onTypeSelected?.invoke(ResourceType.FTP)
            dismiss()
        }

        binding.cardCloud.setOnClickListener {
            onTypeSelected?.invoke(ResourceType.CLOUD)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ResourceTypeSelectorDialog"

        fun newInstance(): ResourceTypeSelectorDialog = ResourceTypeSelectorDialog()
    }
}
