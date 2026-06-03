package com.sza.fastmediasorter.ui.duplicates

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.core.view.children
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityDuplicatesBinding
import com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment
import com.sza.fastmediasorter.ui.common.input.InputSurface
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class DuplicatesActivity : BaseActivity<ActivityDuplicatesBinding>() {

    companion object {
        const val EXTRA_RESOURCE_ID = "extra_resource_id"
        const val EXTRA_AUTO_DELETE = "extra_auto_delete"
    }

    override fun getViewBinding(): ActivityDuplicatesBinding =
        ActivityDuplicatesBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val fragment = DuplicatesFragment().apply {
                arguments = Bundle().apply {
                    putLong(EXTRA_RESOURCE_ID, intent.getLongExtra(EXTRA_RESOURCE_ID, -1L))
                    putBoolean(EXTRA_AUTO_DELETE, intent.getBooleanExtra(EXTRA_AUTO_DELETE, false))
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
        }
    }

    override fun setupViews() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    override fun observeData() {}

    override fun getInitialFocusView(): View? {
        Timber.d("S0289: duplicates initial-focus / delete-FAB focus chain")
        val duplicatesList = binding.fragmentContainer.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvDuplicates)
        val firstDuplicateItem = duplicatesList?.findViewHolderForAdapterPosition(0)?.itemView
        val startScanButton = binding.fragmentContainer.findViewById<View>(R.id.btnStartScan)
        val cancelScanButton = binding.fragmentContainer.findViewById<View>(R.id.btnCancelScan)
        return firstDuplicateItem
            ?: startScanButton
            ?: cancelScanButton
            ?: binding.toolbar.children.firstOrNull { it.isClickable }
            ?: duplicatesList
            ?: binding.toolbar
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_ESCAPE -> { finish(); return true }
            KeyEvent.KEYCODE_F1 -> {
                InputHelpDialogFragment.show(supportFragmentManager, InputSurface.DUPLICATES)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
