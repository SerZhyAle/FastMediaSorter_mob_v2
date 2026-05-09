package com.sza.fastmediasorter.ui.welcome

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.viewpager2.widget.ViewPager2
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.databinding.ActivityWelcomeBinding
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.ui.settings.fragments.PermissionsManagementFragment
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerHelper
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerManager
import com.sza.fastmediasorter.BuildConfig
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class WelcomeActivity : BaseActivity<ActivityWelcomeBinding>(), PermissionsManagementFragment.WelcomeCompleteListener {

    private val viewModel: WelcomeViewModel by viewModels()

    private lateinit var pagerAdapter: WelcomePagerAdapter
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
        // the app — WelcomeActivity is the root task at this point so a naked finish() would exit.
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

        setupViewPager()
        setupButtons()
        updateUI()
    }

    override fun observeData() {
        // No data to observe
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { state ->
            restoredPage = state.getInt(KEY_CURRENT_PAGE, 0)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_CURRENT_PAGE, currentPage)
    }

    private fun applyEdgeToEdgeInsets() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            applyWindowInsets(insets)
            insets
        }
        // setupViews() runs inside post{} — the first insets dispatch has already happened.
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

        val marginSmall = resources.getDimensionPixelSize(R.dimen.margin_small)
        val endInset = maxOf(statusBar.right, cutout.right, navBar.right)

        val topNav = binding.layoutTopNav
        if (topNav != null) {
            // sw480dp / sw720dp: btnSkip lives inside layoutTopNav — push the whole bar below
            // the status bar by adjusting container padding so all buttons stay aligned.
            topNav.setPadding(
                topNav.paddingLeft,
                statusBar.top + marginSmall,
                endInset + marginSmall,
                topNav.paddingBottom
            )
        } else {
            // Default layout: btnSkip is a standalone element constrained to top/end of root.
            (binding.btnSkip.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let {
                it.topMargin = statusBar.top + marginSmall
                it.marginEnd = endInset + marginSmall
                binding.btnSkip.layoutParams = it
            }
        }

        // Bottom nav above navigation bar
        binding.layoutBottomNav?.setPadding(
            binding.layoutBottomNav?.paddingLeft ?: 0,
            binding.layoutBottomNav?.paddingTop ?: 0,
            binding.layoutBottomNav?.paddingRight ?: 0,
            navBar.bottom
        )
    }

    private fun setupViewPager() {
        val pages = mutableListOf(
            // Page 1: Welcome (Enhanced with feature cards)
            WelcomePage(
                iconRes = R.drawable.welcome_hero_media,
                titleRes = R.string.welcome_title_1,
                descriptionRes = R.string.welcome_description_1,
                featureCards = listOf(
                    FeatureCard(R.drawable.ic_image, R.string.welcome_feature_photos),
                    FeatureCard(R.drawable.ic_resource_cloud, R.string.welcome_feature_cloud),
                    FeatureCard(R.drawable.ic_swap_horizontal, R.string.welcome_feature_sorting)
                ),
                showLanguagePicker = true,
                onLanguageSelected = ::onWelcomeLanguageSelected,
            ),
            // Page 2: Resource Types
            WelcomePage(
                iconRes = R.drawable.resource_types,
                titleRes = R.string.welcome_title_2,
                descriptionRes = R.string.welcome_description_2
            ),
            // Page 3: Touch Zones
            WelcomePage(
                iconRes = R.mipmap.ic_launcher,
                titleRes = R.string.welcome_title_3,
                descriptionRes = R.string.welcome_description_3,
                showTouchZonesScheme = true
            ),
            // Page 4: Resources & Destinations
            WelcomePage(
                iconRes = R.drawable.destinations,
                titleRes = R.string.welcome_title_4,
                descriptionRes = R.string.welcome_description_4
            ),
            // Page 5: Additional Features (Enhanced with feature cards)
            WelcomePage(
                iconRes = R.drawable.welcome_hero_features,
                titleRes = R.string.welcome_title_5,
                descriptionRes = R.string.welcome_description_5,
                featureCards = listOf(
                    FeatureCard(R.drawable.ic_ocr, R.string.welcome_feature_ocr),
                    FeatureCard(R.drawable.ic_audio, R.string.welcome_feature_audio),
                    FeatureCard(R.drawable.ic_book, R.string.welcome_feature_ebook)
                )
            ),
        )

        // VR-exclusive page: 3D/VR capabilities (only in VR flavor)
        if (BuildConfig.SUPPORT_VR_PLAYER) {
            pages.add(
                WelcomePage(
                    iconRes = R.drawable.welcome_hero_vr,
                    titleRes = R.string.welcome_vr_title,
                    descriptionRes = R.string.welcome_vr_description,
                    featureCards = listOf(
                        FeatureCard(R.drawable.ic_stereo_3d, R.string.welcome_vr_feature_stereo),
                        FeatureCard(R.drawable.ic_vr_headset, R.string.welcome_vr_feature_headset),
                        FeatureCard(R.drawable.ic_vr_formats, R.string.welcome_vr_feature_formats)
                    )
                )
            )
            pageBackgrounds.add(R.color.welcome_page_8_background)
        }

        // Page 6 (first install only): Default Player onboarding
        // markDefaultPlayerOnboardingShown() is called in onPageSelected() when the user reaches
        // this page — so skipping welcome doesn't suppress future display.
        val shouldShowDefaultPlayerPage = BuildConfig.SUPPORTS_DEFAULT_PLAYER &&
            (!viewModel.isDefaultPlayerOnboardingShown() ||
                !DefaultPlayerHelper.isAlreadyDefaultPlayer(this))

        if (shouldShowDefaultPlayerPage) {
            defaultPlayerPageIndex = pages.size
            pages.add(
                WelcomePage(
                    iconRes = 0,
                    titleRes = 0,
                    descriptionRes = 0,
                    isDefaultPlayerPage = true,
                    onSetDefaultForTypeClick = { mimeType ->
                        // Enable aliases synchronously first — the system must see the alias
                        // as enabled before it will consider the app eligible for ROLE_MUSIC.
                        DefaultPlayerManager.applyPrimaryPlayerState(this, true)
                        viewModel.enablePrimaryMediaPlayer() // persist to DataStore
                        DefaultPlayerHelper.openChooserOrFallbackFromActivity(this, mimeType)
                    }
                )
            )
        }

        pagerAdapter = WelcomePagerAdapter(pages)
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

        setupIndicators(pages.size)

        // Restore ViewPager position after activity recreation so the user lands on the
        // same page they were on before the config change, not always back on page 0.
        if (restoredPage > 0 && restoredPage < pages.size) {
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
        binding.btnSkip.setOnClickListener {
            // Phase 4: "not suppressed on first launch" — if the default player card exists
            // and the user hasn't seen it yet, redirect to it instead of finishing.
            // onPageSelected() will mark it as shown; subsequent Skip presses call finishWelcome().
            if (defaultPlayerPageIndex != -1 && currentPage < defaultPlayerPageIndex) {
                binding.viewPager.currentItem = defaultPlayerPageIndex
            } else {
                finishWelcome()
            }
        }

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
            finishWelcome()
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
            // Only show finish if it's NOT the permission page (which has its own grant button)
            // But wait, if last page IS permission page, we still need a way to continue if already granted?
            // Or maybe check if permissions are granted?
            // Actually, for simplicity, let's keep Finish button but maybe text it "Start" if permissions granted
            // For now, if it is permission page, we rely on the Grant button inside the page. 
            // BUT, if user already has permissions, we should probably auto-skip or show "Continue".
            // Let's just show Finish button if it's NOT permission page, or if it is but maybe we want to allow skip?
            // The "Skip" button at top handles skipping.
            binding.btnFinish.visibility = View.VISIBLE
        } else {
            binding.btnNext.visibility = View.VISIBLE
            binding.btnFinish.visibility = View.GONE
        }
        
        // Hide "Skip" on last page
         binding.btnSkip.visibility = if (isLastPage) View.INVISIBLE else View.VISIBLE
    }

    private fun applyPageBackground() {
        val backgroundIndex = currentPage.coerceIn(0, pageBackgrounds.lastIndex)
        binding.root.setBackgroundResource(pageBackgrounds[backgroundIndex])
    }

    private fun onWelcomeLanguageSelected(code: String) {
        LocaleHelper.saveLanguage(this, code)
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            // API < 33: LocaleManager is unavailable — recreate Activity manually.
            // overridePendingTransition(0, 0) called after recreate() suppresses the
            // default enter animation of the new Activity instance.
            recreate()
            overridePendingTransition(0, 0)
        }
        // API 33+: LocaleManager already called in saveLanguage() will recreate the
        // Activity automatically with no further action needed here.
    }

    private fun finishWelcome() {
        binding.fragmentContainerWelcome.visibility = View.VISIBLE
        binding.viewPager.visibility = View.GONE
        binding.layoutBottomNav?.visibility = View.GONE
        binding.layoutTopNav?.visibility = View.GONE
        binding.layoutIndicator?.visibility = View.GONE
        binding.btnSkip.visibility = View.GONE
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container_welcome, PermissionsManagementFragment.newInstance(fromWelcome = true))
            .commit()
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

            // Show toast message
            Toast.makeText(this, R.string.setup_content_first_toast, Toast.LENGTH_LONG).show()

            // Navigate to Settings with MainActivity as the back-stack root so that
            // pressing Back from Settings returns to MainActivity instead of closing the app.
            TaskStackBuilder.create(this)
                .addNextIntent(Intent(this, MainActivity::class.java))
                .addNextIntent(Intent(this, SettingsActivity::class.java))
                .startActivities()
            finish()
        } else {
            // Normal navigation to MainActivity
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
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

    companion object {
        private const val TAG = "WelcomePerms"
        private const val KEY_CURRENT_PAGE = "key_current_page"
    }
}
