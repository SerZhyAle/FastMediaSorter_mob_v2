package com.sza.fastmediasorter.ui.settings

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.orientation.isWideLayout
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.core.util.AnimationPolicy
import com.sza.fastmediasorter.core.util.XrDeviceProbe
import com.sza.fastmediasorter.databinding.ActivitySettingsBinding
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.ui.common.help.HelpOpenManager
import com.sza.fastmediasorter.ui.common.help.HelpTooltipManager
import com.sza.fastmediasorter.ui.common.input.FocusDirection
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.ui.scheduledops.ScheduledOperationsActivity
import com.sza.fastmediasorter.ui.settings.fragments.BaseSettingsFragment
import com.sza.fastmediasorter.ui.settings.fragments.MediaSettingsFragment
import com.sza.fastmediasorter.ui.settings.fragments.ToolsSettingsFragment
import com.sza.fastmediasorter.ui.settings.helpers.SettingsNavigationRailManager
import com.sza.fastmediasorter.ui.settings.helpers.SettingsPageStateStore
import com.sza.fastmediasorter.ui.settings.helpers.SettingsPageTimingRecorder
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

    // S4622: decides whether the Launcher page is listed - the same gate as the General page's launcher rows.
    @Inject lateinit var launcherModeContract: LauncherModeContract

    // S0284: auto-indexed settings search registry (replaces the static SettingsSearchRegistry object).
    @Inject lateinit var settingsSearchRegistry: com.sza.fastmediasorter.ui.settings.search.SettingsSearchRegistry

    private val viewModel: SettingsViewModel by viewModels()
    private val searchAdapter = SettingsSearchAdapter(::onSearchResultSelected)
    private var searchDebounceJob: Job? = null

    // S4621: set in setupViews; nullable because setupViews never runs for the EXTRA_OPEN_SCHEDULED redirect.
    private var pagerAdapter: SettingsPagerAdapter? = null
    private var railManager: SettingsNavigationRailManager? = null
    private var pendingSavedState: Bundle? = null
    private var pageStateStore: SettingsPageStateStore? = null
    private val pageTimingRecorder = SettingsPageTimingRecorder()
    private val keyboardManager =
        SettingsKeyboardNavigationManager(object : SettingsKeyboardNavigationManager.Callback {
            override fun switchTab(delta: Int) {
                selectPosition(
                    (binding.viewPager.currentItem + delta)
                        .coerceIn(0, (binding.viewPager.adapter?.itemCount ?: 1) - 1),
                )
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
            override fun showHelp() { openTabHelp() }
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
        /** Intent extra: Int - open Settings on this tab index (0=General, 1=Media, 2=Playback, 3=Destinations). */
        const val EXTRA_INITIAL_TAB = "extra_initial_tab"

        /** S4621: Intent extra - String [SettingsDestination] id; wins over [EXTRA_INITIAL_TAB] when both are set. */
        const val EXTRA_INITIAL_DESTINATION = "extra_initial_destination"

        /**
         * Intent extra: Boolean - legacy deep-link that used to open the Operations tab with the
         * Scheduled section expanded. Since S3365 [onCreate] redirects it to the program screen;
         * the constant stays because pre-update widgets hold the wire value in their intents.
         */
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

        /** S4622: the blocks of the Tools page that a host's settings command opens. */
        const val SECTION_TRANSLATION = "translation"
        const val SECTION_CALCULATOR = "calculator"
        const val SECTION_STOPWATCH = "stopwatch"

        /** S4622: open Settings on the Launcher page - the launcher's own settings commands use it. */
        fun openLauncherPageIntent(context: Context): Intent =
            Intent(context, SettingsActivity::class.java)
                .putExtra(EXTRA_INITIAL_DESTINATION, SettingsDestination.LAUNCHER)

        /**
         * S4622: open Settings on the Tools page with [sectionId] expanded - the one route a host screen
         * uses for its settings command, so no host keeps an editable copy (SETTINGS-NAVIGATION 2.1 item A).
         */
        fun openToolsSectionIntent(context: Context, sectionId: String): Intent =
            Intent(context, SettingsActivity::class.java).apply {
                Timber.d("S4622: host command opens Tools section=$sectionId")
                putExtra(EXTRA_INITIAL_DESTINATION, SettingsDestination.TOOLS)
                putExtra(EXTRA_EXPAND_SECTION, sectionId)
            }

        internal fun resolveInitialTabPosition(
            adapterItemCount: Int,
            lastTabPosition: Int,
            initialTab: Int,
        ): Int {
            return when {
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
                android.content.Intent(
                    context,
                    com.sza.fastmediasorter.ui.keybinding.KeybindingRemapActivity::class.java
                )
            )
        }
    }

    override fun shouldEnableEdgeToEdge(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // S3365: deep-links held by pre-update widgets and shortcuts carried EXTRA_OPEN_SCHEDULED at
        // the settings activity; the scheduled section is gone, so forward them to the program screen
        // and finish. The extra is removed so a re-delivered intent does not loop the redirect.
        if (intent.getBooleanExtra(EXTRA_OPEN_SCHEDULED, false)) {
            intent.removeExtra(EXTRA_OPEN_SCHEDULED)
            startActivity(android.content.Intent(this, ScheduledOperationsActivity::class.java))
            finish()
            super.onCreate(savedInstanceState)
            return
        }
        pendingSavedState = savedInstanceState
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
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) {
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
        val showLauncher = launcherModeContract.isAvailableInBuild && !XrDeviceProbe.isXrDevice(this)
        val adapter = SettingsPagerAdapter(this, settingsTabExtensions, showLauncher)
        if (BuildConfig.DEBUG) {
            Timber.d(
                "SettingsActivity: [${elapsed()}ms] SettingsPagerAdapter created (tabCount=${adapter.itemCount})"
            )
        }

        binding.viewPager.adapter = adapter
        if (BuildConfig.DEBUG) Timber.d("SettingsActivity: [${elapsed()}ms] viewPager.adapter set")

        if (AnimationPolicy.isAnimationAllowed) {
            binding.viewPager.setPageTransformer { page, position ->
                page.translationX = 0f
                page.alpha = if (position == 0f) 1f else 0f
            }
        } else {
            binding.viewPager.setPageTransformer(null)
        }
        if (BuildConfig.DEBUG) Timber.d("SettingsActivity: [${elapsed()}ms] viewPager configured (transformer)")

        // S4621: the page menu is the one way to change page. A swipe would change the destination
        // behind the narrow list, which would then name a page other than the one the user left.
        binding.viewPager.isUserInputEnabled = false
        pagerAdapter = adapter
        val navigation = SettingsNavigationRailManager(
            rail = binding.settingsRail,
            contentPane = binding.viewPager,
            destinations = adapter.destinations,
            onDestinationSelected = { destination -> selectDestination(destination.id) },
            onPaneChanged = ::onNavigationPaneChanged,
        )
        railManager = navigation
        navigation.bind()

        val initialTab = intent.getStringExtra(EXTRA_INITIAL_DESTINATION)?.let(adapter::indexOf)
            ?.takeIf { it >= 0 }
            ?: intent.getIntExtra(EXTRA_INITIAL_TAB, -1)
        navigation.restoreState(pendingSavedState, hasNamedDestination = initialTab >= 0)
        pendingSavedState = null
        val store = SettingsPageStateStore(this)
        pageStateStore = store
        val lastTabPosition = store.lastDestinationId { adapter.destinationAt(it)?.id }
            ?.let(adapter::indexOf) ?: -1
        val initialPosition = resolveInitialTabPosition(
            adapterItemCount = adapter.itemCount,
            lastTabPosition = lastTabPosition,
            initialTab = initialTab,
        )

        if (BuildConfig.DEBUG) {
            Timber.d(
                "SettingsActivity: [${elapsed()}ms] initialPosition=$initialPosition lastTabPosition=$lastTabPosition"
            )
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                adapter.destinationAt(position)?.let {
                    store.saveLastDestinationId(it.id)
                    navigation.markSelected(it.id)
                    pageTimingRecorder.onActivated(it.id, binding.viewPager) { getSettingsFragment(position)?.view }
                }
                onNavigationPaneChanged()
                // S4394: posted - the newly selected page's view may not exist yet, and the
                // refresh resolves that page's fragment by tag.
                binding.viewPager.post { refreshCollapseAllButton() }
            }
        })

        binding.viewPager.setCurrentItem(initialPosition, false)
        adapter.destinationAt(initialPosition)?.let {
            store.saveLastDestinationId(it.id)
            navigation.markSelected(it.id)
        }
        navigation.applyPresentation(resources.configuration.isWideLayout())

        setupGlobalSearch()
        setupCollapseAllButton()
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
            // S1693: titleRow exists only in the portrait layout, so the generated field is nullable.
            binding.titleRow?.let { titleRow ->
                // The dp height is a floor: the title is sp, so a large font scale outgrows a fixed
                // row and the pane below clips it.
                titleRow.layoutParams.height = max(titleH, binding.titleText.lineHeight)
                titleRow.requestLayout()
            }
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
    // S4621: the page list is the first stop for D-pad and keyboard whenever it is on screen.
    override fun getInitialFocusView(): View? =
        if (binding.settingsRail.isVisible) binding.settingsRail else binding.viewPager

    /** S0289 Phase 09: route mouse wheel onto the settings pager so each tab fragment scrolls naturally. */
    override fun getMouseScrollTargetView(): View? = binding.viewPager

    /** The button and F1 both land here, so each tab gives one answer (CONTEXT-HELP rule 3). */
    private fun openTabHelp() {
        // S4621: the page answers by its destination; a flavor extension page has no help page of its own,
        // so its destination carries the screen-level entry.
        val surfaceId = pagerAdapter?.destinationAt(binding.viewPager.currentItem)?.helpSurfaceId
            ?: SettingsDestination.SCREEN_HELP_SURFACE
        HelpOpenManager.open(this, surfaceId, UiSurface.SETTINGS)
    }

    private fun setupGlobalSearch() {
        binding.searchResultsRecycler.layoutManager = LinearLayoutManager(this)
        binding.searchResultsRecycler.adapter = searchAdapter

        HelpTooltipManager.apply(binding.btnSettingsHelp)
        binding.btnSettingsHelp.setOnClickListener { openTabHelp() }

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

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // S4621: order is search overlay, then narrow page -> page list, then leaving the screen.
                    when {
                        binding.searchOverlay.isVisible -> closeSearchOverlay()
                        railManager?.onBackFromPage() == true -> Unit
                        else -> leaveSettings()
                    }
                }

                private fun leaveSettings() {
                    // If the back stack is empty (e.g. process was restarted while Settings was open),
                    // navigate up to MainActivity explicitly instead of going to home screen.
                    if (isTaskRoot) {
                        startActivity(
                            android.content.Intent(
                                this@SettingsActivity,
                                com.sza.fastmediasorter.ui.main.MainActivity::class.java
                            )
                                .addFlags(
                                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                                )
                        )
                        finish()
                        return
                    }
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        )
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
        // Clearing focus leaves the IME up, and on a short screen it covers the row a result jumps to.
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
        binding.searchInput.clearFocus()
    }

    private fun updateSearchResults(results: List<SettingsSearchIndex>) {
        searchAdapter.submitList(results)
        binding.searchEmptyText.isVisible = results.isEmpty()
    }

    private fun onSearchResultSelected(item: SettingsSearchIndex) {
        closeSearchOverlay()
        selectDestination(item.destination.destinationId)
        railManager?.takeIf { !it.isWide }?.showPage()

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
        val fragment = getSettingsFragment(pagerAdapter?.indexOf(item.destination.destinationId) ?: -1)
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
        // The retry chain outlives the screen otherwise, and expandSectionForTarget can commitNow() a
        // child fragment, which throws once the activity has saved its state.
        if (isFinishing || isDestroyed || supportFragmentManager.isStateSaved) return
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

    /** S4621: opens the page of destination [id]; an id this build does not have opens General. */
    private fun selectDestination(id: String) {
        val position = pagerAdapter?.indexOf(id)?.takeIf { it >= 0 } ?: 0
        selectPosition(position)
    }

    /**
     * S4622: a row of another page that leads to a Tools block opens it in place, the way a host command
     * does from outside. The extra is read by a page created later; a page that already exists is told now.
     */
    fun openToolsSection(sectionId: String) {
        intent.putExtra(EXTRA_EXPAND_SECTION, sectionId)
        openDestination(SettingsDestination.TOOLS)
        val position = pagerAdapter?.indexOf(SettingsDestination.TOOLS) ?: return
        binding.viewPager.post { (getSettingsFragment(position) as? ToolsSettingsFragment)?.expandSectionFromIntent() }
    }

    /** S4622: a page row that leads to another page (the Launcher opener) shows that page, as the menu does. */
    fun openDestination(id: String) {
        selectDestination(id)
        railManager?.takeIf { !it.isWide }?.showPage()
    }

    private fun selectPosition(position: Int) {
        if (binding.viewPager.currentItem != position) binding.viewPager.setCurrentItem(position, false)
    }

    /** S4621: the toolbar names the page while a narrow window shows it without the page list. */
    private fun onNavigationPaneChanged() {
        val manager = railManager ?: return
        val destination = pagerAdapter?.destinationAt(binding.viewPager.currentItem)
        binding.titleText.text = if (manager.isShowingNarrowPage && destination != null) {
            getString(destination.titleRes)
        } else {
            getString(R.string.settings)
        }
    }

    // This activity absorbs rotation (android:configChanges), so the breakpoint is re-read here.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        railManager?.applyPresentation(newConfig.isWideLayout())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        railManager?.saveState(outState)
    }

    /** S4394: a page's view exists - sync the toolbar collapse-all affordance to its state. */
    fun onSettingsPageViewReady() {
        refreshCollapseAllButton()
    }

    private fun setupCollapseAllButton() {
        binding.btnCollapseAllGroups.setOnClickListener {
            (getSettingsFragment(binding.viewPager.currentItem) as? BaseSettingsFragment)?.collapseAllSections()
        }
        binding.viewPager.post { refreshCollapseAllButton() }
    }

    /**
     * S4394: shows the toolbar collapse-all button while the current page has an expanded group.
     * The observer stays attached for the page's view lifetime; the position guard keeps a
     * background page's toggle from showing the button for a page the user is not on.
     */
    private fun refreshCollapseAllButton() {
        // Reached from viewPager.post, which can run after a theme recreate has already nulled the binding.
        if (isDestroyed) return
        val position = binding.viewPager.currentItem
        val fragment = getSettingsFragment(position) as? BaseSettingsFragment
        if (fragment == null) {
            binding.btnCollapseAllGroups.isVisible = false
            return
        }
        fragment.observeCollapseState { anyExpanded ->
            if (binding.viewPager.currentItem == position) {
                binding.btnCollapseAllGroups.isVisible = anyExpanded
            }
        }
    }

    private fun getSettingsFragment(position: Int): androidx.fragment.app.Fragment? {
        return supportFragmentManager.findFragmentByTag("f$position")
    }

    /**
     * S4621: scroll offset remembered for [page], or 0. Only a top-level page has a destination id; a
     * page nested inside Media carries no pager tag and so is never remembered on its own.
     */
    fun rememberedScrollOffset(page: androidx.fragment.app.Fragment): Int {
        val id = destinationIdOf(page) ?: return 0
        return pageStateStore?.scrollOffset(id) ?: 0
    }

    fun rememberScrollOffset(page: androidx.fragment.app.Fragment, offset: Int) {
        val id = destinationIdOf(page) ?: return
        pageStateStore?.saveScrollOffset(id, offset)
    }

    private fun destinationIdOf(page: androidx.fragment.app.Fragment): String? {
        val position = page.tag?.removePrefix("f")?.toIntOrNull()
            ?.takeIf { page.parentFragmentManager === supportFragmentManager }
        return position?.let { pagerAdapter?.destinationAt(it)?.id }
    }
}
