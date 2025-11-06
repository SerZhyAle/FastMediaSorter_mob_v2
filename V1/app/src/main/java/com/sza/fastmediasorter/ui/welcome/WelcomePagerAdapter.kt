package com.sza.fastmediasorter.ui.welcome

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R

class WelcomePagerAdapter(
    private val pages: List<WelcomePage>,
) : RecyclerView.Adapter<WelcomePagerAdapter.PageViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): PageViewHolder {
        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.welcome_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: PageViewHolder,
        position: Int,
    ) {
        holder.bind(pages[position])
    }

    override fun getItemCount(): Int = pages.size

    class PageViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val icon: TextView = itemView.findViewById(R.id.pageIcon)
        private val title: TextView = itemView.findViewById(R.id.pageTitle)
        private val description: TextView = itemView.findViewById(R.id.pageDescription)
        private val featuresList: ViewGroup = itemView.findViewById(R.id.featuresList)

        fun bind(page: WelcomePage) {
            icon.text = page.icon
            title.text = page.title
            description.text = page.description

            featuresList.removeAllViews()
            page.features.forEach { feature ->
                val featureView =
                    TextView(itemView.context).apply {
                        text = "• $feature"
                        textSize = 14f
                        setTextColor(itemView.context.getColor(android.R.color.white))
                        setPadding(0, 8, 0, 8)
                    }
                featuresList.addView(featureView)
            }
        }
    }
}
