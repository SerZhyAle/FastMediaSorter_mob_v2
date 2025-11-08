package com.sza.fastmediasorter_v2.ui.editresource

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter_v2.R
import com.sza.fastmediasorter_v2.core.ui.BaseActivity
import com.sza.fastmediasorter_v2.databinding.ActivityEditResourceBinding
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.domain.model.ResourceType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class EditResourceActivity : BaseActivity<ActivityEditResourceBinding>() {

    private val viewModel: EditResourceViewModel by viewModels()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun getViewBinding(): ActivityEditResourceBinding {
        return ActivityEditResourceBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Slideshow interval - text input with unit toggle
        binding.etSlideshowInterval.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                updateSlideshowIntervalFromInput()
            }
        }
        
        binding.toggleSlideshowUnit.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                updateSlideshowIntervalFromInput()
            }
        }

        // Media type checkboxes
        binding.cbSupportImages.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbSupportVideo.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbSupportAudio.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }
        binding.cbSupportGif.setOnCheckedChangeListener { _, _ -> updateMediaTypes() }

        // Resource name
        binding.etResourceName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val name = binding.etResourceName.text.toString()
                viewModel.updateName(name)
            }
        }

        // Is destination switch
        binding.switchIsDestination.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateIsDestination(isChecked)
        }
        
        // SMB credentials listeners
        binding.etSmbServerEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.updateSmbServer(binding.etSmbServerEdit.text.toString())
            }
        }
        
        binding.etSmbShareNameEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.updateSmbShareName(binding.etSmbShareNameEdit.text.toString())
            }
        }
        
        binding.etSmbUsernameEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.updateSmbUsername(binding.etSmbUsernameEdit.text.toString())
            }
        }
        
        binding.etSmbPasswordEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.updateSmbPassword(binding.etSmbPasswordEdit.text.toString())
            }
        }
        
        binding.etSmbDomainEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.updateSmbDomain(binding.etSmbDomainEdit.text.toString())
            }
        }
        
        binding.etSmbPortEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val port = binding.etSmbPortEdit.text.toString().toIntOrNull() ?: 445
                viewModel.updateSmbPort(port)
            }
        }
        
        // SFTP credentials listeners
        binding.etSftpHostEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.updateSftpHost(binding.etSftpHostEdit.text.toString())
            }
        }
        
        binding.etSftpPortEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val port = binding.etSftpPortEdit.text.toString().toIntOrNull() ?: 22
                viewModel.updateSftpPort(port)
            }
        }
        
        binding.etSftpUsernameEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.updateSftpUsername(binding.etSftpUsernameEdit.text.toString())
            }
        }
        
        binding.etSftpPasswordEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.updateSftpPassword(binding.etSftpPasswordEdit.text.toString())
            }
        }
        
        binding.etSftpPathEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.updateSftpPath(binding.etSftpPathEdit.text.toString())
            }
        }

        // Buttons
        binding.btnReset.setOnClickListener {
            viewModel.resetToOriginal()
        }

        binding.btnTest.setOnClickListener {
            viewModel.testConnection()
        }

        binding.btnSave.setOnClickListener {
            viewModel.saveChanges()
        }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    state.currentResource?.let { resource ->
                        // Update toolbar title with resource type
                        val resourceTypeLabel = when (resource.type) {
                            ResourceType.LOCAL -> getString(R.string.resource_type_local)
                            ResourceType.SMB -> getString(R.string.resource_type_smb)
                            ResourceType.SFTP -> getString(R.string.resource_type_sftp)
                            ResourceType.CLOUD -> getString(R.string.resource_type_cloud)
                        }
                        binding.toolbar.title = getString(R.string.edit_resource_with_type, resourceTypeLabel)
                        
                        // Update UI with resource data
                        binding.etResourceName.setText(resource.name)
                        binding.etResourcePath.setText(resource.path)
                        binding.tvCreatedDate.text = dateFormat.format(Date(resource.createdDate))
                        binding.tvFileCount.text = resource.fileCount.toString()

                        // Slideshow interval - convert to input field
                        updateSlideshowIntervalUI(resource.slideshowInterval)

                        // Media types
                        binding.cbSupportImages.isChecked = MediaType.IMAGE in resource.supportedMediaTypes
                        binding.cbSupportVideo.isChecked = MediaType.VIDEO in resource.supportedMediaTypes
                        binding.cbSupportAudio.isChecked = MediaType.AUDIO in resource.supportedMediaTypes
                        binding.cbSupportGif.isChecked = MediaType.GIF in resource.supportedMediaTypes

                        // Is destination
                        binding.switchIsDestination.isChecked = resource.isDestination
                        
                        // Show/hide credentials sections based on resource type
                        binding.layoutSmbCredentials.isVisible = resource.type == ResourceType.SMB
                        binding.layoutSftpCredentials.isVisible = resource.type == ResourceType.SFTP
                    }
                    
                    // Update SMB credentials UI
                    if (state.currentResource?.type == ResourceType.SMB) {
                        binding.etSmbServerEdit.setText(state.smbServer)
                        binding.etSmbShareNameEdit.setText(state.smbShareName)
                        binding.etSmbUsernameEdit.setText(state.smbUsername)
                        binding.etSmbPasswordEdit.setText(state.smbPassword)
                        binding.etSmbDomainEdit.setText(state.smbDomain)
                        binding.etSmbPortEdit.setText(state.smbPort.toString())
                    }
                    
                    // Update SFTP credentials UI
                    if (state.currentResource?.type == ResourceType.SFTP) {
                        binding.etSftpHostEdit.setText(state.sftpHost)
                        binding.etSftpPortEdit.setText(state.sftpPort.toString())
                        binding.etSftpUsernameEdit.setText(state.sftpUsername)
                        binding.etSftpPasswordEdit.setText(state.sftpPassword)
                        binding.etSftpPathEdit.setText(state.sftpPath)
                    }

                    // Enable/disable Save button based on hasChanges
                    binding.btnSave.isEnabled = state.hasChanges || state.hasSmbCredentialsChanges || state.hasSftpCredentialsChanges
                    binding.btnReset.isEnabled = state.hasChanges || state.hasSmbCredentialsChanges || state.hasSftpCredentialsChanges
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loading.collect { isLoading ->
                    binding.progressBar.isVisible = isLoading
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is EditResourceEvent.ShowError -> {
                            Toast.makeText(this@EditResourceActivity, event.message, Toast.LENGTH_SHORT).show()
                        }
                        is EditResourceEvent.ShowMessage -> {
                            Toast.makeText(this@EditResourceActivity, event.message, Toast.LENGTH_SHORT).show()
                        }
                        is EditResourceEvent.ResourceUpdated -> {
                            Toast.makeText(this@EditResourceActivity, getString(R.string.resource_updated), Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        is EditResourceEvent.TestResult -> {
                            val message = if (event.success) {
                                getString(R.string.test_successful)
                            } else {
                                getString(R.string.test_failed, event.message)
                            }
                            Toast.makeText(this@EditResourceActivity, message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun updateSlideshowIntervalUI(totalSeconds: Int) {
        // Determine best unit (prefer seconds if <= 60, otherwise minutes)
        if (totalSeconds <= 60) {
            binding.etSlideshowInterval.setText(totalSeconds.toString())
            binding.btnSeconds.isChecked = true
        } else {
            val minutes = totalSeconds / 60
            binding.etSlideshowInterval.setText(minutes.toString())
            binding.btnMinutes.isChecked = true
        }
    }

    private fun updateSlideshowIntervalFromInput() {
        val inputValue = binding.etSlideshowInterval.text.toString().toIntOrNull() ?: return
        val clampedValue = inputValue.coerceIn(1, 60)
        
        if (inputValue != clampedValue) {
            binding.etSlideshowInterval.setText(clampedValue.toString())
        }
        
        val totalSeconds = if (binding.btnMinutes.isChecked) {
            clampedValue * 60
        } else {
            clampedValue
        }
        
        viewModel.updateSlideshowInterval(totalSeconds)
    }

    private fun updateMediaTypes() {
        val types = mutableSetOf<MediaType>()
        if (binding.cbSupportImages.isChecked) types.add(MediaType.IMAGE)
        if (binding.cbSupportVideo.isChecked) types.add(MediaType.VIDEO)
        if (binding.cbSupportAudio.isChecked) types.add(MediaType.AUDIO)
        if (binding.cbSupportGif.isChecked) types.add(MediaType.GIF)
        viewModel.updateSupportedMediaTypes(types)
    }
}
