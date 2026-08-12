package com.sza.fastmediasorter.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemOpenSourceLicenseBinding

class OpenSourceLicenseAdapter(
    private val notices: List<Notice>,
    private val openUrl: (String) -> Unit
) : RecyclerView.Adapter<OpenSourceLicenseAdapter.NoticeViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoticeViewHolder {
        val binding = ItemOpenSourceLicenseBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NoticeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoticeViewHolder, position: Int) {
        holder.bind(notices[position])
    }

    override fun getItemCount(): Int = notices.size

    inner class NoticeViewHolder(
        private val binding: ItemOpenSourceLicenseBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(notice: Notice) = with(binding) {
            title.text = notice.name
            coordinate.text = notice.coordinate
            license.text = root.context.getString(
                if (notice.oss) R.string.oss_notice_license_label else R.string.oss_notice_vendor_terms_label,
                notice.spdx
            )
            note.text = notice.note
            note.visibility = if (notice.note == null) View.GONE else View.VISIBLE
            source.setOnClickListener { openUrl(notice.sourceUrl) }
            viewLicense.setOnClickListener { openUrl(notice.licenseUrl) }
        }
    }

    data class Notice(
        val name: String,
        val coordinate: String,
        val spdx: String,
        val licenseUrl: String,
        val sourceUrl: String,
        val oss: Boolean,
        val note: String?
    )
}
