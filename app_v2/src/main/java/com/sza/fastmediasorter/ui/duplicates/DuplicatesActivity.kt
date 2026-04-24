package com.sza.fastmediasorter.ui.duplicates

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityDuplicatesBinding
import com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment
import com.sza.fastmediasorter.ui.common.input.InputSurface
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DuplicatesActivity : AppCompatActivity() {

    companion object {
        /** ID ресурса, из которого было открыто Activity. -1L если не задан. */
        const val EXTRA_RESOURCE_ID = "extra_resource_id"
        /** true — после сканирования сразу удалить выбранные дубликаты без подтверждения FAB. */
        const val EXTRA_AUTO_DELETE = "extra_auto_delete"
    }

    private lateinit var binding: ActivityDuplicatesBinding

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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityDuplicatesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

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
}
