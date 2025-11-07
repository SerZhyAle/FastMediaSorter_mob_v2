package com.sza.fastmediasorter_v2.ui.welcome

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.sza.fastmediasorter_v2.R
import com.sza.fastmediasorter_v2.databinding.ActivityWelcomeBinding
import com.sza.fastmediasorter_v2.core.ui.BaseActivity
import com.sza.fastmediasorter_v2.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WelcomeActivity : BaseActivity<ActivityWelcomeBinding>() {

    private val viewModel: WelcomeViewModel by viewModels()

    private lateinit var pagerAdapter: WelcomePagerAdapter
    private var currentPage = 0

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
                iconRes = R.mipmap.ic_launcher,
                titleRes = R.string.welcome_title_2,
                descriptionRes = R.string.welcome_description_2
            ),
            WelcomePage(
                iconRes = R.mipmap.ic_launcher,
                titleRes = R.string.welcome_title_3,
                descriptionRes = R.string.welcome_description_3
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
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
