package com.sza.fastmediasorter.ui.welcome

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.input.TvNavAction
import com.sza.fastmediasorter.core.launcher.LauncherRoleManager
import com.sza.fastmediasorter.core.theme.ColorThemePrefs
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.core.util.StoragePermissionRule
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.databinding.ActivityWelcomeBinding
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.ui.dialog.SearchableLanguagePickerDialog
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.profile.DeviceProfilePickerDialogFragment
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerHelper
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerManager
import com.sza.fastmediasorter.ui.welcome.helpers.WelcomeEnableAllManager
import com.sza.fastmediasorter.ui.welcome.helpers.WelcomeFeatureCards
import com.sza.fastmediasorter.ui.welcome.helpers.WelcomeFunctionalityController
import com.sza.fastmediasorter.ui.welcome.helpers.WelcomePermissionsManager
import com.sza.fastmediasorter.ui.welcome.helpers.WelcomeRemoteSourcesController
import com.sza.fastmediasorter.ui.welcome.helpers.WelcomeTvNavigationManager
import com.sza.fastmediasorter.util.showBoundToHost
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WelcomeActivity : BaseActivity<ActivityWelcomeBinding>() {

    private val viewModel: WelcomeViewModel by viewModels()

    @Inject
    lateinit var mediaCapabilities: MediaCapabilities

    /** Compile-time availability of the optional surfaces the first-page pitch may name (S2310). */
    @Inject
    lateinit var capabilityAvailability: CapabilityAvailability

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

    // S1377: registered in setupViewPager, unregistered on the destroy edge below.
    private var rotationManager: com.sza.fastmediasorter.ui.welcome.helpers.WelcomeRotationManager? = null

    // S1234: the per-page palette moved to WelcomePagePalette - the brand animation owns the page
    // background now, and the colour tints the translucent panel behind each page's copy instead.

    // S2312: the slider's D-pad / keyboard focus logic owns no Activity state, so it lives in its own
    // manager and this Activity only dispatches keys into it. Built lazily because it needs the binding.
    private val tvNavigation: WelcomeTvNavigationManager by lazy {
        WelcomeTvNavigationManager(
            binding = binding,
            currentPage = { currentPage },
            pageCount = { pagerAdapter.itemCount },
            focusedView = { currentFocus },
            activateFocused = { activateFocusedViewOrAncestor() },
        )
    }

    override fun getViewBinding(): ActivityWelcomeBinding =
        ActivityWelcomeBinding.inflate(layoutInflater)

    override fun setupViews() {
        observeBrandBackdrop()
        // WelcomeActivity is the root task at this point, so Back minimises instead of exiting.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    moveTaskToBack(true)
                }
            }
        )

        // Apply edge-to-edge insets: skip button below status bar, bottom nav above nav bar
        applyEdgeToEdgeInsets()

        // S0910: enable-all MUST attach before permissionsManager. permissionsManager.attach() registers
        // the special-settings launcher, and on a mid-run recreation (rotation / process death) that
        // register() SYNCHRONOUSLY redelivers the pending MANAGE_MEDIA result - which finishes the grant-all
        // run and clears grantAllInProgress. enableAllManager.attach() re-arms grantAllOnComplete (guarded on
        // grantAllInProgress), so it must run first, while grantAllInProgress is still the restored `true`;
        // otherwise the re-arm no-ops and the default-player stage never starts (the S0910 stall).
        enableAllManager.attach(
            activity = this,
            permissionsManager = permissionsManager,
            // S2322: the default-app stage opens one of the user's own files so the OS can raise its
            // "Open with / Always" sheet. Announce it here, immediately before it happens - the
            // overview shown at the very start is long gone behind the permission screens by now.
            onConfirmDefaultPlayerStage = {
                if (supportFragmentManager.findFragmentByTag(TAG_ENABLE_ALL_DEFAULT_APP) == null) {
                    WelcomeEnableAllExplainerDialogFragment.newInstance(
                        mode = WelcomeEnableAllExplainerDialogFragment.Mode.DEFAULT_APP,
                        requestKey = REQUEST_KEY_ENABLE_ALL_DEFAULT_APP,
                    ).show(supportFragmentManager, TAG_ENABLE_ALL_DEFAULT_APP)
                }
            },
        ) { completeWelcomeFlow() }

        // The permissions page (S0402) owns ActivityResult launchers - wire it before the pager binds.
        permissionsManager.attach(this)

        // S1214: this screen recreates itself routinely (theme picks) and the language picker survives
        // that recreation, so the listener belongs to the Activity instance rather than to a tap, and
        // the language in effect is re-read on delivery instead of captured when the picker opened.
        supportFragmentManager.setFragmentResultListener(
            SearchableLanguagePickerDialog.RESULT_KEY,
            this
        ) { _, bundle ->
            val code = bundle.getString(SearchableLanguagePickerDialog.RESULT_LANGUAGE_CODE)
                ?: return@setFragmentResultListener
            if (code != LocaleHelper.getLanguage(this)) {
                onWelcomeLanguageSelected(code)
            }
        }

        registerEnableAllResultListeners()

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
                    showProfilePresetReapplyWarning(event.type, event.overrideCount)
            }
        }
    }

    /** Re-entry from Settings changed the profile: warn before the preset overwrites tuned settings
     *  (mirrors the Settings device-profile picker warning). Confirm reapplies; cancel keeps settings.
     *  S1216: [overrideCount] is resolved by the ViewModel and names how many settings are at stake;
     *  a profile that overrides nothing is applied without a dialog, same as the Settings picker. */
    private fun showProfilePresetReapplyWarning(type: DeviceProfileType, overrideCount: Int) {
        if (overrideCount == 0) {
            viewModel.confirmProfilePresetReapply(type)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setMessage(
                resources.getQuantityString(
                    R.plurals.settings_profile_warning_count,
                    overrideCount,
                    overrideCount
                )
            )
            .setPositiveButton(R.string.profile_picker_select) { _, _ ->
                viewModel.confirmProfilePresetReapply(type)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showBoundToHost(this@WelcomeActivity)
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

    /**
     * S1234: the brand backdrop runs only while onboarding is on screen, so a backgrounded welcome
     * never keeps a 60 fps animator alive - the same contract the launcher desktop uses for this
     * view. A lifecycle observer rather than onStart/onStop overrides: this class already sits on
     * detekt's 40-function ceiling, and the pair belongs together anyway.
     */
    private fun observeBrandBackdrop() {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                binding.brandAnimation.startAnimation()
            }

            override fun onStop(owner: LifecycleOwner) {
                binding.brandAnimation.pauseAnimation()
            }

            // S1377: the rotation callback is registered against the Application, so it outlives this
            // screen unless dropped here. Riding this observer keeps the Activity off detekt's
            // 40-function ceiling that the KDoc above records.
            override fun onDestroy(owner: LifecycleOwner) {
                rotationManager?.let { unregisterComponentCallbacks(it) }
                rotationManager = null
            }
        })
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
            statusBar.left,
            statusBar.right,
            navBar.left,
            navBar.right,
            cutout.left,
            cutout.right
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
        // S2310: a build that ships the home surface is pitched as a device shell, not as a media
        // organizer. The same capability that decides the launcher toggle below picks the copy, so
        // the page can never promise a home screen the build does not compile in (S1388).
        val shellPitch = launcherModeContract.isAvailableInBuild
        val streamsInPitch = capabilityAvailability.isStreamsAvailable()
        val wearInPitch = mediaCapabilities.supportsWearCompanion

        val firstPageDescriptionRes = if (shellPitch) {
            R.string.welcome_description_1_shell
        } else {
            R.string.welcome_description_1
        }
        val firstPageDetailsRes = if (shellPitch) {
            R.string.welcome_description_1_details_shell
        } else {
            R.string.welcome_description_1_details
        }
        pagesList = mutableListOf(
            // Page 1: Welcome (Enhanced with feature cards)
            WelcomePage(
                iconRes = R.drawable.ic_app_logo,
                titleRes = R.string.welcome_title_1,
                descriptionRes = firstPageDescriptionRes,
                detailDescriptionRes = firstPageDetailsRes,
                // Ordered as a pitch, not as a grid: what the app opens, then where it reads from,
                // then what it does with it. Rendered as a one-column list on a phone.
                // S1389: the set answers to the build's own capabilities - see WelcomeFeatureCards.
                featureCards = WelcomeFeatureCards.build(
                    capabilities = mediaCapabilities,
                    launcherAvailable = shellPitch,
                    streamsAvailable = streamsInPitch,
                    wearAvailable = wearInPitch,
                ),
                showLanguagePicker = true,
                onLanguagePickerRequested = ::showWelcomeLanguagePicker,
                showThemePicker = true,
                onThemeSelected = ::onWelcomeThemeSelected,
                showLauncherModeToggle = shellPitch,
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
                // S1383: tapping the already-selected tile means "this one, go on" - the same step
                // Next would take, so the profile page needs no separate confirm control.
                onProfileConfirmed = { type ->
                    viewModel.onProfileSelected(type)
                    tvNavigation.flipPage(forward = true)
                },
            )
        )

        // Networks page. Three remote-source group toggles (SMB / (S)FTP / Cloud);
        // WelcomeRemoteSourcesController owns all logic and binds via the page callback. The cloud
        // toggle collapses on flavors without cloud support (decided inside the controller via the gate).
        // S1388: a build with neither remote group has nothing to offer here - the controller hides
        // every row and the page renders its header ("Add media from network shares and remote
        // storage") over an empty body, promising what the build cannot do. Left out entirely
        // instead, the same way the default-player page below is gated.
        val shouldShowNetworksPage =
            mediaCapabilities.supportsLocalNetworkSources || mediaCapabilities.supportsCloud
        if (shouldShowNetworksPage) {
            pagesList.add(
                WelcomePage(
                    iconRes = 0,
                    titleRes = 0,
                    descriptionRes = 0,
                    isNetworksPage = true,
                    onBindNetworks = { b -> remoteSourcesController.bind(b, this) },
                )
            )
        }

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
            (
                !viewModel.isDefaultPlayerOnboardingShown() ||
                    !DefaultPlayerHelper.isAlreadyDefaultPlayer(this)
                )

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

        // S1377: a fresh adapter is the only way the page layouts get inflated again and the
        // width-qualified values re-read, because this Activity is deliberately not recreated on
        // rotation. Dropped on the destroy edge in observeBrandBackdrop's lifecycle observer.
        rotationManager = com.sza.fastmediasorter.ui.welcome.helpers.WelcomeRotationManager(
            initialOrientation = resources.configuration.orientation,
            currentPageProvider = { binding.viewPager.currentItem },
            onOrientationChanged = { page ->
                pagerAdapter = WelcomePagerAdapter(pagesList, mediaCapabilities)
                binding.viewPager.adapter = pagerAdapter
                binding.viewPager.setCurrentItem(page, false)
                currentPage = page
                previousPage = page
                val state = viewModel.state.value
                pagerAdapter.refreshProfiles(state.recommendedProfile, state.selectedProfile)
                setupIndicators(pagesList.size)
                updateUI()
            },
        )
        registerComponentCallbacks(rotationManager)
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
                    if (isActive) {
                        R.drawable.indicator_active
                    } else {
                        R.drawable.indicator_inactive
                    }
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

        // S0409: one-tap full setup - enables everything, walks permission + default-player dialogs,
        // then finishes, skipping the remaining pages. S2311: it now opens the profile picker first and
        // runs from its result, so an unlucky auto-detection is visible and correctable before anything
        // is applied.
        binding.btnEnableAll.setOnClickListener {
            // A second tap while the explainer is up would stack a duplicate showing the same text.
            if (supportFragmentManager.findFragmentByTag(TAG_ENABLE_ALL_EXPLAINER) != null) {
                return@setOnClickListener
            }
            // S2322: explain the chain of system screens before the profile picker, because the
            // picker's own tap already applies settings - after it, an explanation would describe
            // something the user has agreed to.
            WelcomeEnableAllExplainerDialogFragment.newInstance(
                mode = WelcomeEnableAllExplainerDialogFragment.Mode.OVERVIEW,
                requestKey = REQUEST_KEY_ENABLE_ALL_EXPLAINER,
            ).show(supportFragmentManager, TAG_ENABLE_ALL_EXPLAINER)
        }
    }

    /**
     * The three fragment results the "Enable all" path answers with: the explainer overview, the
     * device-profile confirmation, and the just-in-time default-app reminder.
     *
     * All three belong to the Activity rather than to a tap, because each dialog outlives the
     * recreation this screen performs routinely (theme and language picks). They live in their own
     * function only because setupViews would otherwise exceed detekt's LongMethod ceiling - measured,
     * not assumed: with them inline the function reached 82 lines against a limit of 80.
     */
    private fun registerEnableAllResultListeners() {
        // S2311: the enable-all shortcut confirms the device profile before it applies anything, and
        // no result at all (Back / tap-outside) deliberately applies nothing.
        supportFragmentManager.setFragmentResultListener(
            REQUEST_KEY_ENABLE_ALL_PROFILE,
            this
        ) { _, bundle ->
            val type = bundle.getString(DeviceProfilePickerDialogFragment.RESULT_PROFILE)
                ?.let { name -> runCatching { DeviceProfileType.valueOf(name) }.getOrNull() }
                ?: return@setFragmentResultListener
            enableAllManager.start { viewModel.applyProfileForEnableAll(type) }
        }

        // S2322: the overview is answered before anything is applied, so declining it must leave the
        // profile picker unopened - opening the picker is what commits the user (S2311 ADR-3).
        supportFragmentManager.setFragmentResultListener(
            REQUEST_KEY_ENABLE_ALL_EXPLAINER,
            this
        ) { _, bundle ->
            val proceed = bundle.getBoolean(
                WelcomeEnableAllExplainerDialogFragment.RESULT_PROCEED,
                false
            )
            val pickerUp = supportFragmentManager.findFragmentByTag(TAG_ENABLE_ALL_PROFILE) != null
            if (proceed && !pickerUp) {
                val state = viewModel.state.value
                // warnOnApply is false: this is first-run onboarding, so there are no tuned settings
                // for that warning to protect.
                DeviceProfilePickerDialogFragment.newInstance(
                    current = state.selectedProfile
                        ?: state.recommendedProfile
                        ?: DeviceProfileType.PERSONAL_SMARTPHONE,
                    recommended = state.recommendedProfile,
                    warnOnApply = false,
                    requestKey = REQUEST_KEY_ENABLE_ALL_PROFILE,
                ).show(supportFragmentManager, TAG_ENABLE_ALL_PROFILE)
            }
        }

        // S2322: declining the just-in-time reminder drops the whole default-app stage, so no
        // unexplained "Open with" sheet is ever raised over the user's files.
        supportFragmentManager.setFragmentResultListener(
            REQUEST_KEY_ENABLE_ALL_DEFAULT_APP,
            this
        ) { _, bundle ->
            val proceed = bundle.getBoolean(
                WelcomeEnableAllExplainerDialogFragment.RESULT_PROCEED,
                false
            )
            if (proceed) {
                enableAllManager.onDefaultPlayerStageConfirmed()
            } else {
                enableAllManager.onDefaultPlayerStageDeclined()
            }
        }
    }

    private fun updateUI() {
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

    /**
     * S1190: the interface language is chosen from the same searchable picker the settings screen uses,
     * so the Welcome page no longer caps the choice at the three languages a button strip could hold.
     */
    private fun showWelcomeLanguagePicker() {
        // A second tap while the picker is already up would stack a duplicate showing the same choice.
        if (supportFragmentManager.findFragmentByTag(SearchableLanguagePickerDialog.TAG) != null) return
        SearchableLanguagePickerDialog.newInstanceForUiLanguage(LocaleHelper.getLanguage(this))
            .show(supportFragmentManager, SearchableLanguagePickerDialog.TAG)
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
            // The opt-in outlives this frame as a durable flag rather than an intent extra: the first-run
            // Settings screen is recreated while theme and locale are applied, and an extra consumed by a
            // doomed instance took the user's choice with it.
            launcherRoleManager.markRoleRequestPending()
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
                SettingsActivity.openLauncherSectionIntent(this)
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

    private fun getRequiredMediaPermissions(): Array<String> = StoragePermissionRule.requiredPermissions()

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
            val nav = tvNavigation
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    nav.handleHorizontal(View.FOCUS_LEFT, forward = false)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    nav.handleHorizontal(View.FOCUS_RIGHT, forward = true)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    nav.handleVertical(View.FOCUS_UP)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    nav.handleVertical(View.FOCUS_DOWN)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> if (nav.handleSelect()) return true
                KeyEvent.KEYCODE_TAB -> if (nav.handleSequential(forward = !event.isShiftPressed)) return true
                KeyEvent.KEYCODE_ESCAPE -> {
                    onBackPressedDispatcher.onBackPressed()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * BACK is the only slider key still routed through TvKeyRouter; D-pad / ENTER / TAB / ESC are
     * owned by [dispatchKeyEvent] above.
     */
    override fun onTvNavigation(action: TvNavAction): Boolean = when (action) {
        TvNavAction.Back -> {
            onBackPressedDispatcher.onBackPressed()
            true
        }
        else -> false
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

        // S2311: own result key so the enable-all confirmation cannot collide with the Settings screen's
        // use of the same picker.
        private const val REQUEST_KEY_ENABLE_ALL_PROFILE = "welcome_enable_all_profile_result"
        private const val TAG_ENABLE_ALL_PROFILE = "WelcomeEnableAllProfilePicker"

        // S2322: the two explainer surfaces carry their own keys because both are answered by the
        // same fragment class - one shared key would let the overview's answer start the
        // default-app stage.
        private const val REQUEST_KEY_ENABLE_ALL_EXPLAINER = "welcome_enable_all_explainer_result"
        private const val TAG_ENABLE_ALL_EXPLAINER = "WelcomeEnableAllExplainer"
        private const val REQUEST_KEY_ENABLE_ALL_DEFAULT_APP = "welcome_enable_all_default_app_result"
        private const val TAG_ENABLE_ALL_DEFAULT_APP = "WelcomeEnableAllDefaultAppPrompt"
    }
}
