package com.sza.fastmediasorter.ui.scheduledops

import android.net.Uri
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityScheduledOperationsBinding
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.ui.common.support.DocsPageOpenManager
import dagger.hilt.android.AndroidEntryPoint
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

    // A recreated activity gets its pending result at ON_START, before the deferred setupViews()
    // builds screenManager. A permission result can be dropped - onResumeWithViews() refreshes the
    // button - but a folder pick waits in the ViewModel for the dialog the screen reopens.
    private val notificationsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (::screenManager.isInitialized) screenManager.updateNotificationPermissionButton()
        }

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (::screenManager.isInitialized) {
                screenManager.onFolderPicked(uri)
            } else {
                scheduledViewModel.deferFolderPick(uri)
            }
        }

    override fun getViewBinding(): ActivityScheduledOperationsBinding =
        ActivityScheduledOperationsBinding.inflate(layoutInflater)

    override fun getInputHelpSurface(): UiSurface = UiSurface.SCHEDULED_OPS

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_scheduled_ops, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.action_help) {
            DocsPageOpenManager.open(this, UiSurface.SCHEDULED_OPS)
            true
        } else {
            super.onOptionsItemSelected(item)
        }
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

    // BaseActivity defers setupViews() to binding.root.post{}, so onResume() fires before
    // screenManager exists; onResumeWithViews() is the contract for post-setup resume work.
    override fun onResumeWithViews() {
        super.onResumeWithViews()
        screenManager.updateNotificationPermissionButton()
    }
}
