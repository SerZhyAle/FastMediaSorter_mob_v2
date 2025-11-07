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

        // Slideshow interval slider
        binding.sliderSlideshowInterval.addOnChangeListener { _, value, _ ->
            updateSlideshowIntervalText(value.toInt())
            viewModel.updateSlideshowInterval(value.toInt())
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

        // Buttons
        binding.btnBack.setOnClickListener {
            finish()
        }

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
                        // Update UI with resource data
                        binding.etResourceName.setText(resource.name)
                        binding.etResourcePath.setText(resource.path)
                        binding.tvResourceType.text = resource.type.name
                        binding.tvCreatedDate.text = dateFormat.format(Date(resource.createdDate))
                        binding.tvFileCount.text = getString(R.string.file_count) + ": ${resource.fileCount}"

                        // Slideshow interval
                        binding.sliderSlideshowInterval.value = resource.slideshowInterval.toFloat()
                        updateSlideshowIntervalText(resource.slideshowInterval)

                        // Media types
                        binding.cbSupportImages.isChecked = MediaType.IMAGE in resource.supportedMediaTypes
                        binding.cbSupportVideo.isChecked = MediaType.VIDEO in resource.supportedMediaTypes
                        binding.cbSupportAudio.isChecked = MediaType.AUDIO in resource.supportedMediaTypes
                        binding.cbSupportGif.isChecked = MediaType.GIF in resource.supportedMediaTypes

                        // Is destination
                        binding.switchIsDestination.isChecked = resource.isDestination
                    }

                    // Enable/disable Save button based on hasChanges
                    binding.btnSave.isEnabled = state.hasChanges
                    binding.btnReset.isEnabled = state.hasChanges
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

    private fun updateSlideshowIntervalText(value: Int) {
        val text = if (value < 60) {
            "$value sec"
        } else {
            val minutes = value / 60
            val seconds = value % 60
            if (seconds == 0) {
                "$minutes min"
            } else {
                "$minutes min $seconds sec"
            }
        }
        binding.tvSlideshowIntervalValue.text = text
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
