package com.sza.fastmediasorter.ui.settings

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import com.sza.fastmediasorter.core.orientation.isWideLayout
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.core.util.AnimationPolicy
import com.sza.fastmediasorter.databinding.ActivitySettingsBinding
import com.sza.fastmediasorter.ui.common.input.FocusDirection
import com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.ui.settings.fragments.BaseSettingsFragment
import com.sza.fastmediasorter.ui.settings.fragments.MediaSettingsFragment
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.utils.getStatusBarHeightSafe
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.max

@AndroidEntryPoint
class SettingsActivity : BaseActivity<ActivitySettingsBinding>() {

    // S1784: deliberately NOT a sensitive screen, reversing S1045.
    //
    // S1045 secured the whole window because the Authorization tab shows the default user and password in
    // plaintext. The owner overruled that on 2026-08-17: "ничего тут сенситив нет, а пароль по умолчанию
    // личная проблема пользователя". The cost was concrete and one-sided - FLAG_SECURE blanks every
    // screenshot of the settings screen, so neither a bug report nor a device-test artefact could ever
    // show it, which is why the UI evidence gate has a black-frame branch at all.
    //
    // The two screens that hold a resource's own credentials - adding a resource and editing one - keep
    // the flag, so `secureSensitiveScreens` still means what it says.

    // S0245: flavor-supplied extra Settings tabs (currently only the VR flavor adds an entry).
    @Inject lateinit var settingsTabExtensions: Set<@JvmSuppressWildcards SettingsTabExtension>

    // S0284: auto-indexed settings search registry (replaces the static SettingsSearchRegistry object).
    @Inject lateinit var settingsSearchRegistry: com.sza.fastmediasorter.ui.settings.search.SettingsSearchRegistry

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
        override fun showHelp() { InputHelpDialogFragment.show(supportFragmentManager, UiSurface.SETTINGS) }
        override fun activateFocused(): Boolean = activateFocusedViewOrAncestor()
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
        /** Intent extra: Boolean - open the Operations tab and expand the Scheduled section (S0353 widget deep-link). */
        const val EXTRA_OPEN_SCHEDULED = "extra_open_scheduled"

        /** S0780: Intent extra - String section id; the owning tab fragment expands that group after opening. */
        const val EXTRA_EXPAND_SECTION = "extra_expand_section"

        const val TAB_GENERAL = 0
        const val TAB_MEDIA = 1
        const val TAB_PLAYBACK = 2
        const val TAB_OPERATIONS = 3

        /** S0780: deep-link section ids consumed by the tab fragments' checkAndExpandSectionFromIntent. */
        const val SECTION_STREAMS = "streams"
        const val SECTION_ADDITIONAL_PROGRAMS = "additional_programs"

        /** S1883: the Wear OS group on the Operations tab - the settings target of the companion route. */
        const val SECTION_WEAR = "wear"
        private const val PREFS_NAME = "settings_state"
        private const val KEY_LAST_TAB_POSITION = "last_tab_position"

        internal fun resolveInitialTabPosition(
            intent: Intent,
            adapterItemCount: Int,
            lastTabPosition: Int,
            sourceResourceId: Long,
            initialTab: Int,
            enableScheduledOperations: Boolean,
        ): Int {
            val openScheduled = intent.getBooleanExtra(EXTRA_OPEN_SCHEDULED, false)
            return when {
                (sourceResourceId != -1L || openScheduled) && enableScheduledOperations -> 3
                initialTab in 0 until adapterItemCount -> initialTab
                lastTabPosition in 0 until adapterItemCount -> lastTabPosition
                else -> 0
            }
        }

        /** S0780: open Settings on the Media tab and expand the Streams group (streams-panel "Configure"). */
        fun openStreamsSectionIntent(context: Context): Intent =
            Intent(context, SettingsActivity::class.java).apply {
                putExtra(EXTRA_INITIAL_TAB, TAB_MEDIA)
                putExtra(EXTRA_EXPAND_SECTION, SECTION_STREAMS)
            }

        /** S0780: open Settings on the Management tab and expand the Additional Programs group. */
        fun openProgramsSectionIntent(context: Context): Intent =
            Intent(context, SettingsActivity::class.java).apply {
                putExtra(EXTRA_INITIAL_TAB, TAB_OPERATIONS)
                putExtra(EXTRA_EXPAND_SECTION, SECTION_ADDITIONAL_PROGRAMS)
            }

