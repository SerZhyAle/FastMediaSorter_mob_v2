package com.sza.fastmediasorter_v2.ui.welcome

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter_v2.databinding.PageWelcomeBinding

class WelcomePagerAdapter(
    private val pages: List<WelcomePage>
) : RecyclerView.Adapter<WelcomePagerAdapter.WelcomeViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WelcomeViewHolder {
        val binding = PageWelcomeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WelcomeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WelcomeViewHolder, position: Int) {
        holder.bind(pages[position])
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
}

data class WelcomePage(
    val iconRes: Int,
    val titleRes: Int,
    val descriptionRes: Int
)
