package com.sza.fastmediasorter_v2.ui.main

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter_v2.core.ui.BaseActivity
import com.sza.fastmediasorter_v2.databinding.ActivityMainBinding
import com.sza.fastmediasorter_v2.ui.addresource.AddResourceActivity
import com.sza.fastmediasorter_v2.ui.browse.BrowseActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var resourceAdapter: ResourceAdapter

    override fun getViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        resourceAdapter = ResourceAdapter(
            onItemClick = { resource ->
                viewModel.selectResource(resource)
            },
            onItemDoubleClick = { resource ->
                viewModel.selectResource(resource)
                viewModel.startPlayer()
            },
            onItemLongClick = { resource ->
                viewModel.selectResource(resource)
                viewModel.startPlayer()
            },
            onEditClick = { resource ->
                // TODO: Навигация к экрану редактирования
                Timber.d("Edit resource: ${resource.name}")
            },
            onDeleteClick = { resource ->
                showDeleteConfirmation(resource)
            },
            onMoveUpClick = { resource ->
                viewModel.moveResourceUp(resource)
            },
            onMoveDownClick = { resource ->
                viewModel.moveResourceDown(resource)
            }
        )
        
        binding.rvResources.adapter = resourceAdapter
        
        binding.btnStartPlayer.setOnClickListener {
            viewModel.startPlayer()
        }
        
        binding.btnAddResource.setOnClickListener {
            viewModel.addResource()
        }
        
        binding.btnSettings.setOnClickListener {
            // TODO: Навигация к настройкам
            Timber.d("Open settings")
        }
        
        binding.btnFilter.setOnClickListener {
            // TODO: Показать диалог фильтрации
            Timber.d("Open filter")
        }
        
        binding.btnRefresh.setOnClickListener {
            // TODO: Обновить список ресурсов
            Timber.d("Refresh resources")
        }
        
        binding.btnCopyResource.setOnClickListener {
            viewModel.copySelectedResource()
        }
        
        binding.btnExit.setOnClickListener {
            finish()
        }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    resourceAdapter.submitList(state.resources)
                    resourceAdapter.setSelectedResource(state.selectedResource?.id)
                    
                    binding.btnStartPlayer.isEnabled = state.selectedResource != null
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
                        is MainEvent.ShowError -> {
                            Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_LONG).show()
                        }
                        is MainEvent.ShowMessage -> {
                            Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                        }
                        is MainEvent.NavigateToBrowse -> {
                            startActivity(BrowseActivity.createIntent(this@MainActivity, event.resourceId))
                        }
                        is MainEvent.NavigateToAddResource -> {
                            startActivity(Intent(this@MainActivity, AddResourceActivity::class.java))
                        }
                        MainEvent.NavigateToSettings -> {
                            // TODO: Навигация к Settings экрану
                            Timber.d("Navigate to settings")
                        }
                    }
                }
            }
        }
    }
    
    private fun showDeleteConfirmation(resource: com.sza.fastmediasorter_v2.domain.model.MediaResource) {
        AlertDialog.Builder(this)
            .setTitle("Delete Resource")
            .setMessage("Are you sure you want to delete ${resource.name}?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteResource(resource)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
