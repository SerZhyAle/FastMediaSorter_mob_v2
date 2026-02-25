package com.sza.fastmediasorter.ui.welcome

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.PageWelcomeBinding
import com.sza.fastmediasorter.databinding.PageWelcomeEnhancedBinding
import com.sza.fastmediasorter.databinding.PageWelcomePermissionsBinding
import com.sza.fastmediasorter.databinding.PageWelcomeTouchZonesBinding

class WelcomePagerAdapter(
    private val pages: List<WelcomePage>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_NORMAL = 0
        private const val VIEW_TYPE_TOUCH_ZONES = 1
        private const val VIEW_TYPE_PERMISSIONS = 2
        private const val VIEW_TYPE_ENHANCED = 3
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            pages[position].isPermissionsPage -> VIEW_TYPE_PERMISSIONS
            pages[position].showTouchZonesScheme -> VIEW_TYPE_TOUCH_ZONES
            pages[position].featureCards.isNotEmpty() -> VIEW_TYPE_ENHANCED
            else -> VIEW_TYPE_NORMAL
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_TOUCH_ZONES -> {
                val binding = PageWelcomeTouchZonesBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                TouchZonesViewHolder(binding)
            }
            VIEW_TYPE_PERMISSIONS -> {
                val binding = PageWelcomePermissionsBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                PermissionsViewHolder(binding)
            }
            VIEW_TYPE_ENHANCED -> {
                val binding = PageWelcomeEnhancedBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                EnhancedViewHolder(binding)
            }
            else -> {
                val binding = PageWelcomeBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                WelcomeViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is TouchZonesViewHolder -> holder.bind(pages[position])
            is PermissionsViewHolder -> holder.bind(pages[position])
            is EnhancedViewHolder -> holder.bind(pages[position])
            is WelcomeViewHolder -> holder.bind(pages[position])
        }
    }

    override fun getItemCount(): Int = pages.size

    class WelcomeViewHolder(
        private val binding: PageWelcomeBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(page: WelcomePage) {
            binding.ivIcon.setImageResource(page.iconRes)
            binding.tvTitle.text = binding.root.context.getString(page.titleRes)
            binding.tvDescription.text = binding.root.context.getString(page.descriptionRes)

            // Apply staggered entrance animations
            animateEntrance(binding.ivIcon, 0L)
            animateEntrance(binding.tvTitle, 150L)
            animateEntrance(binding.tvDescription, 300L)
        }
    }

    class TouchZonesViewHolder(
        private val binding: PageWelcomeTouchZonesBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(page: WelcomePage) {
            binding.tvTitle.text = binding.root.context.getString(page.titleRes)
            binding.tvDescription.text = binding.root.context.getString(page.descriptionRes)

            animateEntrance(binding.tvTitle, 0L)
            animateEntrance(binding.ivTouchZonesScheme, 150L)
            animateEntrance(binding.tvDescription, 300L)
        }
    }

    class PermissionsViewHolder(
        private val binding: PageWelcomePermissionsBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(page: WelcomePage) {
            binding.btnGrant.setOnClickListener {
                page.onGrantClick?.invoke()
            }

            animateEntrance(binding.ivIcon, 0L)
            animateEntrance(binding.tvTitle, 150L)
            animateEntrance(binding.btnGrant, 300L)
        }
    }

    class EnhancedViewHolder(
        private val binding: PageWelcomeEnhancedBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(page: WelcomePage) {
            val context = binding.root.context
            binding.ivIcon.setImageResource(page.iconRes)
            binding.tvTitle.text = context.getString(page.titleRes)
            binding.tvDescription.text = context.getString(page.descriptionRes)

            // Bind feature cards
            if (page.featureCards.size >= 3) {
                val card1 = page.featureCards[0]
                val card2 = page.featureCards[1]
                val card3 = page.featureCards[2]

                binding.ivFeature1.setImageResource(card1.iconRes)
                binding.tvFeature1.text = context.getString(card1.labelRes)

                binding.ivFeature2.setImageResource(card2.iconRes)
                binding.tvFeature2.text = context.getString(card2.labelRes)

                binding.ivFeature3.setImageResource(card3.iconRes)
                binding.tvFeature3.text = context.getString(card3.labelRes)
            }

            // Staggered entrance animations
            animateEntrance(binding.ivIcon, 0L)
            animateEntrance(binding.tvTitle, 150L)
            animateEntrance(binding.tvDescription, 250L)
            animateEntrance(binding.layoutFeatureCards, 400L)
        }
    }
}

/**
 * Helper function to apply fade+slide-up entrance animation with a start delay.
 */
private fun animateEntrance(view: View, delayMs: Long) {
    val anim = AnimationUtils.loadAnimation(view.context, R.anim.welcome_fade_slide_up)
    anim.startOffset = delayMs
    view.startAnimation(anim)
}

data class WelcomePage(
    val iconRes: Int,
    val titleRes: Int,
    val descriptionRes: Int,
    val showTouchZonesScheme: Boolean = false,
    val isPermissionsPage: Boolean = false,
    val onGrantClick: (() -> Unit)? = null,
    val featureCards: List<FeatureCard> = emptyList()
)

data class FeatureCard(
    val iconRes: Int,
    val labelRes: Int
)
