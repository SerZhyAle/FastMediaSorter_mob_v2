package com.sza.fastmediasorter.ui.resourceeditor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityResourceEditorBinding
import com.sza.fastmediasorter.domain.model.ResourceEditorMode
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment
import com.sza.fastmediasorter.ui.common.input.InputSurface
import com.sza.fastmediasorter.utils.getStatusBarHeightSafe
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class ResourceEditorActivity : BaseActivity<ActivityResourceEditorBinding>() {

    override fun getViewBinding(): ActivityResourceEditorBinding {
        return ActivityResourceEditorBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        // Apply edge-to-edge insets: fragment toolbar below status bar, content above nav bar
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentContainer) { view, insets ->
            val navBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            view.setPadding(0, insets.getStatusBarHeightSafe(resources), 0, navBar.bottom)
            insets
        }
        // setupViews() runs inside post{} — initial insets dispatch was already missed.
        // Force a re-dispatch so the listener fires and padding is applied correctly.
        androidx.core.view.ViewCompat.requestApplyInsets(binding.fragmentContainer)
    }

    override fun observeData() {
        // Fragment manages its own data
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val textEditorFocused =
            (currentFocus as? android.widget.TextView)?.onCheckIsTextEditor() == true
        when {
            keyCode == KeyEvent.KEYCODE_F1 -> {
                InputHelpDialogFragment.show(supportFragmentManager, InputSurface.RESOURCE_EDITOR)
                return true
            }
            keyCode == KeyEvent.KEYCODE_S && event?.isCtrlPressed == true -> {
                val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                if (fragment is ResourceEditorFragment) fragment.performSave()
                return true
            }
            keyCode == KeyEvent.KEYCODE_ESCAPE && !textEditorFocused -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val mode = ResourceEditorMode.valueOf(
                intent.getStringExtra(EXTRA_MODE) ?: ResourceEditorMode.CREATE.name
            )
            val resourceId = intent.getLongExtra(EXTRA_RESOURCE_ID, -1L).takeIf { it != -1L }
            val resourceType = intent.getStringExtra(EXTRA_RESOURCE_TYPE)?.let { 
                ResourceType.valueOf(it) 
            }

            val fragment = ResourceEditorFragment.newInstance(mode, resourceType, resourceId)
            
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
        }
    }

    companion object {
        private const val EXTRA_MODE = "extra_mode"
        private const val EXTRA_RESOURCE_ID = "extra_resource_id"
        private const val EXTRA_RESOURCE_TYPE = "extra_resource_type"

        fun createAddIntent(context: Context, resourceType: ResourceType): Intent {
            return Intent(context, ResourceEditorActivity::class.java).apply {
                putExtra(EXTRA_MODE, ResourceEditorMode.CREATE.name)
                putExtra(EXTRA_RESOURCE_TYPE, resourceType.name)
            }
        }

        fun createEditIntent(context: Context, resourceId: Long): Intent {
            return Intent(context, ResourceEditorActivity::class.java).apply {
                putExtra(EXTRA_MODE, ResourceEditorMode.EDIT.name)
                putExtra(EXTRA_RESOURCE_ID, resourceId)
            }
        }

        fun createCopyIntent(context: Context, resourceId: Long): Intent {
            return Intent(context, ResourceEditorActivity::class.java).apply {
                putExtra(EXTRA_MODE, ResourceEditorMode.COPY.name)
                putExtra(EXTRA_RESOURCE_ID, resourceId)
            }
        }
    }
}
