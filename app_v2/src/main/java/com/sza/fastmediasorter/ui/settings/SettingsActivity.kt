package com.sza.fastmediasorter.ui.settings

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import com.sza.fastmediasorter.BuildConfig
import androidx.activity.viewModels
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivitySettingsBinding
import com.sza.fastmediasorter.ui.settings.fragments.MediaSettingsFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class SettingsActivity : BaseActivity<ActivitySettingsBinding>() {

    private val viewModel: SettingsViewModel by viewModels()
    private val searchAdapter = SettingsSearchAdapter(::onSearchResultSelected)
    private var searchDebounceJob: Job? = null
    private var setupStartUptimeMs: Long = 0L
    
    companion object {
        private const val PREFS_NAME = "settings_state"
        private const val KEY_LAST_TAB_POSITION = "last_tab_position"
    }
    
    override fun getViewBinding(): ActivitySettingsBinding {
        return ActivitySettingsBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        setupStartUptimeMs = SystemClock.uptimeMillis()
        fun elapsed() = SystemClock.uptimeMillis() - setupStartUptimeMs

        binding.backButton.setOnClickListener {
            finish()
        }

        if (BuildConfig.DEBUG) Timber.d("SettingsActivity: [0ms] start setupViews")

        val adapter = SettingsPagerAdapter(this)
        if (BuildConfig.DEBUG) Timber.d("SettingsActivity: [${elapsed()}ms] SettingsPagerAdapter created")

        binding.viewPager.adapter = adapter
        if (BuildConfig.DEBUG) Timber.d("SettingsActivity: [${elapsed()}ms] viewPager.adapter set")

        // Disable animations between tabs (as per V2 Specification)
        // Use instant page transformer - no animation
        binding.viewPager.setPageTransformer { page, position ->
            page.translationX = 0f
            page.alpha = if (position == 0f) 1f else 0f
        }
        binding.viewPager.offscreenPageLimit = 1
        if (BuildConfig.DEBUG) Timber.d("SettingsActivity: [${elapsed()}ms] viewPager configured (transformer + offscreenLimit)")

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.settings_tab_general)
                1 -> getString(R.string.settings_tab_media)
                2 -> getString(R.string.settings_tab_playback)
                3 -> getString(R.string.settings_tab_destinations)
                else -> ""
            }
        }.attach()
        if (BuildConfig.DEBUG) Timber.d("SettingsActivity: [${elapsed()}ms] TabLayoutMediator attached")

        // Restore last opened tab position
        val lastTabPosition = getLastTabPosition()
        if (BuildConfig.DEBUG) Timber.d("SettingsActivity: [${elapsed()}ms] lastTabPosition=$lastTabPosition read (disk)")
        if (lastTabPosition in 0 until (adapter.itemCount)) {
            binding.viewPager.post {
                binding.viewPager.setCurrentItem(lastTabPosition, false)
            }
        }

        // Save tab position when changed
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                saveLastTabPosition(position)
            }
        })

        setupGlobalSearch()
        if (BuildConfig.DEBUG) Timber.d("SettingsActivity: [${elapsed()}ms] setupGlobalSearch done (${SettingsSearchRegistry.entries.size} search entries)")

        binding.root.post {
            val startupDurationMs = SystemClock.uptimeMillis() - setupStartUptimeMs
            Timber.i("SettingsActivity ready in ${startupDurationMs}ms")
        }
    }

    override fun observeData() {
        // Settings are observed in individual fragments
    }

    override fun onLayoutConfigurationChanged(newConfig: android.content.res.Configuration) {
        if (binding.searchOverlay.isVisible) {
            closeSearchOverlay()
        }
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
                if (binding.searchOverlay.isVisible) {
                    closeSearchOverlay()
                    return true
                }
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

    private fun setupGlobalSearch() {
        binding.searchResultsRecycler.layoutManager = LinearLayoutManager(this)
        binding.searchResultsRecycler.adapter = searchAdapter

        binding.searchButton.setOnClickListener {
            openSearchOverlay()
        }

        binding.searchCloseButton.setOnClickListener {
            closeSearchOverlay()
        }

        binding.searchInput.doAfterTextChanged { editable ->
            val query = editable?.toString().orEmpty()
            searchDebounceJob?.cancel()
            searchDebounceJob = lifecycleScope.launch {
                delay(250)
                updateSearchResults(SettingsSearchRegistry.search(query))
            }
        }

        updateSearchResults(SettingsSearchRegistry.entries)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.searchOverlay.isVisible) {
                    closeSearchOverlay()
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    private fun openSearchOverlay() {
        binding.searchOverlay.isVisible = true
        if (binding.searchInput.text?.isNotEmpty() == true) {
            binding.searchInput.setText("")
        }
        updateSearchResults(SettingsSearchRegistry.entries)
        binding.searchInput.requestFocus()
    }

    private fun closeSearchOverlay() {
        searchDebounceJob?.cancel()
        binding.searchOverlay.isVisible = false
        binding.searchInput.clearFocus()
    }

    private fun updateSearchResults(results: List<SettingsSearchIndex>) {
        searchAdapter.submitList(results)
        binding.searchEmptyText.isVisible = results.isEmpty()
    }

    private fun onSearchResultSelected(item: SettingsSearchIndex) {
        closeSearchOverlay()
        binding.viewPager.currentItem = item.destination.tabIndex

        binding.viewPager.post {
            if (item.destination == SettingsSearchDestination.MEDIA) {
                (getSettingsFragment(item.destination.tabIndex) as? MediaSettingsFragment)
                    ?.ensureSectionExpanded(item.sectionId)
            }
            navigateToTarget(item.viewId, retryCount = 0)
        }
    }

    private fun navigateToTarget(viewId: Int, retryCount: Int) {
        val targetView = findViewById<View>(viewId)
        if (targetView == null) {
            if (retryCount < 25) {
                binding.viewPager.postDelayed(
                    { navigateToTarget(viewId, retryCount + 1) },
                    80L
                )
            }
            return
        }

        targetView.post {
            targetView.requestFocus()
            val rect = Rect()
            targetView.getDrawingRect(rect)
            targetView.requestRectangleOnScreen(rect, true)
            highlightView(targetView)
        }
    }

    private fun highlightView(view: View) {
        ValueAnimator.ofFloat(1f, 0.4f, 1f).apply {
            duration = 700L
            repeatCount = 1
            addUpdateListener { animator ->
                view.alpha = animator.animatedValue as Float
            }
            start()
        }
    }

    private fun getSettingsFragment(position: Int): androidx.fragment.app.Fragment? {
        return supportFragmentManager.findFragmentByTag("f$position")
    }
    
    /**
     * Get last opened tab position from SharedPreferences.
     * Wrapped in StrictModeHelper to avoid violations.
     */
    private fun getLastTabPosition(): Int {
        return StrictModeHelper.allowDiskReads {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_LAST_TAB_POSITION, 0) // Default to first tab (General)
        }
    }
    
    /**
     * Save current tab position to SharedPreferences.
     * Wrapped in StrictModeHelper to avoid violations.
     */
    private fun saveLastTabPosition(position: Int) {
        StrictModeHelper.allowDiskWrites {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_LAST_TAB_POSITION, position)
                .apply()
        }
    }
}
