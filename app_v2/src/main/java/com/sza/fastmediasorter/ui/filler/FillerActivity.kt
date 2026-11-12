package com.sza.fastmediasorter.ui.filler

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityFillerBinding
import com.sza.fastmediasorter.ui.common.help.HelpToolbarEntryManager
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint

/** The storage filler program (S4521): view wiring only, the run lives in [FillerViewModel]. */
@AndroidEntryPoint
class FillerActivity : BaseActivity<ActivityFillerBinding>() {

    private val viewModel: FillerViewModel by viewModels()

    private lateinit var screen: FillerScreenManager

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.onFolderPicked(it.toString()) }
    }

    override fun getViewBinding(): ActivityFillerBinding = ActivityFillerBinding.inflate(layoutInflater)

    override fun setupViews() {
        binding.root.applySystemBarInsetPadding()
        binding.toolbar.setUpNavigation(this)
        HelpToolbarEntryManager.install(binding.toolbar, this, getHelpSurfaceId(), null)
        screen = FillerScreenManager(this, binding)
        binding.btnFillerChooseFolder.setOnClickListener { folderPicker.launch(null) }
        binding.btnFillerStart.setOnClickListener { screen.confirmStart(viewModel.state.value, viewModel::start) }
        binding.btnFillerCancel.setOnClickListener { viewModel.cancel() }
        binding.btnFillerRetryCleanup.setOnClickListener { viewModel.retryCleanup() }
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.state) { state -> screen.render(state) }
    }

    override fun getInitialFocusView(): View = binding.btnFillerStart

    override fun getMouseScrollTargetView(): View = binding.fillerScroll

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, FillerActivity::class.java)
    }
}
