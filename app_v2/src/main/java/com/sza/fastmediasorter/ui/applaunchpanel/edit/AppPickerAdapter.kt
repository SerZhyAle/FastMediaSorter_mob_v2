package com.sza.fastmediasorter.ui.applaunchpanel.edit

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.databinding.ItemAppPickerRowBinding
import com.sza.fastmediasorter.domain.usecase.panel.LaunchableApp

/** Binds launchable apps to icon + label rows; reports the picked app through [onPicked]. */
class AppPickerAdapter(
    private val apps: List<LaunchableApp>,
    private val onPicked: (LaunchableApp) -> Unit,
) : RecyclerView.Adapter<AppPickerAdapter.AppViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppPickerRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AppViewHolder(binding)
    }

    override fun getItemCount(): Int = apps.size

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    inner class AppViewHolder(
        private val binding: ItemAppPickerRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(app: LaunchableApp) {
            binding.ivAppIcon.setImageDrawable(app.icon)
            binding.tvAppLabel.text = app.label
            // TalkBack reads the row by the app label rather than the bare layout container.
            binding.rowApp.contentDescription = app.label
            binding.rowApp.setOnClickListener { onPicked(app) }
        }
    }
}
