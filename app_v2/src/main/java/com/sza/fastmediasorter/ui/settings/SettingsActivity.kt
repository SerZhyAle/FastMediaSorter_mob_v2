package com.sza.fastmediasorter.ui.settings

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
        binding.toolbar.setNavigationOnClickListener {
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
}
