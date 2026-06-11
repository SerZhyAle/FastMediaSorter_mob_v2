package com.sza.fastmediasorter.ui.welcome

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import com.sza.fastmediasorter.core.input.TvNavAction
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.theme.ColorThemePrefs
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.databinding.ActivityWelcomeBinding
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.ui.settings.fragments.PermissionsManagementFragment
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerHelper
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerManager
import com.sza.fastmediasorter.ui.welcome.helpers.WelcomeFunctionalityController
import com.sza.fastmediasorter.ui.welcome.helpers.WelcomePermissionsManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class WelcomeActivity : BaseActivity<ActivityWelcomeBinding>(), PermissionsManagementFragment.WelcomeCompleteListener {

    private val viewModel: WelcomeViewModel by viewModels()

    @Inject
    lateinit var mediaCapabilities: MediaCapabilities

    /** Owns the functionality page (S0400): capability toggles + inline deliverable downloads. */
    @Inject
    lateinit var functionalityController: WelcomeFunctionalityController

    /** Owns the permissions page (S0402): adaptive permission set + grant-all flow (Activity launchers). */
    @Inject
    lateinit var permissionsManager: WelcomePermissionsManager

    private lateinit var pagerAdapter: WelcomePagerAdapter
    private lateinit var pagesList: MutableList<WelcomePage>
    private var currentPage = 0
    private var defaultPlayerPageIndex = -1
    // Separate field so the restored page is applied once in setupViewPager() without
    // affecting currentPage until the ViewPager is ready.
    private var restoredPage = 0
    private val pageBackgrounds = mutableListOf(
        R.color.welcome_page_1_background,
        R.color.welcome_page_2_background,
        R.color.welcome_page_3_background,
        R.color.welcome_page_4_background,
        R.color.welcome_page_5_background,
        R.color.welcome_page_6_background,
        R.color.welcome_page_7_background
    )

    override fun getViewBinding(): ActivityWelcomeBinding =
        ActivityWelcomeBinding.inflate(layoutInflater)

    override fun setupViews() {
        // On the permission screen (fromWelcome mode) Back completes the flow instead of closing
        // the app - WelcomeActivity is the root task at this point so a naked finish() would exit.
        // On the slide pages, Back minimises instead of finishing for the same reason.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.fragmentContainerWelcome.isVisible) {
                    completeWelcomeFlow()
                } else {
                    moveTaskToBack(true)
                }
            }
        })

        // Apply edge-to-edge insets: skip button below status bar, bottom nav above nav bar
        applyEdgeToEdgeInsets()

        // The permissions page (S0402) owns ActivityResult launchers - wire it before the pager binds.
        permissionsManager.attach(this)

        setupViewPager()
        setupButtons()
        updateUI()
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.state) { state ->
            // The device-profile page (S0399) is seeded at setup; once detection resolves, refresh the
            // grid selection directly (ViewPager2 will not rebind the visible page on notifyItemChanged).
            if (::pagerAdapter.isInitialized) {
                pagerAdapter.refreshProfiles(state.recommendedProfile, state.selectedProfile)
            }
        }

        collectOnLifecycle(viewModel.events) { event ->
            when (event) {
                is WelcomeEvent.ConfirmProfilePresetReapply ->
                    showProfilePresetReapplyWarning(event.type)
            }
        }
    }

    /** Re-entry from Settings changed the profile: warn before the preset overwrites tuned settings
     *  (mirrors the Settings device-profile picker warning). Confirm reapplies; cancel keeps settings. */
    private fun showProfilePresetReapplyWarning(type: DeviceProfileType) {
        AlertDialog.Builder(this)
            .setMessage(R.string.settings_profile_warning)
            .setPositiveButton(R.string.profile_picker_select) { _, _ ->
                viewModel.confirmProfilePresetReapply(type)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Onboarding is intentionally a bright pastel experience. Force the day (light) resource set
        // so the welcome pages keep dark-text-on-pastel-background readability even when the system
        // is in dark mode - the night palette uses saturated backgrounds with white text, which is
        // unreadable (e.g. white-on-#E65100 on the "Resources and Destinations" page).
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { state ->
            restoredPage = state.getInt(KEY_CURRENT_PAGE, 0)
        }
        // Restore the permissions grant-all run state (S0402) so a rotation mid-special-permission walk
        // resumes instead of restarting.
        permissionsManager.onRestoreInstanceState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_CURRENT_PAGE, currentPage)
        permissionsManager.onSaveInstanceState(outState)
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
        val statusBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        val navBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        val cutout = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout())

        // In landscape the status bar / nav bar / cutout may sit on a side edge - pad both sides.
        val sideInset = maxOf(
            statusBar.left, statusBar.right,
            navBar.left, navBar.right,
            cutout.left, cutout.right
        )
        val barPadding = resources.getDimensionPixelSize(R.dimen.welcome_top_nav_padding)

        // Page area clears the status bar / display cutout.
        binding.viewPager.setPadding(sideInset, statusBar.top, sideInset, 0)

        // The single bottom navigation bar clears the navigation bar and side cutouts.
        binding.layoutBottomNav.setPadding(
            barPadding + sideInset,
            barPadding,
            barPadding + sideInset,
            barPadding + navBar.bottom
        )
    }

    private fun setupViewPager() {
        pagesList = mutableListOf(
            // Page 1: Welcome (Enhanced with feature cards)
            WelcomePage(
                iconRes = R.drawable.welcome_hero_media,
                titleRes = R.string.welcome_title_1,
                descriptionRes = R.string.welcome_description_1,
                detailDescriptionRes = R.string.welcome_description_1_details,
                featureCards = listOf(
                    // Row 1 in 3-col grid: content + two storage origins
                    FeatureCard(R.drawable.ic_image, R.string.welcome_feature_photos),
                    FeatureCard(R.drawable.ic_resource_local, R.string.welcome_feature_local_folders),
                    FeatureCard(R.drawable.ic_resource_smb, R.string.welcome_feature_network),
                    // Row 2 in 3-col grid: cloud storage + two actions
                    FeatureCard(R.drawable.ic_resource_cloud, R.string.welcome_feature_cloud),
                    FeatureCard(R.drawable.ic_swap_horizontal, R.string.welcome_feature_sorting),
                    FeatureCard(R.drawable.ic_slideshow, R.string.welcome_feature_slideshow)
                ),
                showLanguagePicker = true,
                onLanguageSelected = ::onWelcomeLanguageSelected,
                showThemePicker = true,
                onThemeSelected = ::onWelcomeThemeSelected,
            ),
        )

        // S0399: device-profile page (index 1). Full tile grid; selection seeded from detection and
        // refreshed via refreshProfiles() once async detection resolves.
        pagesList.add(
            WelcomePage(
                isProfilesPage = true,
                selectableProfiles = viewModel.selectableProfiles(),
                recommendedProfileType = viewModel.state.value.recommendedProfile,
                selectedProfileType = viewModel.state.value.selectedProfile,
                onProfileSelected = { type -> viewModel.onProfileSelected(type) },
            )
        )

        // Decorative networks page (S0398 Phase 04, index 2). Static/informational; the Cloud tile
        // collapses on flavors without cloud support. S0391 later turns tiles into toggles.
        pagesList.add(
            WelcomePage(
                iconRes = 0,
                titleRes = 0,
                descriptionRes = 0,
                isNetworksPage = true,
                showCloudNetworkTile = mediaCapabilities.supportsCloud,
            )
        )

        // S0400: functionality page (index 3). Capability toggles + inline deliverable downloads;
        // WelcomeFunctionalityController owns all logic and binds via the page callback.
        pagesList.add(
            WelcomePage(
                isFunctionalityPage = true,
                onBindFunctionality = { b -> functionalityController.bind(b, this) },
            )
        )

        // S0402: permissions page (index 4). Adaptive permission set hosted in the pager so the step
        // indicator + nav stay visible; WelcomePermissionsManager owns the adaptive list + grant-all.
        pagesList.add(
            WelcomePage(
                isPermissionsPage = true,
                onBindPermissions = { b -> permissionsManager.bind(b) },
            )
        )

        // Page 6 (first install only): Default Player onboarding
        // markDefaultPlayerOnboardingShown() is called in onPageSelected() when the user reaches
        // this page - so skipping welcome doesn't suppress future display.
        val shouldShowDefaultPlayerPage = mediaCapabilities.supportsDefaultPlayer &&
            (!viewModel.isDefaultPlayerOnboardingShown() ||
                !DefaultPlayerHelper.isAlreadyDefaultPlayer(this))

        if (shouldShowDefaultPlayerPage) {
            defaultPlayerPageIndex = pagesList.size
            pagesList.add(
                WelcomePage(
                    iconRes = 0,
                    titleRes = 0,
                    descriptionRes = 0,
                    isDefaultPlayerPage = true,
                    onSetDefaultForTypeClick = { mimeType ->
                        // Enable aliases synchronously first - the system must see the alias
                        // as enabled before it will consider the app eligible for ROLE_MUSIC.
                        DefaultPlayerManager.applyPrimaryPlayerState(this, true)
                        viewModel.enablePrimaryMediaPlayer() // persist to DataStore
                        DefaultPlayerHelper.openChooserOrFallbackFromActivity(this, mimeType)
                    }
                )
            )
        }

        pagerAdapter = WelcomePagerAdapter(pagesList)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                if (position == defaultPlayerPageIndex) {
                    viewModel.markDefaultPlayerOnboardingShown()
                }
                updateUI()
            }
        })

        setupIndicators(pagesList.size)

        // Restore ViewPager position after activity recreation so the user lands on the
        // same page they were on before the config change, not always back on page 0.
        if (restoredPage > 0 && restoredPage < pagesList.size) {
            currentPage = restoredPage
            previousPage = restoredPage
            binding.viewPager.setCurrentItem(restoredPage, false)
            restoredPage = 0
        }
    }

    private val indicatorDotSize by lazy {
        resources.getDimensionPixelSize(R.dimen.indicator_size)
    }
    private val indicatorPillWidth by lazy {
        (indicatorDotSize * 3) // Active pill is 3x wider
    }
    private val indicatorMarginPx by lazy {
        resources.getDimensionPixelSize(R.dimen.indicator_margin)
    }

    private fun setupIndicators(count: Int) {
        binding.layoutIndicator.removeAllViews()

        for (i in 0 until count) {
            val isActive = i == 0
            val indicator = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    if (isActive) indicatorPillWidth else indicatorDotSize,
                    indicatorDotSize
                ).apply {
                    setMargins(indicatorMarginPx, 0, indicatorMarginPx, 0)
                }
                setBackgroundResource(
                    if (isActive) R.drawable.indicator_active
                    else R.drawable.indicator_inactive
                )
            }
            binding.layoutIndicator.addView(indicator)
        }
    }

    private var previousPage = 0

    private fun updateIndicators() {
        if (previousPage == currentPage) return

        val count = binding.layoutIndicator.childCount
        if (count == 0) return

        // Animate previous indicator: pill → dot
        if (previousPage in 0 until count) {
            val prevIndicator = binding.layoutIndicator.getChildAt(previousPage)
            prevIndicator.setBackgroundResource(R.drawable.indicator_inactive)
            animateIndicatorWidth(prevIndicator, indicatorPillWidth, indicatorDotSize)
        }

        // Animate current indicator: dot → pill
        if (currentPage in 0 until count) {
            val currIndicator = binding.layoutIndicator.getChildAt(currentPage)
            currIndicator.setBackgroundResource(R.drawable.indicator_active)
            animateIndicatorWidth(currIndicator, indicatorDotSize, indicatorPillWidth)
        }

        previousPage = currentPage
    }

    private fun animateIndicatorWidth(view: View, from: Int, to: Int) {
        ValueAnimator.ofInt(from, to).apply {
            duration = 250
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val params = view.layoutParams
                params.width = animator.animatedValue as Int
                view.layoutParams = params
            }
            start()
        }
    }

    private fun setupButtons() {
        binding.btnPrevious.setOnClickListener {
            if (currentPage > 0) {
                binding.viewPager.currentItem = currentPage - 1
            }
        }

        binding.btnNext.setOnClickListener {
            if (currentPage < pagerAdapter.itemCount - 1) {
                binding.viewPager.currentItem = currentPage + 1
            }
        }

        binding.btnFinish.setOnClickListener {
            viewModel.saveDeviceProfile(isSkipped = false)
            // S0402: permissions are now requested on their own pager page, not a terminal overlay -
            // Finish goes straight to completion (grants already happened on the permissions page).
            completeWelcomeFlow()
        }
    }

    private fun updateUI() {
        applyPageBackground()
        updateIndicators()

        val isLastPage = currentPage == pagerAdapter.itemCount - 1
        val isFirstPage = currentPage == 0

        binding.btnPrevious.visibility = if (isFirstPage) View.INVISIBLE else View.VISIBLE
        
        if (isLastPage) {
            binding.btnNext.visibility = View.GONE
            binding.btnFinish.visibility = View.VISIBLE
        } else {
            binding.btnNext.visibility = View.VISIBLE
            binding.btnFinish.visibility = View.GONE
        }
    }

    private fun applyPageBackground() {
        val backgroundIndex = currentPage.coerceIn(0, pageBackgrounds.lastIndex)
        binding.root.setBackgroundResource(pageBackgrounds[backgroundIndex])
    }

    @Suppress("DEPRECATION")
    private fun onWelcomeLanguageSelected(code: String) {
        LocaleHelper.saveLanguage(this, code)
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            // API < 33: LocaleManager is unavailable - recreate Activity manually.
            // overridePendingTransition(0, 0) called after recreate() suppresses the
            // default enter animation of the new Activity instance.
            // S0218: API < 33 < 34, so overrideActivityTransition (API 34+) is unreachable
            // here; overridePendingTransition is the only valid call on this branch.
            // @Suppress("DEPRECATION") on the function silences the unavoidable warning.
            recreate()
            overridePendingTransition(0, 0)
        }
        // API 33+: LocaleManager already called in saveLanguage() will recreate the
        // Activity automatically with no further action needed here.
    }

    private fun onWelcomeThemeSelected(mode: String) {
        Timber.d("S0398: page0 theme selected mode=$mode (dual-write, applied next cold start)")
        // Dual-write, mirroring the Settings split: the ViewModel persists the canonical DataStore
        // value; the synchronous startup mirror is written here in the UI layer. Welcome stays
        // force-light (onCreate forces MODE_NIGHT_NO), so the theme is applied on the next cold start
        // - intentionally no applyMode()/recreate() here.
        viewModel.saveColorTheme(mode)
        ColorThemePrefs.setMode(this, mode)
    }

    override fun onWelcomeComplete() {
        completeWelcomeFlow()
    }

    private fun completeWelcomeFlow() {
        if (hasRequiredMediaPermissions()) {
            viewModel.setMediaPermissionsGranted(true)
        }
        goToMainActivity()
    }

    private fun goToMainActivity() {
        viewModel.setWelcomeCompleted()

        // Check if this is the first run after welcome completion
        if (viewModel.isFirstRunAfterWelcome()) {
            // Mark first run as completed
            viewModel.setFirstRunCompleted()

            Toast.makeText(this, R.string.setup_content_first_toast, Toast.LENGTH_LONG).show()

            // Navigate to Settings with MainActivity as the back-stack root so that
            // pressing Back from Settings returns to MainActivity instead of closing the app.
            TaskStackBuilder.create(this)
                .addNextIntent(Intent(this, MainActivity::class.java))
                .addNextIntent(Intent(this, SettingsActivity::class.java))
                .startActivities()
            finish()
        } else {
            // Re-entry from Settings: return to the caller (the Settings back stack) instead of
            // clearing the task to MainActivity, so the user lands back where they opened onboarding.
            Timber.d("S0398: welcome re-entry complete, finishing to return to caller (no CLEAR_TASK)")
            finish()
        }
    }

    private fun getRequiredMediaPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            }

            // API 29-32: WRITE_EXTERNAL_STORAGE not grantable on targetSdk 29+; READ only
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            // API 23-28: both READ and WRITE required at runtime
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }

            else -> emptyArray()
        }
    }

    private fun hasRequiredMediaPermissions(): Boolean {
        return getRequiredMediaPermissions().all { permission ->
            ContextCompat.checkSelfPermission(this, permission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    // ── S0230: TV / keyboard navigation ──────────────────────────────────────

    /**
     * The welcome slider owns ALL of its navigation keys here, before they reach TvKeyRouter / the
     * ViewPager2. This is deliberate: TvKeyRouter drops events whose source carries gamepad bits, and
     * some TV remotes / emulators report D-pad that way, which would otherwise let the ViewPager2
     * RecyclerView perform its own focus-escape page scroll. Handling the keys at dispatch time makes
     * slider navigation source-independent. On the permissions fragment (fragment container visible)
     * everything falls through to native framework traversal. S0289.
     *
     * - LEFT / RIGHT: focus a neighbour within the current scope (page content or bottom bar); flip
     *   the page only at the scope's true horizontal edge.
     * - UP / DOWN: move focus between the page content and the bottom bar.
     * - ENTER / DPAD_CENTER: activate the focused control, else the primary CTA.
     * - TAB / SHIFT+TAB: cycle focus through page + bar. ESC: Back.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            Timber.d("S0289DIAG: dispatch key=${event.keyCode} src=0x${Integer.toHexString(event.source)} fragVisible=${binding.fragmentContainerWelcome.isVisible}")
        }
        if (event.action == KeyEvent.ACTION_DOWN && !binding.fragmentContainerWelcome.isVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { handleSliderHorizontal(View.FOCUS_LEFT, forward = false); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { handleSliderHorizontal(View.FOCUS_RIGHT, forward = true); return true }
                KeyEvent.KEYCODE_DPAD_UP -> { handleSliderVertical(View.FOCUS_UP); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { handleSliderVertical(View.FOCUS_DOWN); return true }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> if (handleSliderSelect()) return true
                KeyEvent.KEYCODE_TAB -> if (handleSliderSequential(forward = !event.isShiftPressed)) return true
                KeyEvent.KEYCODE_ESCAPE -> { onBackPressedDispatcher.onBackPressed(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * BACK is the only slider key still routed through TvKeyRouter; D-pad / ENTER / TAB / ESC are
     * owned by [dispatchKeyEvent] above. On the permissions fragment everything falls through to
     * native framework traversal.
     */
    override fun onTvNavigation(action: TvNavAction): Boolean = when (action) {
        TvNavAction.Back -> { onBackPressedDispatcher.onBackPressed(); true }
        else -> false
    }

    /** ENTER / DPAD_CENTER: activate the focused clickable control, else the visible primary CTA. */
    private fun handleSliderSelect(): Boolean {
        val focused = currentFocus
        if (focused != null && focused.isClickable) {
            focused.performClick()
            return true
        }
        return when {
            binding.btnFinish.isVisible -> { binding.btnFinish.performClick(); true }
            binding.btnNext.isVisible -> { binding.btnNext.performClick(); true }
            else -> false
        }
    }

    /** The itemView of the currently-visible ViewPager2 page, or null if not laid out yet. */
    private fun currentPageView(): View? {
        val recycler = binding.viewPager.getChildAt(0) as? RecyclerView ?: return null
        return recycler.findViewHolderForAdapterPosition(currentPage)?.itemView
    }

    private fun isDescendantOf(view: View, parent: View): Boolean {
        var p = view.parent
        while (p != null) {
            if (p === parent) return true
            p = p.parent
        }
        return false
    }

    /** Horizontal focus scope for [view]: the bottom bar if focus is there, else the current page. */
    private fun horizontalScope(view: View?): ViewGroup? {
        if (view == null) return null
        if (isDescendantOf(view, binding.layoutBottomNav)) return binding.layoutBottomNav
        val page = currentPageView()
        return if (page is ViewGroup && isDescendantOf(view, page)) page else null
    }

    /**
     * LEFT / RIGHT: move focus to a neighbour inside the current scope; flip the page only at the
     * scope's horizontal edge. Always consumes so ViewPager2 never performs its own page scroll.
     */
    private fun handleSliderHorizontal(direction: Int, forward: Boolean): Boolean {
        if (binding.fragmentContainerWelcome.isVisible) return false
        val focused = currentFocus
        val scope = horizontalScope(focused)
        if (focused != null && scope != null) {
            val neighbour = FocusFinder.getInstance().findNextFocus(scope, focused, direction)
            if (neighbour != null && neighbour !== focused) {
                neighbour.requestFocus()
                return true
            }
        }
        return flipPage(forward)
    }

    /**
     * UP / DOWN: move focus between the current page content and the bottom bar without letting the
     * ViewPager2 RecyclerView perform its focus-escape page scroll. Always consumes.
     */
    private fun handleSliderVertical(direction: Int): Boolean {
        if (binding.fragmentContainerWelcome.isVisible) return false
        val focused = currentFocus
        val bar = binding.layoutBottomNav
        val page = currentPageView() as? ViewGroup
        val inBar = focused != null && isDescendantOf(focused, bar)
        val inPage = focused != null && page != null && isDescendantOf(focused, page)

        when {
            inPage -> {
                val next = FocusFinder.getInstance().findNextFocus(page, focused, direction)
                if (next != null && next !== focused) {
                    next.requestFocus()
                } else if (direction == View.FOCUS_DOWN) {
                    focusBar()
                }
            }
            inBar -> if (direction == View.FOCUS_UP && page != null) {
                page.requestFocus(View.FOCUS_UP)
            }
            else -> (page ?: bar).requestFocus(View.FOCUS_DOWN)
        }
        return true
    }

    /** Focus the visible primary button in the bottom bar. */
    private fun focusBar() {
        val target = binding.btnFinish.takeIf { it.isVisible }
            ?: binding.btnNext
        target.requestFocus()
    }

    /** Visible, focusable controls of the slider in TAB order: current page content, then bottom bar. */
    private fun sliderFocusables(): List<View> {
        val list = ArrayList<View>()
        (currentPageView() as? ViewGroup)?.addFocusables(list, View.FOCUS_FORWARD)
        binding.layoutBottomNav.addFocusables(list, View.FOCUS_FORWARD)
        return list.filter { it.isShown && it.isFocusable }
    }

    /** TAB / SHIFT+TAB: cycle focus within the slider (page + bar) with wraparound, no pager scroll. */
    private fun handleSliderSequential(forward: Boolean): Boolean {
        val all = sliderFocusables()
        if (all.isEmpty()) return false
        val idx = all.indexOf(currentFocus)
        val nextIdx = when {
            idx < 0 -> 0
            forward -> (idx + 1) % all.size
            else -> (idx - 1 + all.size) % all.size
        }
        all[nextIdx].requestFocus()
        return true
    }

    private fun flipPage(forward: Boolean): Boolean {
        return if (forward) {
            if (currentPage < pagerAdapter.itemCount - 1) {
                binding.viewPager.currentItem = currentPage + 1
                true
            } else false
        } else {
            if (currentPage > 0) {
                binding.viewPager.currentItem = currentPage - 1
                true
            } else false
        }
    }

    /**
     * On TV, set initial focus to the primary forward-action button so the first D-pad
     * press is immediately actionable without a random "focus init" key press.
     */
    override fun getInitialFocusView(): View {
        Timber.d("S0289: welcome initial-focus / edge-aware slider")
        return binding.btnNext
    }

    /** S0289 Phase 09: route mouse wheel onto the onboarding pager. */
    override fun getMouseScrollTargetView(): View? = binding.viewPager

    companion object {
        private const val KEY_CURRENT_PAGE = "key_current_page"
    }
}
