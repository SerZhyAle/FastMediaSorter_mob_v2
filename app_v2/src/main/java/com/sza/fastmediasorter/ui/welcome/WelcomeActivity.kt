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
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.input.TvNavAction
import com.sza.fastmediasorter.core.launcher.LauncherRoleManager
import com.sza.fastmediasorter.core.theme.ColorThemePrefs
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.databinding.ActivityWelcomeBinding
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerHelper
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerManager
import com.sza.fastmediasorter.ui.welcome.helpers.WelcomeEnableAllManager
import com.sza.fastmediasorter.ui.welcome.helpers.WelcomeFunctionalityController
import com.sza.fastmediasorter.ui.welcome.helpers.WelcomePermissionsManager
import com.sza.fastmediasorter.ui.welcome.helpers.WelcomeRemoteSourcesController
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WelcomeActivity : BaseActivity<ActivityWelcomeBinding>() {

    private val viewModel: WelcomeViewModel by viewModels()

    @Inject
    lateinit var mediaCapabilities: MediaCapabilities

    /** Owns the functionality page (S0400): capability toggles + inline deliverable downloads. */
    @Inject
    lateinit var functionalityController: WelcomeFunctionalityController

    /** Owns the permissions page (S0402): adaptive permission set + grant-all flow (Activity launchers). */
    @Inject
    lateinit var permissionsManager: WelcomePermissionsManager

    /** Owns the networks page (S0391): three remote-source group toggles over the six per-source flags. */
    @Inject
    lateinit var remoteSourcesController: WelcomeRemoteSourcesController

    /** Owns the "Enable all" sequence (S0409): profile + settings + permissions + default-player + finish. */
    @Inject
    lateinit var enableAllManager: WelcomeEnableAllManager

    @Inject
    lateinit var launcherModeContract: LauncherModeContract

    @Inject
    lateinit var launcherRoleManager: LauncherRoleManager

    // Overlay-permission result for the Welcome gesture toggle. Registered at construction (before
    // STARTED, as the API requires); the callback routes back into the functionality controller.
    private val gestureOverlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            functionalityController.onGesturePermissionResult()
        }

    // S0404: guards maybeRequestLauncherMode to fire once across the several completeWelcomeFlow call sites.
    private var launcherModeHandled = false

    private lateinit var pagerAdapter: WelcomePagerAdapter
    private lateinit var pagesList: MutableList<WelcomePage>
    private var currentPage = 0
    private var defaultPlayerPageIndex = -1
    private var profilesPageIndex = -1
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
        // WelcomeActivity is the root task at this point, so Back minimises instead of exiting.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        // Apply edge-to-edge insets: skip button below status bar, bottom nav above nav bar
        applyEdgeToEdgeInsets()

        // S0910: enable-all MUST attach before permissionsManager. permissionsManager.attach() registers
        // the special-settings launcher, and on a mid-run recreation (rotation / process death) that
        // register() SYNCHRONOUSLY redelivers the pending MANAGE_MEDIA result - which finishes the grant-all
        // run and clears grantAllInProgress. enableAllManager.attach() re-arms grantAllOnComplete (guarded on
        // grantAllInProgress), so it must run first, while grantAllInProgress is still the restored `true`;
        // otherwise the re-arm no-ops and the default-player stage never starts (the S0910 stall).
        enableAllManager.attach(this, permissionsManager) { completeWelcomeFlow() }

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
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.settings_profile_warning)
            .setPositiveButton(R.string.profile_picker_select) { _, _ ->
                viewModel.confirmProfilePresetReapply(type)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { state ->
            restoredPage = state.getInt(KEY_CURRENT_PAGE, 0)
        }
        // Restore the permissions grant-all run state (S0402) so a rotation mid-special-permission walk
        // resumes instead of restarting.
        permissionsManager.onRestoreInstanceState(savedInstanceState)
        enableAllManager.onRestoreInstanceState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_CURRENT_PAGE, currentPage)
        permissionsManager.onSaveInstanceState(outState)
        enableAllManager.onSaveInstanceState(outState)
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
                iconRes = R.drawable.ic_app_logo,
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
                showLauncherModeToggle = launcherModeContract.isAvailableInBuild,
                launcherModeChecked = viewModel.launcherModeRequested,
                onLauncherModeToggled = { viewModel.setLauncherModeRequested(it) },
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

        // Networks page (index 2). Three remote-source group toggles (SMB / (S)FTP / Cloud);
        // WelcomeRemoteSourcesController owns all logic and binds via the page callback. The cloud
        // toggle collapses on flavors without cloud support (decided inside the controller via the gate).
        pagesList.add(
            WelcomePage(
                iconRes = 0,
                titleRes = 0,
                descriptionRes = 0,
                isNetworksPage = true,
                onBindNetworks = { b -> remoteSourcesController.bind(b, this) },
            )
        )

        // S0400: functionality page (index 3). Capability toggles + inline deliverable downloads;
        // WelcomeFunctionalityController owns all logic and binds via the page callback.
        functionalityController.attachGesturePermissionLauncher(gestureOverlayPermissionLauncher)
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
                        DefaultPlayerManager.applyPrimaryPlayerState(this, true, mediaCapabilities)
                        viewModel.enablePrimaryMediaPlayer() // persist to DataStore
                        DefaultPlayerHelper.openChooserOrFallbackFromActivity(this, mimeType)
                    }
                )
            )
        }

        profilesPageIndex = pagesList.indexOfFirst { it.isProfilesPage }

        pagerAdapter = WelcomePagerAdapter(pagesList, mediaCapabilities)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                if (position == defaultPlayerPageIndex) {
                    viewModel.markDefaultPlayerOnboardingShown()
                }
                // S0471: once the user advances past the device-profile page, apply the selected
                // profile's settings preset so the following capability/permission pages render its
                // defaults; any change the user then makes there survives to completion (deduped in VM).
                if (profilesPageIndex >= 0 && position > profilesPageIndex) {
                    viewModel.applyFirstRunPresetForSelectedProfile()
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

        // S0409: one-tap full setup. Sets profile OTHER, enables everything, walks permission +
        // default-player dialogs, then finishes - skipping the remaining pages.
        binding.btnEnableAll.setOnClickListener {
            enableAllManager.start {
                viewModel.onProfileSelected(DeviceProfileType.OTHER)
                viewModel.saveDeviceProfile(isSkipped = false)
            }
        }
    }

    private fun updateUI() {
        applyPageBackground()
        updateIndicators()

        val isLastPage = currentPage == pagerAdapter.itemCount - 1
        val isFirstPage = currentPage == 0

        binding.btnPrevious.visibility = if (isFirstPage) View.INVISIBLE else View.VISIBLE

        // S0409: the Enable-all shortcut lives only on the first page, beside Next.
        binding.btnEnableAll.visibility = if (isFirstPage) View.VISIBLE else View.GONE

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
        // Mirror the Settings split: DataStore remains canonical in the ViewModel while the
        // synchronous SharedPreferences mirror updates here so the next Activity launch picks the
        // same mode before inflation. Accent-only changes need a manual recreate because
        // setDefaultNightMode is a no-op when the night-mode bucket stays the same.
        val previousNightMode = AppCompatDelegate.getDefaultNightMode()
        val newNightMode = ColorThemePrefs.toNightMode(mode)
        viewModel.saveColorTheme(mode)
        ColorThemePrefs.setMode(this, mode)
        ColorThemePrefs.applyMode(mode)
        if (newNightMode == previousNightMode) {
            recreate()
        }
    }

    private fun completeWelcomeFlow() {
        if (hasRequiredMediaPermissions()) {
            viewModel.setMediaPermissionsGranted(true)
        }
        // S0404/S1107: the user opted in to launcher mode on the first Welcome page. Enable the HOME
        // component as a durable candidate now, but do NOT launch the role dialog from this finishing
        // frame - it would be buried under the MainActivity+SettingsActivity stack (ADR-2). Instead route
        // the request to the non-finishing first-run Settings screen, which auto-triggers the working
        // enableMode() path there (the "chooser on next Home press" never fires when a default launcher
        // already exists, which is every real device).
        val requestLauncherRole =
            !launcherModeHandled && launcherModeContract.isAvailableInBuild && viewModel.launcherModeRequested
        if (requestLauncherRole) {
            launcherModeHandled = true
            launcherRoleManager.markAsHomeCandidate()
        }
        goToMainActivity(requestLauncherRole)
    }

    private fun goToMainActivity(requestLauncherRole: Boolean = false) {
        viewModel.setWelcomeCompleted()

        // Check if this is the first run after welcome completion
        if (viewModel.isFirstRunAfterWelcome()) {
            // Mark first run as completed
            viewModel.setFirstRunCompleted()

            Toast.makeText(this, R.string.setup_content_first_toast, Toast.LENGTH_LONG).show()

            // Navigate to Settings with MainActivity as the back-stack root so that
            // pressing Back from Settings returns to MainActivity instead of closing the app.
            // S1107: on the launcher opt-in path, deep-link Settings to the launcher section and have it
            // auto-request the HOME role from that non-finishing context (reliable, unlike a dialog from
            // this finishing onboarding frame).
            val settingsIntent = if (requestLauncherRole) {
                SettingsActivity.openLauncherSectionIntent(this, requestRole = true)
            } else {
                Intent(this, SettingsActivity::class.java)
            }
            TaskStackBuilder.create(this)
                .addNextIntent(Intent(this, MainActivity::class.java))
                .addNextIntent(settingsIntent)
                .startActivities()
            finish()
        } else {
            // Re-entry from Settings: return to the caller (the Settings back stack) instead of
            // clearing the task to MainActivity, so the user lands back where they opened onboarding.
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
     * slider navigation source-independent. S0289.
     *
     * - LEFT / RIGHT: focus a neighbour within the current scope (page content or bottom bar); flip
     *   the page only at the scope's true horizontal edge.
     * - UP / DOWN: move focus between the page content and the bottom bar.
     * - ENTER / DPAD_CENTER: activate the focused control, else the primary CTA.
     * - TAB / SHIFT+TAB: cycle focus through page + bar. ESC: Back.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
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
     * owned by [dispatchKeyEvent] above.
     */
    override fun onTvNavigation(action: TvNavAction): Boolean = when (action) {
        TvNavAction.Back -> { onBackPressedDispatcher.onBackPressed(); true }
        else -> false
    }

    /** ENTER / DPAD_CENTER: activate the focused clickable control, else the visible primary CTA. */
    private fun handleSliderSelect(): Boolean {
        if (activateFocusedViewOrAncestor()) return true
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
     * The first focusable control inside the current page, or null if the page has none.
     * ViewPager2's RecyclerView descends to the page when asked for FOCUS_DOWN, so this returns the
     * top-most actionable control (e.g. the language picker on the first page).
     */
    private fun firstPageFocusable(): View? {
        val page = currentPageView() as? ViewGroup ?: return null
        val candidates = ArrayList<View>()
        page.addFocusables(candidates, View.FOCUS_FORWARD)
        return candidates.firstOrNull { it.isShown && it.isFocusable }
    }

    /**
     * Move focus from the pager container onto a real control: the first focusable in the current
     * page, falling back to the bottom bar. Returns true if focus moved off the container.
     *
     * Why: the first D-pad press on a freshly-opened page leaves focus on the ViewPager2 RecyclerView
     * (the page's parent, not a descendant), so it belongs to neither the page nor the bar scope.
     * Without this, LEFT/RIGHT would resolve a null scope and fall straight through to flipPage,
     * making the in-page pickers unreachable on a remote that has no TAB key. S0289.
     */
    private fun enterPageFromContainer(): Boolean {
        firstPageFocusable()?.let { it.requestFocus(); return true }
        if (binding.btnNext.isVisible || binding.btnFinish.isVisible) { focusBar(); return true }
        return false
    }

    /** True when focus sits on neither the current page content nor the bottom bar (e.g. on the
     *  ViewPager2 RecyclerView container itself, or nowhere yet). */
    private fun isOnPagerContainer(focused: View?): Boolean {
        if (focused == null) return true
        if (isDescendantOf(focused, binding.layoutBottomNav)) return false
        val page = currentPageView()
        return !(page is ViewGroup && isDescendantOf(focused, page))
    }

    /**
     * LEFT / RIGHT: move focus to a neighbour inside the current scope; flip the page only at the
     * scope's horizontal edge. Always consumes so ViewPager2 never performs its own page scroll.
     */
    private fun handleSliderHorizontal(direction: Int, forward: Boolean): Boolean {
        val focused = currentFocus
        // Focus on the pager container (typical after the very first D-pad key): pull it into the page
        // instead of flipping, so the in-page pickers become reachable without a TAB key.
        if (isOnPagerContainer(focused) && enterPageFromContainer()) return true
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
            inBar -> if (direction == View.FOCUS_UP) {
                // From the bar, UP re-enters the page on a real control (last focusable for a natural
                // "come back to where you were near the bottom" feel), else stays put.
                val target = (currentPageView() as? ViewGroup)?.let { p ->
                    val list = ArrayList<View>()
                    p.addFocusables(list, View.FOCUS_FORWARD)
                    list.lastOrNull { it.isShown && it.isFocusable }
                }
                target?.requestFocus()
            }
            // Focus on the pager container: pull it onto a real control instead of leaving it stranded.
            else -> enterPageFromContainer()
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
        return binding.btnNext
    }

    /** S0289 Phase 09: route mouse wheel onto the onboarding pager. */
    override fun getMouseScrollTargetView(): View? = binding.viewPager

    companion object {
        private const val KEY_CURRENT_PAGE = "key_current_page"
    }
}