        /**
         * S1088: open Settings on the General tab, where the System-launcher enable toggle + launcher-settings
         * entry now live (the Operations "System launcher" group was removed).
         * S1107: onboarding sends the user here to complete the HOME-role request. The request itself no
         * longer travels in this intent - it is a durable flag on LauncherRoleManager, because an extra
         * consumed by one of the first-run screen's short-lived instances lost the user's opt-in.
         */
        fun openLauncherSectionIntent(context: Context): Intent =
            Intent(context, SettingsActivity::class.java).apply {
                putExtra(EXTRA_INITIAL_TAB, TAB_GENERAL)
            }

        fun openKeybindingRemap(context: Context) {
            context.startActivity(
                android.content.Intent(context, com.sza.fastmediasorter.ui.keybinding.KeybindingRemapActivity::class.java)
            )
        }
    }

    override fun shouldEnableEdgeToEdge(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Measure actionBarSize and register insets listener before the first frame
        // to prevent toolbarContainer height from jumping on activity open.
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)
        actionBarSizePx = TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
        // Apply insets handling whenever the system draws content edge-to-edge:
        // either we opted in explicitly, or Android 15+ (API 35) forces it for
        // targetSdk 35 regardless of shared edge-to-edge setup. Without this, the toolbar
        // title slides under the status bar on Android 15+ (observed on Samsung S25FE).
        if (shouldEnableEdgeToEdge() ||
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            applyEdgeToEdgeInsets()
        }
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

        if (AnimationPolicy.isAnimationAllowed) {
            binding.viewPager.setPageTransformer { page, position ->
                page.translationX = 0f
                page.alpha = if (position == 0f) 1f else 0f
            }
        } else {
            binding.viewPager.setPageTransformer(null)
            Timber.d("S2250: settings tab transformer disabled")
        }
        if (BuildConfig.DEBUG) Timber.d("SettingsActivity: [${elapsed()}ms] viewPager configured (transformer)")

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = getString(adapter.getTabTitleResId(position))
        }.attach()
        if (BuildConfig.DEBUG) Timber.d("SettingsActivity: [${elapsed()}ms] TabLayoutMediator attached")
        setupConnectedTabs(adapter)

        val sourceResourceId = intent.getLongExtra(EXTRA_SOURCE_RESOURCE_ID, -1L)
        val initialTab = intent.getIntExtra(EXTRA_INITIAL_TAB, -1)
        val lastTabPosition = getLastTabPosition()
        val initialPosition = resolveInitialTabPosition(
            intent = intent,
            adapterItemCount = adapter.itemCount,
            lastTabPosition = lastTabPosition,
            sourceResourceId = sourceResourceId,
            initialTab = initialTab,
            enableScheduledOperations = BuildConfig.ENABLE_SCHEDULED_OPERATIONS,
        )

        if (BuildConfig.DEBUG) {
            Timber.d("SettingsActivity: [${elapsed()}ms] initialPosition=$initialPosition lastTabPosition=$lastTabPosition")
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                saveLastTabPosition(position)
            }
        })

        binding.viewPager.setCurrentItem(initialPosition, false)
        saveLastTabPosition(initialPosition)

        setupGlobalSearch()
        if (BuildConfig.DEBUG) Timber.d("SettingsActivity: [${elapsed()}ms] setupGlobalSearch done")
        // Warm the lazy search index off the main thread so the first overlay open is instant;
        // the index is no longer built synchronously on the Settings open path.
        lifecycleScope.launch(Dispatchers.IO) { runCatching { settingsSearchRegistry.entries } }

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

    private fun setupConnectedTabs(adapter: SettingsPagerAdapter) {
        val useFixedTabs = binding.tabLayout.tabCount <= 4
        binding.tabLayout.tabMode = if (useFixedTabs) TabLayout.MODE_FIXED else TabLayout.MODE_SCROLLABLE
        binding.tabLayout.tabGravity = if (useFixedTabs) TabLayout.GRAVITY_FILL else TabLayout.GRAVITY_START

        for (position in 0 until binding.tabLayout.tabCount) {
            val tab = binding.tabLayout.getTabAt(position) ?: continue
            val title = getString(adapter.getTabTitleResId(position))
            tab.icon = null
            tab.contentDescription = title
            tab.customView = createConnectedTabView(title, position, binding.tabLayout.tabCount)
            tab.view.minimumWidth = 0
            tab.view.setPadding(0, 0, 0, 0)
        }

        if (useFixedTabs) {
            forceEqualTabWidths()
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = syncConnectedTabState(tab, true)
            override fun onTabUnselected(tab: TabLayout.Tab) = syncConnectedTabState(tab, false)
            override fun onTabReselected(tab: TabLayout.Tab) = syncConnectedTabState(tab, true)
        })

        // Capture tabLayout so the posted runnable never dereferences `binding`: on a launch/recreate
        // race the activity can be torn down before this frame runs, and `binding` then throws
        // IllegalStateException. The captured view stays valid to read; bail if already destroyed
        // since the recreated instance re-runs setupConnectedTabs.
        val tabLayout = binding.tabLayout
        tabLayout.post {
            if (isDestroyed) return@post
            for (position in 0 until tabLayout.tabCount) {
                tabLayout.getTabAt(position)?.let { tab ->
                    syncConnectedTabState(tab, position == tabLayout.selectedTabPosition)
                }
            }
        }
    }

    private fun createConnectedTabView(
        title: String,
        position: Int,
        tabCount: Int,
    ): TextView {
        val horizontalPadding = resources.getDimensionPixelSize(R.dimen.settings_tab_horizontal_padding)
        val verticalPadding = resources.getDimensionPixelSize(R.dimen.settings_tab_vertical_padding)
        return AppCompatTextView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            gravity = Gravity.CENTER
            text = title
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            minHeight = resources.getDimensionPixelSize(R.dimen.settings_tab_min_touch_height)
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            setTextAppearance(R.style.TextAppearance_FastMediaSorter_SettingsTabConnected)
            setTextColor(ResourcesCompat.getColorStateList(resources, R.color.settings_tab_text, theme))
            background = ResourcesCompat.getDrawable(resources, resolveConnectedTabBackground(position, tabCount), theme)
            isDuplicateParentStateEnabled = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    private fun resolveConnectedTabBackground(position: Int, tabCount: Int): Int = when {
        tabCount <= 1 -> R.drawable.bg_settings_tab_single
        position == 0 -> R.drawable.bg_settings_tab_start
        position == tabCount - 1 -> R.drawable.bg_settings_tab_end
        else -> R.drawable.bg_settings_tab_middle
    }

    private fun syncConnectedTabState(tab: TabLayout.Tab, selected: Boolean) {
        tab.customView?.isSelected = selected
        tab.customView?.refreshDrawableState()
    }

    private fun forceEqualTabWidths() {
        val tabStrip = binding.tabLayout.getChildAt(0) as? LinearLayout ?: return
        val isWide = resources.configuration.isWideLayout()
        val landscapeTabWidth = resources.getDimensionPixelSize(R.dimen.settings_tab_land_width)
        for (index in 0 until tabStrip.childCount) {
            val child = tabStrip.getChildAt(index)
            val params = child.layoutParams as? LinearLayout.LayoutParams ?: continue
            if (isWide) {
                params.width = landscapeTabWidth
                params.weight = 0f
            } else {
                params.width = 0
                params.weight = 1f
            }
            child.layoutParams = params
        }
        tabStrip.requestLayout()
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
        // S1619: in landscape the navigation bar - and on some devices the cutout - sits on a SIDE.
        // The toolbar spans the full width with the search button flush against the trailing edge, so
        // without a side inset the system owns those pixels and a tap on the button reaches its Back
        // instead. Padding the containers rather than the root keeps their backgrounds edge to edge.
        val sideBars = insets.getInsets(
            androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout()
        )

        // Toolbar container below status bar, clear of the side bars
        binding.toolbarContainer.setPadding(
            sideBars.left,
            statusBarInsetPx,
            sideBars.right,
            binding.toolbarContainer.paddingBottom
        )

        // ViewPager above nav bar only; adjustPan handles keyboard avoidance via window panning.
        // IME insets are unreliable on API<30 and cause content clipping when keyboard is hidden.
        binding.viewPager.setPadding(
            sideBars.left,
            binding.viewPager.paddingTop,
            sideBars.right,
            navBar.bottom
        )

        // The search overlay covers the pager, so it needs the same clearance - its own XML padding
        // plus the insets, or the close button lands under the side bar the same way.
        val overlayPadding = resources.getDimensionPixelSize(R.dimen.settings_padding)
        binding.searchOverlay.setPadding(
            overlayPadding + sideBars.left,
            overlayPadding,
            overlayPadding + sideBars.right,
            overlayPadding + navBar.bottom
        )

        // Re-apply compact toolbar height now that the inset is known
        if (resources.configuration.isWideLayout()) {
            updateLandscapeToolbarHeight()
        }
    }

    /** Last requested compact state, so inset updates can re-apply correctly. */
    private var toolbarCompact = false

    private fun applyCompactToolbar(compact: Boolean) {
        toolbarCompact = compact
        val compactH = resources.getDimensionPixelSize(R.dimen.toolbar_row_height_compact)
        val isWide = resources.configuration.isWideLayout()

        if (isWide) {
            updateLandscapeToolbarHeight()
        } else {
            val titleH = if (compact) compactH else resources.getDimensionPixelSize(R.dimen.settings_title_row_height)
            val tabH = if (compact) {
                resources.getDimensionPixelSize(R.dimen.settings_tabs_height_compact)
            } else {
                resources.getDimensionPixelSize(R.dimen.settings_tabs_height)
            }
            // S1693: titleRow exists only in the portrait layout, so the generated field is nullable.
            binding.titleRow?.let { titleRow ->
                titleRow.layoutParams.height = titleH
                titleRow.requestLayout()
            }
            binding.tabLayout.layoutParams.height = tabH
            binding.tabLayout.requestLayout()
        }
    }

    /**
     * In landscape the single toolbarContainer covers both the status-bar inset area (applied as
     * top padding in applyWindowInsets) and the visible toolbar content below it. The content row
     * must be a full action-bar height regardless of inset size - the previous formula derived it
     * as (actionBarSize - statusBarInset), which collapsed to ~0 on devices whose top inset is
     * close to the action-bar height (e.g. Pixel Fold unfolded), clipping the title/tabs row.
     * Compact mode reduces the CONTENT portion by 35%.
     * Called from both applyCompactToolbar and applyWindowInsets to keep heights in sync.
     */
    private fun updateLandscapeToolbarHeight() {
        val compactMinHeight = resources.getDimensionPixelSize(R.dimen.settings_tabs_height_compact)
        val contentH = if (toolbarCompact) max((actionBarSizePx * 0.65f).toInt(), compactMinHeight) else actionBarSizePx
        val totalH = contentH + statusBarInsetPx
        val lp = binding.toolbarContainer.layoutParams ?: return
        if (lp.height != totalH) {
            lp.height = totalH
            binding.toolbarContainer.layoutParams = lp
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyboardManager.handleKeyDown(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    /**
     * On TV/keyboard-only devices, hand initial focus to the active tab content rather than the
     * toolbar. ViewPager2 forwards the focus request to the currently-bound fragment's first
     * focusable child via native focus search (S0230 §11.3 - Settings per-screen audit).
     */
    override fun getInitialFocusView(): View? {
        return binding.viewPager
    }

    /** S0289 Phase 09: route mouse wheel onto the settings pager so each tab fragment scrolls naturally. */
    override fun getMouseScrollTargetView(): View? = binding.viewPager

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
                updateSearchResults(settingsSearchRegistry.search(query))
            }
        }

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
        updateSearchResults(settingsSearchRegistry.entries)
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

        binding.viewPager.post { navigateToTarget(item, retryCount = 0) }
    }

    /**
     * S1967: opens the collapsible section holding the row a search result points at.
     *
     * Called on every attempt of [navigateToTarget] rather than once before it, because only the
     * initially created tab has its fragment ready at the moment the tab is selected. Aimed at a
     * fragment that does not exist yet, the expansion does nothing and no one notices - which is
     * exactly how the first version of this fix passed on General and failed on Playback.
     */
    private fun expandSectionForTarget(item: SettingsSearchIndex) {
        val fragment = getSettingsFragment(item.destination.tabIndex)
        // Media's sections build their child fragment on first expand, so opening the container is
        // necessary but not sufficient there; this path attaches the child and is left as it was.
        if (fragment is MediaSettingsFragment) {
            fragment.ensureSectionExpanded(item.sectionId)
        }
        // Every destination, not only Media. The row's ancestors name the section that encloses it,
        // which a section name cannot do where one layout carries eight sections under one name.
        (fragment as? BaseSettingsFragment)?.expandSectionForSearchTarget(item.ancestorIds)
    }

    private fun navigateToTarget(item: SettingsSearchIndex, retryCount: Int) {
        expandSectionForTarget(item)
        val targetView = findViewById<View>(item.viewId)
        if (targetView == null) {
            if (retryCount < 25) {
                binding.viewPager.postDelayed(
                    { navigateToTarget(item, retryCount + 1) },
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
