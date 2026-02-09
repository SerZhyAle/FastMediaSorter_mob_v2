package com.sza.fastmediasorter.ui.settings

import android.view.KeyEvent
import android.view.View
import androidx.activity.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivitySettingsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : BaseActivity<ActivitySettingsBinding>() {

    private val viewModel: SettingsViewModel by viewModels()
    
    override fun getViewBinding(): ActivitySettingsBinding {
        return ActivitySettingsBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        binding.backButton.setOnClickListener {
            finish()
        }
        
        val adapter = SettingsPagerAdapter(this)
        binding.viewPager.adapter = adapter
        
        // Disable animations between tabs (as per V2 Specification)
        // Use instant page transformer - no animation
        binding.viewPager.setPageTransformer { page, position ->
            page.translationX = 0f
            page.alpha = if (position == 0f) 1f else 0f
        }
        binding.viewPager.offscreenPageLimit = 1
        
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.settings_tab_general)
                1 -> getString(R.string.settings_tab_media)
                2 -> getString(R.string.settings_tab_playback)
                3 -> getString(R.string.settings_tab_destinations)
                else -> ""
            }
        }.attach()
    }

    override fun observeData() {
        // Settings are observed in individual fragments
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            // Previous tab
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_PAGE_UP -> {
                val currentPosition = binding.viewPager.currentItem
                if (currentPosition > 0) {
                    binding.viewPager.currentItem = currentPosition - 1
                }
                return true
            }
            
            // Next tab
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_PAGE_DOWN -> {
                val currentPosition = binding.viewPager.currentItem
                val adapter = binding.viewPager.adapter
                if (adapter != null && currentPosition < adapter.itemCount - 1) {
                    binding.viewPager.currentItem = currentPosition + 1
                }
                return true
            }
            
            // Exit settings
            KeyEvent.KEYCODE_ESCAPE -> {
                finish()
                return true
            }
            
            // Next UI element (TAB or Down arrow)
            KeyEvent.KEYCODE_TAB -> {
                if (event?.isShiftPressed == true) {
                    // Shift+TAB: previous element
                    val currentFocus = currentFocus
                    currentFocus?.focusSearch(View.FOCUS_UP)?.requestFocus()
                } else {
                    // TAB: next element
                    val currentFocus = currentFocus
                    currentFocus?.focusSearch(View.FOCUS_DOWN)?.requestFocus()
                }
                return true
            }
            
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                // Down arrow: next element
                val currentFocus = currentFocus
                val nextFocus = currentFocus?.focusSearch(View.FOCUS_DOWN)
                if (nextFocus != null && nextFocus != currentFocus) {
                    nextFocus.requestFocus()
                    return true
                }
            }
            
            KeyEvent.KEYCODE_DPAD_UP -> {
                // Up arrow: previous element
                val currentFocus = currentFocus
                val prevFocus = currentFocus?.focusSearch(View.FOCUS_UP)
                if (prevFocus != null && prevFocus != currentFocus) {
                    prevFocus.requestFocus()
                    return true
                }
            }
        }
        
        return super.onKeyDown(keyCode, event)
    }
}
