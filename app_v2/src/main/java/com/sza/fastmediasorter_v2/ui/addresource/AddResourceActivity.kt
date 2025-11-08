package com.sza.fastmediasorter_v2.ui.addresource

import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter_v2.core.ui.BaseActivity
import com.sza.fastmediasorter_v2.databinding.ActivityAddResourceBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class AddResourceActivity : BaseActivity<ActivityAddResourceBinding>() {

    private val viewModel: AddResourceViewModel by viewModels()
    private lateinit var resourceToAddAdapter: ResourceToAddAdapter
    private lateinit var smbResourceToAddAdapter: ResourceToAddAdapter

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            Timber.d("Selected folder: $uri")
            viewModel.addManualFolder(uri)
        }
    }

    override fun getViewBinding(): ActivityAddResourceBinding {
        return ActivityAddResourceBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        resourceToAddAdapter = ResourceToAddAdapter(
            onSelectionChanged = { resource, selected ->
                viewModel.toggleResourceSelection(resource, selected)
            },
            onNameChanged = { resource, newName ->
                viewModel.updateResourceName(resource, newName)
            },
            onDestinationChanged = { resource, isDestination ->
                viewModel.toggleDestination(resource, isDestination)
            }
        )
        
        binding.rvResourcesToAdd.adapter = resourceToAddAdapter
        
        smbResourceToAddAdapter = ResourceToAddAdapter(
            onSelectionChanged = { resource, selected ->
                viewModel.toggleResourceSelection(resource, selected)
            },
            onNameChanged = { resource, newName ->
                viewModel.updateResourceName(resource, newName)
            },
            onDestinationChanged = { resource, isDestination ->
                viewModel.toggleDestination(resource, isDestination)
            }
        )
        
        binding.rvSmbResourcesToAdd.adapter = smbResourceToAddAdapter

        binding.cardLocalFolder.setOnClickListener {
            showLocalFolderOptions()
        }

        binding.cardNetworkFolder.setOnClickListener {
            showSmbFolderOptions()
        }

        binding.btnScan.setOnClickListener {
            viewModel.scanLocalFolders()
        }

        binding.btnAddManually.setOnClickListener {
            folderPickerLauncher.launch(null)
        }

        binding.btnAddToResources.setOnClickListener {
            viewModel.addSelectedResources()
        }

        // SMB buttons
        binding.btnSmbTest.setOnClickListener {
            testSmbConnection()
        }

        binding.btnSmbScan.setOnClickListener {
            scanSmbShares()
        }

        binding.btnSmbAddToResources.setOnClickListener {
            addSmbResources()
        }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    // Filter resources by type
                    val localResources = state.resourcesToAdd.filter { 
                        it.type == com.sza.fastmediasorter_v2.domain.model.ResourceType.LOCAL 
                    }
                    val smbResources = state.resourcesToAdd.filter { 
                        it.type == com.sza.fastmediasorter_v2.domain.model.ResourceType.SMB 
                    }
                    
                    // Update adapters
                    resourceToAddAdapter.submitList(localResources)
                    resourceToAddAdapter.setSelectedPaths(state.selectedPaths)
                    
                    smbResourceToAddAdapter.submitList(smbResources)
                    smbResourceToAddAdapter.setSelectedPaths(state.selectedPaths)
                    
                    // Local folder UI visibility
                    val hasLocalResources = localResources.isNotEmpty()
                    binding.tvResourcesToAdd.isVisible = hasLocalResources
                    binding.rvResourcesToAdd.isVisible = hasLocalResources
                    binding.btnAddToResources.isVisible = hasLocalResources
                    
                    // SMB folder UI visibility
                    val hasSmbResources = smbResources.isNotEmpty()
                    binding.tvSmbResourcesToAdd.isVisible = hasSmbResources
                    binding.rvSmbResourcesToAdd.isVisible = hasSmbResources
                    binding.btnSmbAddToResources.isVisible = hasSmbResources
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
                        is AddResourceEvent.ShowError -> {
                            Toast.makeText(this@AddResourceActivity, event.message, Toast.LENGTH_LONG).show()
                        }
                        is AddResourceEvent.ShowMessage -> {
                            Toast.makeText(this@AddResourceActivity, event.message, Toast.LENGTH_SHORT).show()
                        }
                        AddResourceEvent.ResourcesAdded -> {
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun showLocalFolderOptions() {
        binding.layoutResourceTypes.isVisible = false
        binding.layoutLocalFolder.isVisible = true
    }

    private fun showSmbFolderOptions() {
        binding.layoutResourceTypes.isVisible = false
        binding.layoutSmbFolder.isVisible = true
    }

    private fun testSmbConnection() {
        val server = binding.etSmbServer.text.toString().trim()
        val shareName = binding.etSmbShareName.text.toString().trim()
        val username = binding.etSmbUsername.text.toString().trim()
        val password = binding.etSmbPassword.text.toString().trim()
        val domain = binding.etSmbDomain.text.toString().trim()
        val portStr = binding.etSmbPort.text.toString().trim()
        val port = portStr.toIntOrNull() ?: 445

        if (server.isEmpty()) {
            Toast.makeText(this, "Server address is required", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (shareName.isEmpty()) {
            Toast.makeText(this, "Share name is required", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.testSmbConnection(server, shareName, username, password, domain, port)
    }

    private fun scanSmbShares() {
        val server = binding.etSmbServer.text.toString().trim()
        val username = binding.etSmbUsername.text.toString().trim()
        val password = binding.etSmbPassword.text.toString().trim()
        val domain = binding.etSmbDomain.text.toString().trim()
        val portStr = binding.etSmbPort.text.toString().trim()
        val port = portStr.toIntOrNull() ?: 445

        if (server.isEmpty()) {
            Toast.makeText(this, "Server address is required", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.scanSmbShares(server, username, password, domain, port)
    }

    private fun addSmbResources() {
        val server = binding.etSmbServer.text.toString().trim()
        val shareName = binding.etSmbShareName.text.toString().trim()
        val username = binding.etSmbUsername.text.toString().trim()
        val password = binding.etSmbPassword.text.toString().trim()
        val domain = binding.etSmbDomain.text.toString().trim()
        val portStr = binding.etSmbPort.text.toString().trim()
        val port = portStr.toIntOrNull() ?: 445

        if (server.isEmpty()) {
            Toast.makeText(this, "Server address is required", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (shareName.isEmpty()) {
            Toast.makeText(this, "Share name is required", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.addSmbResources(server, shareName, username, password, domain, port)
    }
}
