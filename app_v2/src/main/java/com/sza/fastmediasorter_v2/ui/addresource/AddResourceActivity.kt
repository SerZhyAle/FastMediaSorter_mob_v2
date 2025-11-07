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

        binding.cardLocalFolder.setOnClickListener {
            showLocalFolderOptions()
        }

        binding.cardNetworkFolder.setOnClickListener {
            Toast.makeText(this, "Network folders - Coming Soon", Toast.LENGTH_SHORT).show()
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
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    resourceToAddAdapter.submitList(state.resourcesToAdd)
                    resourceToAddAdapter.setSelectedPaths(state.selectedPaths)
                    
                    val hasResources = state.resourcesToAdd.isNotEmpty()
                    binding.tvResourcesToAdd.isVisible = hasResources
                    binding.rvResourcesToAdd.isVisible = hasResources
                    binding.btnAddToResources.isVisible = hasResources
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
}
