package com.sza.fastmediasorter.ui.welcome

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
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
    private var permissionsGranted = false

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
        updateIndicators()

        val isLastPage = currentPage == pagerAdapter.itemCount - 1
        val isFirstPage = currentPage == 0
        val isPermissionPage = pagerAdapter.getItemViewType(currentPage) == 2 // VIEW_TYPE_PERMISSIONS

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

    private fun finishWelcome() {
        // If user skips onboarding pages and storage permission is still missing,
        // request it immediately before entering the app.
        if (!PermissionHelper.hasStoragePermission(this)) {
            requestPermissions()
            return
        }

        checkAndFinish()
    }

    private fun requestPermissions() {
        // Request storage permission first
        if (!PermissionHelper.hasStoragePermission(this)) {
            // Direct request for modern flow (we are on the explanation page)
            PermissionHelper.requestStoragePermission(this)
        } else {
            // Storage already granted, check MANAGE_MEDIA
            requestManageMediaIfNeeded()
        }
    }

    private fun requestManageMediaIfNeeded() {
        // For Android 12+, offer MANAGE_MEDIA for one-tap file operations
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && 
            !PermissionHelper.hasManageMediaPermission(this)) {
                // Direct request
                PermissionHelper.requestManageMediaPermission(this)
        } else {
            // Either already granted or not available, finish
            permissionsGranted = true
            Toast.makeText(this, R.string.permissions_granted, Toast.LENGTH_SHORT).show()
            
            // Auto finish after granting?
            // Or user clicks "Finish" / "Start App"
            viewModel.setWelcomeCompleted()
            restartApp()
        }
    }

    // kept for legacy or if we want to use dialogs elsewhere, but simplified here
    private fun showPermissionDialog(
        title: String,
        message: String,
        onGrant: () -> Unit,
        onSkip: () -> Unit
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.grant_permission) { _, _ ->
                onGrant()
            }
            .setNegativeButton(R.string.skip_permission) { _, _ ->
                onSkip()
            }
            .setCancelable(false)
            .show()
    }

    private fun checkAndFinish() {
         // Check permissions one last time
        if (PermissionHelper.hasStoragePermission(this)) {
             permissionsGranted = true
        }

        if (permissionsGranted) {
            viewModel.setWelcomeCompleted()
            // Permissions were granted, restart app
             // Only show toast if we didn't show it during grant
             // Toast.makeText(this, R.string.permissions_granted, Toast.LENGTH_SHORT).show()
            restartApp()
        } else {
            // No permissions granted, just go to MainActivity
            goToMainActivity()
        }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            PermissionHelper.REQUEST_CODE_MANAGE_STORAGE -> {
                if (PermissionHelper.hasStoragePermission(this)) {
                    permissionsGranted = true
                    // Proceed to next permission or finish
                    checkAndFinish()
                }
            }
            PermissionHelper.REQUEST_CODE_MANAGE_MEDIA -> {
                requestManageMediaIfNeeded()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            PermissionHelper.REQUEST_CODE_STORAGE -> {
                if (PermissionHelper.hasStoragePermission(this)) {
                    // permissionsGranted = true // Don't set yet, check next logic
                    // After storage permission, check MANAGE_MEDIA
                    requestManageMediaIfNeeded()
                }
            }
        }
    }
}
