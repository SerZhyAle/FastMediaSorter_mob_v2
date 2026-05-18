package com.sza.fastmediasorter.ui.settings

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.sza.fastmediasorter.BuildConfig
import androidx.activity.viewModels
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.utils.getStatusBarHeightSafe
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivitySettingsBinding
import com.sza.fastmediasorter.ui.common.input.FocusDirection
import com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment
import com.sza.fastmediasorter.ui.common.input.InputSurface
import com.sza.fastmediasorter.ui.settings.fragments.MediaSettingsFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class SettingsActivity : BaseActivity<ActivitySettingsBinding>() {

    // S0245: flavor-supplied extra Settings tabs (currently only the VR flavor adds an entry).
    @Inject lateinit var settingsTabExtensions: Set<@JvmSuppressWildcards SettingsTabExtension>

    private val viewModel: SettingsViewModel by viewModels()
    private val searchAdapter = SettingsSearchAdapter(::onSearchResultSelected)
    private var searchDebounceJob: Job? = null
    private val keyboardManager = SettingsKeyboardNavigationManager(object : SettingsKeyboardNavigationManager.Callback {
        override fun switchTab(delta: Int) {
            binding.viewPager.currentItem = (binding.viewPager.currentItem + delta)
                .coerceIn(0, (binding.viewPager.adapter?.itemCount ?: 1) - 1)
        }
        override fun tabCount(): Int = binding.viewPager.adapter?.itemCount ?: 0
        override fun currentTab(): Int = binding.viewPager.currentItem
        override fun openSearchOverlay() = this@SettingsActivity.openSearchOverlay()
        override fun closeSearchOverlay() = this@SettingsActivity.closeSearchOverlay()
        override fun isSearchVisible(): Boolean = binding.searchOverlay.isVisible
        override fun isTextEditorFocused(): Boolean =
            (currentFocus as? android.widget.TextView)?.onCheckIsTextEditor() == true
        override fun clearFocusedTextEditor(): Boolean {
            val focusedView = currentFocus ?: return false
            if ((focusedView as? android.widget.TextView)?.onCheckIsTextEditor() != true) return false

            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(focusedView.windowToken, 0)
            focusedView.clearFocus()
            return true
        }
        override fun navigateBack() { onBackPressedDispatcher.onBackPressed() }
        override fun showHelp() { InputHelpDialogFragment.show(supportFragmentManager, InputSurface.SETTINGS) }
        override fun activateFocused() { currentFocus?.performClick() }
        override fun moveFocus(direction: FocusDirection) {
            val focusDir = when (direction) {
                FocusDirection.UP, FocusDirection.PREVIOUS -> View.FOCUS_UP
                FocusDirection.DOWN, FocusDirection.NEXT -> View.FOCUS_DOWN
                FocusDirection.LEFT -> View.FOCUS_LEFT
                FocusDirection.RIGHT -> View.FOCUS_RIGHT
                FocusDirection.FIRST -> View.FOCUS_UP
                FocusDirection.LAST -> View.FOCUS_DOWN
            }
            currentFocus?.focusSearch(focusDir)?.requestFocus()
        }
    })
    private var setupStartUptimeMs: Long = 0L
    private var actionBarSizePx = 0
    private var statusBarInsetPx = 0
    // S0196 Phase 04: one-shot tag emitted after the first preferences page is laid out.
    private var firstPageRenderedLogged = false
    
    companion object {
        /** Intent extra: Long - pre-select this resource as source in the new scheduled operation dialog. */
        const val EXTRA_SOURCE_RESOURCE_ID = "extra_source_resource_id"
        /** Intent extra: Int - open Settings on this tab index (0=General, 1=Media, 2=Playback, 3=Destinations). */
        const val EXTRA_INITIAL_TAB = "extra_initial_tab"
        private const val PREFS_NAME = "settings_state"
        private const val KEY_LAST_TAB_POSITION = "last_tab_position"

        fun openKeybindingRemap(context: Context) {
            context.startActivity(
                android.content.Intent(context, com.sza.fastmediasorter.ui.keybinding.KeybindingRemapActivity::class.java)
            )
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Measure actionBarSize and register insets listener before the first frame
        // to prevent toolbarContainer height from jumping on activity open.
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)
        actionBarSizePx = TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
        applyEdgeToEdgeInsets()
    }

    override fun getViewBinding(): ActivitySettingsBinding {
        return ActivitySettingsBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        if (BuildConfig.DEBUG) setupStartUptimeMs = SystemClock.uptimeMillis()
        fun elapsed() = if (BuildConfig.DEBUG) SystemClock.uptimeMillis() - setupStartUptimeMs else 0L

        binding.backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        if (BuildConfig.DEBUG) Timber.d("SettingsActivity: [0ms] start setupViews")

        val adapter = SettingsPagerAdapter(this, settingsTabExtensions)
        if (BuildConfig.DEBUG) Timber.d("SettingsActivity: [${elapsed()}ms] SettingsPagerAdapter created (tabCount=${adapter.itemCount})")

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
            tab.text = getString(adapter.getTabTitleResId(position))
        }.attach()
        if (BuildConfig.DEBUG) Timber.d("SettingsActivity: [${elapsed()}ms] TabLayoutMediator attached")

        // If opened to create a new scheduled operation from Browse, jump to Operations tab
        val sourceResourceId = intent.getLongExtra(EXTRA_SOURCE_RESOURCE_ID, -1L)
        val initialTab = intent.getIntExtra(EXTRA_INITIAL_TAB, -1)
        when {
            sourceResourceId != -1L && BuildConfig.ENABLE_SCHEDULED_OPERATIONS -> {
                binding.viewPager.post { binding.viewPager.setCurrentItem(3, false) }
            }
            initialTab in 0 until adapter.itemCount -> {
                binding.viewPager.post { binding.viewPager.setCurrentItem(initialTab, false) }
            }
            else -> {
                // Restore last opened tab position
                val lastTabPosition = getLastTabPosition()
                if (BuildConfig.DEBUG) Timber.d("SettingsActivity: [${elapsed()}ms] lastTabPosition=$lastTabPosition read (disk)")
                if (lastTabPosition in 0 until (adapter.itemCount)) {
                    binding.viewPager.post {
                        binding.viewPager.setCurrentItem(lastTabPosition, false)
                    }
                }
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

        // S0196 Phase 04 measurement hook: posted AFTER the conditional setCurrentItem posts
        // above, so it fires once the initial tab fragment is laid out - "primary content
        // rendered" for the settings surface.
        binding.viewPager.post {
            if (!firstPageRenderedLogged) {
                firstPageRenderedLogged = true
                Timber.d("SettingsActivity: primaryContentBound tab=${binding.viewPager.currentItem}")
            }
        }

        if (BuildConfig.DEBUG) {
            binding.root.post {
                val startupDurationMs = SystemClock.uptimeMillis() - setupStartUptimeMs
                Timber.d("SettingsActivity ready in ${startupDurationMs}ms")
            }
        }
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.settings) { settings ->
            applyCompactToolbar(settings.useCompactElements)
        }
    }

    private fun applyEdgeToEdgeInsets() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            applyWindowInsets(insets)
            insets
        }
        // setupViews() runs inside post{} - the first insets dispatch has already happened.
        // Use getRootWindowInsets() to apply them immediately; fall back to requestApplyInsets
        // for the rare case where insets aren't cached yet.
        val current = androidx.core.view.ViewCompat.getRootWindowInsets(binding.root)
        if (current != null) {
            applyWindowInsets(current)
        } else {
            androidx.core.view.ViewCompat.requestApplyInsets(binding.root)
        }
    }

    private fun applyWindowInsets(insets: androidx.core.view.WindowInsetsCompat) {
        statusBarInsetPx = insets.getStatusBarHeightSafe(resources)
        val navBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())

        // Toolbar container below status bar
        binding.toolbarContainer.setPadding(
            binding.toolbarContainer.paddingLeft, statusBarInsetPx,
            binding.toolbarContainer.paddingRight, binding.toolbarContainer.paddingBottom
        )

        // ViewPager above nav bar only; adjustPan handles keyboard avoidance via window panning.
        // IME insets are unreliable on API<30 and cause content clipping when keyboard is hidden.
        binding.viewPager.setPadding(
            binding.viewPager.paddingLeft, binding.viewPager.paddingTop,
            binding.viewPager.paddingRight, navBar.bottom
        )

        // Re-apply compact toolbar height now that the inset is known
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            updateLandscapeToolbarHeight()
        }
    }

    /** Last requested compact state, so inset updates can re-apply correctly. */
    private var toolbarCompact = false

    private fun applyCompactToolbar(compact: Boolean) {
        toolbarCompact = compact
        val compactH = resources.getDimensionPixelSize(R.dimen.toolbar_row_height_compact)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            updateLandscapeToolbarHeight()
        } else {
            val titleH = if (compact) compactH else resources.getDimensionPixelSize(R.dimen.settings_title_row_height)
            val tabH = if (compact) compactH else resources.getDimensionPixelSize(R.dimen.settings_tabs_height)
            binding.root.findViewById<View>(R.id.titleRow)?.let { titleRow ->
                titleRow.layoutParams.height = titleH
                titleRow.requestLayout()
            }
            binding.tabLayout.layoutParams.height = tabH
            binding.tabLayout.requestLayout()
        }
    }

    /**
     * In landscape the single toolbarContainer covers both the status-bar inset area and
     * the visible toolbar content. Compact mode reduces the CONTENT portion by 35%.
     * Called from both applyCompactToolbar and applyWindowInsets to keep heights in sync.
     */
    private fun updateLandscapeToolbarHeight() {
        val originalContentH = (actionBarSizePx - statusBarInsetPx).coerceAtLeast(0)
        val contentH = if (toolbarCompact) (originalContentH * 0.65f).toInt() else originalContentH
        val totalH = contentH + statusBarInsetPx
        val lp = binding.toolbarContainer.layoutParams ?: return
        if (lp.height != totalH) {
            lp.height = totalH
            binding.toolbarContainer.layoutParams = lp
        }
    }

    override fun onLayoutConfigurationChanged(newConfig: android.content.res.Configuration) {
        if (binding.searchOverlay.isVisible) {
            closeSearchOverlay()
        }
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyboardManager.handleKeyDown(keyCode, event)) return true
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
                // If the back stack is empty (e.g. process was restarted while Settings was open),
                // navigate up to MainActivity explicitly instead of going to home screen.
                if (isTaskRoot) {
                    startActivity(
                        android.content.Intent(this@SettingsActivity, com.sza.fastmediasorter.ui.main.MainActivity::class.java)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                    finish()
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
