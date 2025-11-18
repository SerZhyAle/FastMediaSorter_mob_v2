package com.sza.fastmediasorter.ui.editresource

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityEditResourceBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
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
    
    // TextWatcher references for temporary removal
    private var smbServerWatcher: TextWatcher? = null
    private var smbShareNameWatcher: TextWatcher? = null
    private var smbUsernameWatcher: TextWatcher? = null
    private var smbPasswordWatcher: TextWatcher? = null
    private var smbDomainWatcher: TextWatcher? = null
    private var smbPortWatcher: TextWatcher? = null
    
    private var sftpHostWatcher: TextWatcher? = null
    private var sftpPortWatcher: TextWatcher? = null
    private var sftpUsernameWatcher: TextWatcher? = null
    private var sftpPasswordWatcher: TextWatcher? = null
    private var sftpPathWatcher: TextWatcher? = null

    override fun getViewBinding(): ActivityEditResourceBinding {
        return ActivityEditResourceBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Slideshow interval - text input with unit toggle
        binding.etSlideshowInterval.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSlideshowIntervalFromInput()
            }
        })
        
        binding.toggleSlideshowUnit.addOnButtonCheckedListener { _, _, isChecked ->
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
        binding.etResourceName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateName(s?.toString() ?: "")
            }
        })

        // Is destination switch
        binding.switchIsDestination.setOnCheckedChangeListener { _, isChecked ->
            Timber.d("Switch isDestination clicked: $isChecked")
            viewModel.updateIsDestination(isChecked)
        }
        
        // Clear Trash button
        binding.btnClearTrash.setOnClickListener {
            Timber.d("Button CLEAR_TRASH clicked")
            viewModel.requestClearTrash()
        }
        
        // Buttons
        binding.btnReset.setOnClickListener {
            Timber.d("Button RESET clicked")
            viewModel.resetToOriginal()
        }

        binding.btnTest.setOnClickListener {
            Timber.d("Button TEST clicked")
            viewModel.testConnection()
        }

        binding.btnSave.setOnClickListener {
            Timber.d("Button SAVE clicked")
            viewModel.saveChanges()
        }
        
        // Initialize SMB credentials listeners
        addSmbListeners()
        
        // Initialize SFTP credentials listeners
        addSftpListeners()
    }
    
    private fun addSmbListeners() {
        smbServerWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateSmbServer(s?.toString() ?: "")
            }
        }
        binding.etSmbServerEdit.addTextChangedListener(smbServerWatcher)
        
        smbShareNameWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateSmbShareName(s?.toString() ?: "")
            }
        }
        binding.etSmbShareNameEdit.addTextChangedListener(smbShareNameWatcher)
        
        smbUsernameWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateSmbUsername(s?.toString() ?: "")
            }
        }
        binding.etSmbUsernameEdit.addTextChangedListener(smbUsernameWatcher)
        
        smbPasswordWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateSmbPassword(s?.toString() ?: "")
            }
        }
        binding.etSmbPasswordEdit.addTextChangedListener(smbPasswordWatcher)
        
        smbDomainWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateSmbDomain(s?.toString() ?: "")
            }
        }
        binding.etSmbDomainEdit.addTextChangedListener(smbDomainWatcher)
        
        smbPortWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val port = s?.toString()?.toIntOrNull() ?: 445
                viewModel.updateSmbPort(port)
            }
        }
        binding.etSmbPortEdit.addTextChangedListener(smbPortWatcher)
    }
    
    private fun removeSmbListeners() {
        smbServerWatcher?.let { binding.etSmbServerEdit.removeTextChangedListener(it) }
        smbShareNameWatcher?.let { binding.etSmbShareNameEdit.removeTextChangedListener(it) }
        smbUsernameWatcher?.let { binding.etSmbUsernameEdit.removeTextChangedListener(it) }
        smbPasswordWatcher?.let { binding.etSmbPasswordEdit.removeTextChangedListener(it) }
        smbDomainWatcher?.let { binding.etSmbDomainEdit.removeTextChangedListener(it) }
        smbPortWatcher?.let { binding.etSmbPortEdit.removeTextChangedListener(it) }
    }
    
    private fun addSftpListeners() {
        sftpHostWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateSftpHost(s?.toString() ?: "")
            }
        }
        binding.etSftpHostEdit.addTextChangedListener(sftpHostWatcher)
        
        sftpPortWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val port = s?.toString()?.toIntOrNull() ?: 22
                viewModel.updateSftpPort(port)
            }
        }
        binding.etSftpPortEdit.addTextChangedListener(sftpPortWatcher)
        
        sftpUsernameWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateSftpUsername(s?.toString() ?: "")
            }
        }
        binding.etSftpUsernameEdit.addTextChangedListener(sftpUsernameWatcher)
        
        sftpPasswordWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateSftpPassword(s?.toString() ?: "")
            }
        }
        binding.etSftpPasswordEdit.addTextChangedListener(sftpPasswordWatcher)
        
        sftpPathWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateSftpPath(s?.toString() ?: "")
            }
        }
        binding.etSftpPathEdit.addTextChangedListener(sftpPathWatcher)
    }
    
    private fun removeSftpListeners() {
        sftpHostWatcher?.let { binding.etSftpHostEdit.removeTextChangedListener(it) }
        sftpPortWatcher?.let { binding.etSftpPortEdit.removeTextChangedListener(it) }
        sftpUsernameWatcher?.let { binding.etSftpUsernameEdit.removeTextChangedListener(it) }
        sftpPasswordWatcher?.let { binding.etSftpPasswordEdit.removeTextChangedListener(it) }
        sftpPathWatcher?.let { binding.etSftpPathEdit.removeTextChangedListener(it) }
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
                            ResourceType.FTP -> getString(R.string.resource_type_ftp)
                            ResourceType.CLOUD -> getString(R.string.resource_type_cloud)
                        }
                        binding.toolbar.title = getString(R.string.edit_resource_with_type, resourceTypeLabel)
                        
                        // Update UI with resource data
                        binding.etResourceName.setText(resource.name)
                        binding.etResourcePath.setText(resource.path)
                        binding.tvCreatedDate.text = dateFormat.format(Date(resource.createdDate))
                        binding.tvFileCount.text = resource.fileCount.toString()
                        
                        // Display last browse date or "Never browsed"
                        binding.tvLastBrowseDate.text = resource.lastBrowseDate?.let {
                            dateFormat.format(Date(it))
                        } ?: getString(R.string.never_browsed)

                        // Slideshow interval - convert to input field
                        updateSlideshowIntervalUI(resource.slideshowInterval)

                        // Media types
                        binding.cbSupportImages.isChecked = MediaType.IMAGE in resource.supportedMediaTypes
                        binding.cbSupportVideo.isChecked = MediaType.VIDEO in resource.supportedMediaTypes
                        binding.cbSupportAudio.isChecked = MediaType.AUDIO in resource.supportedMediaTypes
                        binding.cbSupportGif.isChecked = MediaType.GIF in resource.supportedMediaTypes

                        // Is destination - temporarily remove listener to avoid triggering on programmatic change
                        binding.switchIsDestination.setOnCheckedChangeListener(null)
                        binding.switchIsDestination.isChecked = resource.isDestination
                        binding.switchIsDestination.setOnCheckedChangeListener { _, isChecked ->
                            viewModel.updateIsDestination(isChecked)
                        }
                        
                        // Show/hide credentials sections based on resource type
                        binding.layoutSmbCredentials.isVisible = resource.type == ResourceType.SMB
                        binding.layoutSftpCredentials.isVisible = resource.type == ResourceType.SFTP
                    }
                    
                    // Show/hide Clear Trash button
                    binding.btnClearTrash.isVisible = state.hasTrashFolders
                    
                    // Update SMB credentials UI (remove listeners temporarily to avoid triggering)
                    if (state.currentResource?.type == ResourceType.SMB) {
                        removeSmbListeners()
                        binding.etSmbServerEdit.setText(state.smbServer)
                        binding.etSmbShareNameEdit.setText(state.smbShareName)
                        binding.etSmbUsernameEdit.setText(state.smbUsername)
                        binding.etSmbPasswordEdit.setText(state.smbPassword)
                        binding.etSmbDomainEdit.setText(state.smbDomain)
                        binding.etSmbPortEdit.setText(state.smbPort.toString())
                        addSmbListeners()
                    }
                    
                    // Update SFTP credentials UI (remove listeners temporarily to avoid triggering)
                    if (state.currentResource?.type == ResourceType.SFTP) {
                        removeSftpListeners()
                        binding.etSftpHostEdit.setText(state.sftpHost)
                        binding.etSftpPortEdit.setText(state.sftpPort.toString())
                        binding.etSftpUsernameEdit.setText(state.sftpUsername)
                        binding.etSftpPasswordEdit.setText(state.sftpPassword)
                        binding.etSftpPathEdit.setText(state.sftpPath)
                        addSftpListeners()
                    }

                    // Enable/disable Save button based on hasChanges
                    binding.btnSave.isEnabled = state.hasResourceChanges || state.hasSmbCredentialsChanges || state.hasSftpCredentialsChanges
                    binding.btnReset.isEnabled = state.hasResourceChanges || state.hasSmbCredentialsChanges || state.hasSftpCredentialsChanges
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
                            showTestResultDialog(event.message, event.success)
                            
                            // If test successful but credentials not saved, offer to save automatically
                            if (event.success) {
                                val state = viewModel.state.value
                                if (state.hasSmbCredentialsChanges || state.hasSftpCredentialsChanges) {
                                    androidx.appcompat.app.AlertDialog.Builder(this@EditResourceActivity)
                                        .setTitle("Save Credentials?")
                                        .setMessage(
                                            "Connection test successful!\n\n" +
                                            "Do you want to save these credentials now?\n\n" +
                                            "⚠️ Without saving, the resource will use old credentials and may fail to open."
                                        )
                                        .setPositiveButton("Save Now") { _, _ ->
                                            viewModel.saveChanges()
                                        }
                                        .setNegativeButton("Later", null)
                                        .show()
                                }
                            }
                        }
                        is EditResourceEvent.ConfirmClearTrash -> {
                            showClearTrashConfirmDialog(event.count)
                        }
                        is EditResourceEvent.TrashCleared -> {
                            Toast.makeText(
                                this@EditResourceActivity,
                                getString(R.string.trash_cleared, event.count),
                                Toast.LENGTH_SHORT
                            ).show()
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
    
    private fun showTestResultDialog(message: String, isSuccess: Boolean) {
        val title = if (isSuccess) "Connection Test - Success" else "Connection Test - Failed"
        
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Copy") { _, _ ->
                copyToClipboard(message)
            }
            .show()
    }
    
    private fun showClearTrashConfirmDialog(count: Int) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.clear_trash_confirm_title))
            .setMessage(getString(R.string.clear_trash_confirm_message, count))
            .setPositiveButton(R.string.clear_trash) { _, _ ->
                viewModel.clearTrash()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Test Result", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
