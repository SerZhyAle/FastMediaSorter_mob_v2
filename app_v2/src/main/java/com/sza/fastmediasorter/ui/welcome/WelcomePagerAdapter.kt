package com.sza.fastmediasorter.ui.welcome

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.databinding.PageWelcomeBinding
import com.sza.fastmediasorter.databinding.PageWelcomeTouchZonesBinding

class WelcomePagerAdapter(
    private val pages: List<WelcomePage>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_NORMAL = 0
        private const val VIEW_TYPE_TOUCH_ZONES = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (pages[position].showTouchZonesScheme) VIEW_TYPE_TOUCH_ZONES else VIEW_TYPE_NORMAL
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
        }
    }

    class TouchZonesViewHolder(
        private val binding: PageWelcomeTouchZonesBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(page: WelcomePage) {
            binding.tvTitle.text = binding.root.context.getString(page.titleRes)
            binding.tvDescription.text = binding.root.context.getString(page.descriptionRes)
        }
    }
}

data class WelcomePage(
    val iconRes: Int,
    val titleRes: Int,
    val descriptionRes: Int,
    val showTouchZonesScheme: Boolean = false
)
