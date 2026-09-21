package com.sza.fastmediasorter.ui.scheduledops

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityScheduledOperationsBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/** Host for the scheduled-operations program. Screen behavior lives in [ScheduledOperationsScreenManager]. */
@AndroidEntryPoint
class ScheduledOperationsActivity : BaseActivity<ActivityScheduledOperationsBinding>() {

    companion object {
        const val EXTRA_SOURCE_RESOURCE_ID = "extra_source_resource_id"
    }

    @Inject
    lateinit var mediaCapabilities: MediaCapabilities

    private val scheduledViewModel: ScheduledOperationsViewModel by viewModels()
    private lateinit var screenManager: ScheduledOperationsScreenManager

    private val notificationsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            screenManager.updateNotificationPermissionButton()
        }

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            screenManager.onFolderPicked(uri)
        }

    override fun getViewBinding(): ActivityScheduledOperationsBinding =
        ActivityScheduledOperationsBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("S3365: program screen onCreate")
    }

    override fun setupViews() {
        screenManager = ScheduledOperationsScreenManager(
            activity = this,
            binding = binding,
            mediaCapabilities = mediaCapabilities,
            scheduledViewModel = scheduledViewModel,
            notificationsPermissionLauncher = notificationsPermissionLauncher,
            folderPickerLauncher = folderPickerLauncher,
        )
        screenManager.setupViews()
    }

    override fun observeData() {
        screenManager.observeData()
    }

    override fun onResume() {
        super.onResume()
        screenManager.updateNotificationPermissionButton()
    }
}
