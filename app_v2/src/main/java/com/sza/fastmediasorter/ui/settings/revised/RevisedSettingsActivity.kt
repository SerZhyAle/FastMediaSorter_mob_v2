package com.sza.fastmediasorter.ui.settings.revised

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayoutMediator
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivitySettingsRevisedBinding
import com.sza.fastmediasorter.ui.common.input.FocusDirection
import com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment
import com.sza.fastmediasorter.ui.common.input.InputSurface
import com.sza.fastmediasorter.ui.settings.revised.fragments.RevisedGeneralSettingsFragment
import com.sza.fastmediasorter.ui.settings.revised.fragments.RevisedMediaSettingsFragment
import com.sza.fastmediasorter.ui.settings.revised.fragments.RevisedOperationsSettingsFragment
import com.sza.fastmediasorter.ui.settings.revised.fragments.RevisedPlaybackSettingsFragment
import com.sza.fastmediasorter.utils.getStatusBarHeightSafe
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RevisedSettingsActivity : BaseActivity<ActivitySettingsRevisedBinding>() {

    private val searchAdapter = RevisedSettingsSearchAdapter(::onSearchResultSelected)
    private var searchDebounceJob: Job? = null
    private val keyboardManager = RevisedSettingsKeyboardNavigationManager(
        object : RevisedSettingsKeyboardNavigationManager.Callback {
            override fun switchTab(delta: Int) {
                binding.viewPager.currentItem = (binding.viewPager.currentItem + delta)
                    .coerceIn(0, (binding.viewPager.adapter?.itemCount ?: 1) - 1)
            }

            override fun tabCount(): Int = binding.viewPager.adapter?.itemCount ?: 0

            override fun currentTab(): Int = binding.viewPager.currentItem

            override fun openSearchOverlay() = this@RevisedSettingsActivity.openSearchOverlay()

            override fun closeSearchOverlay() = this@RevisedSettingsActivity.closeSearchOverlay()

            override fun isSearchVisible(): Boolean = binding.searchOverlay.isVisible

            override fun isTextEditorFocused(): Boolean {
                return (currentFocus as? android.widget.TextView)?.onCheckIsTextEditor() == true
            }

            override fun clearFocusedTextEditor(): Boolean {
                val focusedView = currentFocus ?: return false
                if ((focusedView as? android.widget.TextView)?.onCheckIsTextEditor() != true) {
                    return false
                }

                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(focusedView.windowToken, 0)
                focusedView.clearFocus()
                return true
            }

            override fun navigateBack() {
                onBackPressedDispatcher.onBackPressed()
            }

            override fun showHelp() {
                InputHelpDialogFragment.show(supportFragmentManager, InputSurface.SETTINGS)
            }

            override fun activateFocused() {
                currentFocus?.performClick()
            }

            override fun moveFocus(direction: FocusDirection) {
                val focusDirection = when (direction) {
                    FocusDirection.UP, FocusDirection.PREVIOUS -> View.FOCUS_UP
                    FocusDirection.DOWN, FocusDirection.NEXT -> View.FOCUS_DOWN
                    FocusDirection.LEFT -> View.FOCUS_LEFT
                    FocusDirection.RIGHT -> View.FOCUS_RIGHT
                    FocusDirection.FIRST -> View.FOCUS_UP
                    FocusDirection.LAST -> View.FOCUS_DOWN
                }
                currentFocus?.focusSearch(focusDirection)?.requestFocus()
            }
        },
    )

    companion object {
        const val EXTRA_INITIAL_TAB = "extra_revised_initial_tab"

        fun createIntent(context: Context, initialTab: Int = 0): Intent {
            return Intent(context, RevisedSettingsActivity::class.java)
                .putExtra(EXTRA_INITIAL_TAB, initialTab)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyEdgeToEdgeInsets()
    }

    override fun getViewBinding(): ActivitySettingsRevisedBinding {
        return ActivitySettingsRevisedBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        binding.backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val adapter = RevisedSettingsPagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.setPageTransformer { page, position ->
            page.translationX = 0f
            page.alpha = if (position == 0f) 1f else 0f
        }
        binding.viewPager.offscreenPageLimit = 1

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = getString(adapter.getTabTitleResId(position))
        }.attach()

        val initialTab = intent.getIntExtra(EXTRA_INITIAL_TAB, 0)
        if (initialTab in 0 until adapter.itemCount) {
            binding.viewPager.post { binding.viewPager.setCurrentItem(initialTab, false) }
        }

        setupGlobalSearch()
    }

    override fun observeData() = Unit

    override fun onLayoutConfigurationChanged(newConfig: android.content.res.Configuration) {
        if (binding.searchOverlay.isVisible) {
            closeSearchOverlay()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyboardManager.handleKeyDown(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            applyWindowInsets(insets)
            insets
        }

        val current = ViewCompat.getRootWindowInsets(binding.root)
        if (current != null) {
            applyWindowInsets(current)
        } else {
            ViewCompat.requestApplyInsets(binding.root)
        }
    }

    private fun applyWindowInsets(insets: WindowInsetsCompat) {
        val statusBarInsetPx = insets.getStatusBarHeightSafe(resources)
        val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

        binding.toolbarContainer.setPadding(
            binding.toolbarContainer.paddingLeft,
            statusBarInsetPx,
            binding.toolbarContainer.paddingRight,
            binding.toolbarContainer.paddingBottom,
        )

        binding.viewPager.setPadding(
            binding.viewPager.paddingLeft,
            binding.viewPager.paddingTop,
            binding.viewPager.paddingRight,
            navBarInsets.bottom,
        )

        binding.searchOverlay.setPadding(
            binding.searchOverlay.paddingLeft,
            binding.searchOverlay.paddingTop,
            binding.searchOverlay.paddingRight,
            navBarInsets.bottom,
        )
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
                updateSearchResults(RevisedSettingsSearchRegistry.search(query))
            }
        }

        updateSearchResults(RevisedSettingsSearchRegistry.entries)
    }

    private fun openSearchOverlay() {
        binding.searchOverlay.isVisible = true
        if (binding.searchInput.text?.isNotEmpty() == true) {
            binding.searchInput.setText("")
        }
        updateSearchResults(RevisedSettingsSearchRegistry.entries)
        binding.searchInput.requestFocus()
    }

    private fun closeSearchOverlay() {
        searchDebounceJob?.cancel()
        binding.searchOverlay.isVisible = false
        binding.searchInput.clearFocus()
    }

    private fun updateSearchResults(results: List<RevisedSettingsSearchIndex>) {
        searchAdapter.submitList(results)
        binding.searchEmptyText.isVisible = results.isEmpty()
    }

    private fun onSearchResultSelected(item: RevisedSettingsSearchIndex) {
        closeSearchOverlay()
        binding.viewPager.currentItem = item.destination.tabIndex
        binding.viewPager.post {
            revealSectionAndNavigate(item, retryCount = 0)
        }
    }

    private fun revealSectionAndNavigate(item: RevisedSettingsSearchIndex, retryCount: Int) {
        val revealed = when (item.destination) {
            RevisedSettingsSearchDestination.GENERAL -> {
                (getRevisedSettingsFragment(item.destination.tabIndex) as? RevisedGeneralSettingsFragment)
                    ?.also { it.ensureSectionExpanded(item.sectionId) } != null
            }

            RevisedSettingsSearchDestination.MEDIA -> {
                (getRevisedSettingsFragment(item.destination.tabIndex) as? RevisedMediaSettingsFragment)
                    ?.also { it.ensureSectionExpanded(item.sectionId) } != null
            }

            RevisedSettingsSearchDestination.PLAYBACK -> {
                (getRevisedSettingsFragment(item.destination.tabIndex) as? RevisedPlaybackSettingsFragment)
                    ?.also { it.ensureSectionExpanded(item.sectionId) } != null
            }

            RevisedSettingsSearchDestination.OPERATIONS -> {
                (getRevisedSettingsFragment(item.destination.tabIndex) as? RevisedOperationsSettingsFragment)
                    ?.also { it.ensureSectionExpanded(item.sectionId) } != null
            }
        }

        if (!revealed && retryCount < 25) {
            binding.viewPager.postDelayed(
                { revealSectionAndNavigate(item, retryCount + 1) },
                80L,
            )
            return
        }

        navigateToTarget(item.viewId, retryCount = 0)
    }

    private fun navigateToTarget(viewId: Int, retryCount: Int) {
        val targetView = findViewById<View>(viewId)
        if (targetView == null) {
            if (retryCount < 25) {
                binding.viewPager.postDelayed({ navigateToTarget(viewId, retryCount + 1) }, 80L)
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
        android.animation.ValueAnimator.ofFloat(1f, 0.4f, 1f).apply {
            duration = 700L
            repeatCount = 1
            addUpdateListener { animator ->
                view.alpha = animator.animatedValue as Float
            }
            start()
        }
    }

    private fun getRevisedSettingsFragment(position: Int): androidx.fragment.app.Fragment? {
        return supportFragmentManager.findFragmentByTag("f$position")
    }
}