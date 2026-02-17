package com.sza.fastmediasorter.ui.welcome

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.databinding.ActivityWelcomeBinding
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WelcomeActivity : BaseActivity<ActivityWelcomeBinding>() {

    private val viewModel: WelcomeViewModel by viewModels()

    private lateinit var pagerAdapter: WelcomePagerAdapter
    private var currentPage = 0
    private var waitingPermissionForFinish = false
    private var hasTriggeredLastPagePermissionRequest = false
    private var hasRequestedManageMediaInSession = false
    private var hasRequestedAllFilesAccessInSession = false
    private val mediaPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted || hasRequiredMediaPermissions()) {
            onRuntimePermissionsProcessed()
        } else {
            showPermissionDeniedDialog()
        }
    }
    private val manageMediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        continueSpecialPermissionsFlowOrComplete()
    }
    private val allFilesAccessPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        continueSpecialPermissionsFlowOrComplete()
    }
    private val pageBackgrounds = intArrayOf(
        R.color.welcome_page_1_background,
        R.color.welcome_page_2_background,
        R.color.welcome_page_3_background,
        R.color.welcome_page_4_background,
        R.color.welcome_page_5_background,
        R.color.welcome_page_6_background
    )

    override fun getViewBinding(): ActivityWelcomeBinding =
        ActivityWelcomeBinding.inflate(layoutInflater)

    override fun setupViews() {
        setupViewPager()
        setupButtons()
        updateUI()
    }

    override fun observeData() {
        // No data to observe
    }

    private fun setupViewPager() {
        val pages = mutableListOf(
            WelcomePage(
                iconRes = R.mipmap.ic_launcher,
                titleRes = R.string.welcome_title_1,
                descriptionRes = R.string.welcome_description_1
            ),
            WelcomePage(
                iconRes = R.drawable.resource_types,
                titleRes = R.string.welcome_title_2,
                descriptionRes = R.string.welcome_description_2
            ),
            WelcomePage(
                iconRes = R.mipmap.ic_launcher,
                titleRes = R.string.welcome_title_3,
                descriptionRes = R.string.welcome_description_3,
                showTouchZonesScheme = true
            ),
            WelcomePage(
                iconRes = R.drawable.destinations,
                titleRes = R.string.welcome_title_4,
                descriptionRes = R.string.welcome_description_4
            ),
            WelcomePage(
                iconRes = R.mipmap.ic_launcher,
                titleRes = R.string.welcome_title_5,
                descriptionRes = R.string.welcome_description_5
            ),
            // Permissions Page
            WelcomePage(
                iconRes = 0, // Not used for permission page type
                titleRes = 0, // Not used
                descriptionRes = 0, // Not used
                isPermissionsPage = true,
                onGrantClick = { requestPermissions() }
            )
        )

        pagerAdapter = WelcomePagerAdapter(pages)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                updateUI()
            }
        })

        setupIndicators(pages.size)
    }

    private fun setupIndicators(count: Int) {
        binding.layoutIndicator.removeAllViews()
        val indicatorSize = resources.getDimensionPixelSize(R.dimen.indicator_size)
        val indicatorMargin = resources.getDimensionPixelSize(R.dimen.indicator_margin)

        for (i in 0 until count) {
            val indicator = View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    indicatorSize,
                    indicatorSize
                ).apply {
                    setMargins(indicatorMargin, 0, indicatorMargin, 0)
                }
                setBackgroundResource(R.drawable.indicator_inactive)
            }
            binding.layoutIndicator.addView(indicator)
        }

        updateIndicators()
    }

    private fun updateIndicators() {
        for (i in 0 until binding.layoutIndicator.childCount) {
            val indicator = binding.layoutIndicator.getChildAt(i)
            indicator.setBackgroundResource(
                if (i == currentPage) R.drawable.indicator_active
                else R.drawable.indicator_inactive
            )
        }
    }

    private fun setupButtons() {
        binding.btnSkip.setOnClickListener {
            finishWelcome()
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

            if (!hasTriggeredLastPagePermissionRequest) {
                hasTriggeredLastPagePermissionRequest = true
                requestPermissions()
            }
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

    private fun finishWelcome() {
        waitingPermissionForFinish = true
        requestPermissions()
    }

    private fun requestPermissions() {
        val requiredPermissions = getRequiredMediaPermissions()
        if (requiredPermissions.isEmpty() || hasRequiredMediaPermissions()) {
            onRuntimePermissionsProcessed()
            return
        }

        mediaPermissionsLauncher.launch(requiredPermissions)
    }

    private fun onRuntimePermissionsProcessed() {
        if (hasRequiredMediaPermissions()) {
            viewModel.setMediaPermissionsGranted(true)
            Toast.makeText(this, R.string.permissions_granted, Toast.LENGTH_SHORT).show()
        }

        continueSpecialPermissionsFlowOrComplete()
    }

    private fun continueSpecialPermissionsFlowOrComplete() {
        if (requestManageMediaPermissionIfNeeded()) {
            return
        }

        if (requestAllFilesAccessPermissionIfNeeded()) {
            return
        }

        if (waitingPermissionForFinish || currentPage == pagerAdapter.itemCount - 1) {
            waitingPermissionForFinish = false
            completeWelcomeFlow()
        }
    }

    private fun requestManageMediaPermissionIfNeeded(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false
        }

        if (PermissionHelper.hasManageMediaPermission(this) || hasRequestedManageMediaInSession) {
            return false
        }

        hasRequestedManageMediaInSession = true

        val intent = try {
            Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
                data = Uri.parse("package:$packageName")
            }
        } catch (_: Exception) {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        }

        manageMediaPermissionLauncher.launch(intent)
        return true
    }

    private fun requestAllFilesAccessPermissionIfNeeded(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false
        }

        if (PermissionHelper.hasAllFilesAccessPermission(this) || hasRequestedAllFilesAccessInSession) {
            return false
        }

        hasRequestedAllFilesAccessInSession = true

        val intent = try {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
        } catch (_: Exception) {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }

        allFilesAccessPermissionLauncher.launch(intent)
        return true
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permissions_denied_title)
            .setMessage(R.string.permissions_denied_warning)
            .setPositiveButton(R.string.retry) { _, _ ->
                requestPermissions()
            }
            .setNegativeButton(R.string.continue_anyway) { _, _ ->
                continueSpecialPermissionsFlowOrComplete()
            }
            .setCancelable(true)
            .show()
    }

    private fun completeWelcomeFlow() {
        if (hasRequiredMediaPermissions()) {
            viewModel.setMediaPermissionsGranted(true)
            viewModel.setWelcomeCompleted()
            restartApp()
            return
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
            
            // Navigate to Settings
            val intent = Intent(this, SettingsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } else {
            // Normal navigation to MainActivity
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun restartApp() {
        LocaleHelper.restartApp(this)
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

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
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
}
