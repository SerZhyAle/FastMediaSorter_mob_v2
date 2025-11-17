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
        val pages = listOf(
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

        binding.btnPrevious.visibility = if (isFirstPage) View.GONE else View.VISIBLE
        binding.btnNext.visibility = if (isLastPage) View.GONE else View.VISIBLE
        binding.btnFinish.visibility = if (isLastPage) View.VISIBLE else View.GONE
    }

    private fun finishWelcome() {
        viewModel.setWelcomeCompleted()
        requestPermissions()
    }

    private fun requestPermissions() {
        // Request storage permission first
        if (!PermissionHelper.hasStoragePermission(this)) {
            showPermissionDialog(
                title = getString(R.string.permission_storage_title),
                message = PermissionHelper.getStoragePermissionMessage(this),
                onGrant = {
                    PermissionHelper.requestStoragePermission(this)
                },
                onSkip = {
                    // Storage permission skipped, check internet permission
                    checkAndFinish()
                }
            )
        } else {
            // Storage already granted, check and finish
            checkAndFinish()
        }
    }

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
        if (permissionsGranted) {
            // Permissions were granted, restart app
            Toast.makeText(this, R.string.permissions_granted, Toast.LENGTH_SHORT).show()
            restartApp()
        } else {
            // No permissions granted, just go to MainActivity
            goToMainActivity()
        }
    }

    private fun goToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
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
                }
                checkAndFinish()
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
                    permissionsGranted = true
                }
                checkAndFinish()
            }
        }
    }
}
