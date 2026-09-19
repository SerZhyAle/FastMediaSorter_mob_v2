package com.sza.fastmediasorter.ui.welcome

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemWelcomePrimaryWindowChoiceBinding
import com.sza.fastmediasorter.domain.launcher.LauncherPrimaryWindow

/**
 * Adapter for selecting the primary window mode during onboarding and in settings.
 */
class WelcomePrimaryWindowChoiceAdapter(
    private var selectedChoice: LauncherPrimaryWindow,
    private var recommendedChoice: LauncherPrimaryWindow,
    private val onChoiceSelected: (LauncherPrimaryWindow) -> Unit
) : RecyclerView.Adapter<WelcomePrimaryWindowChoiceAdapter.ChoiceViewHolder>() {

    private val choices = listOf(
        LauncherPrimaryWindow.HOME_SCREEN,
        LauncherPrimaryWindow.DESKTOP,
        LauncherPrimaryWindow.RESOURCE_MANAGER
    )

    fun updateSelection(selected: LauncherPrimaryWindow, recommended: LauncherPrimaryWindow) {
        val oldSelected = selectedChoice
        val oldRecommended = recommendedChoice
        selectedChoice = selected
        recommendedChoice = recommended
        if (oldSelected != selected || oldRecommended != recommended) {
            notifyItemRangeChanged(0, choices.size)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChoiceViewHolder {
        val binding = ItemWelcomePrimaryWindowChoiceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChoiceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChoiceViewHolder, position: Int) {
        holder.bind(choices[position])
    }

    override fun getItemCount(): Int = choices.size

    inner class ChoiceViewHolder(
        private val binding: ItemWelcomePrimaryWindowChoiceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(choice: LauncherPrimaryWindow) {
            val context = binding.root.context
            val (titleRes, descRes) = when (choice) {
                LauncherPrimaryWindow.HOME_SCREEN ->
                    R.string.launcher_primary_window_home_title to R.string.launcher_primary_window_home_desc
                LauncherPrimaryWindow.DESKTOP ->
                    R.string.launcher_primary_window_desktop_title to R.string.launcher_primary_window_desktop_desc
                LauncherPrimaryWindow.RESOURCE_MANAGER ->
                    R.string.launcher_primary_window_resource_manager_title to
                        R.string.launcher_primary_window_resource_manager_desc
            }

            val isSelected = choice == selectedChoice
            val isRecommended = choice == recommendedChoice

            binding.tvChoiceTitle.setText(titleRes)
            binding.tvChoiceDescription.setText(descRes)
            binding.rbChoice.isChecked = isSelected
            binding.tvChoiceRecommended.isVisible = isRecommended

            val strokeWidthPx = if (isSelected) {
                (2 * context.resources.displayMetrics.density).toInt()
            } else {
                0
            }
            binding.cardChoice.strokeWidth = strokeWidthPx

            val title = context.getString(titleRes)
            val desc = context.getString(descRes)
            val recText = if (isRecommended) {
                " (${context.getString(R.string.launcher_primary_window_recommended)})"
            } else {
                ""
            }
            binding.cardChoice.contentDescription = "$title$recText. $desc"

            binding.cardChoice.setOnClickListener {
                if (choice != selectedChoice) {
                    selectedChoice = choice
                    notifyItemRangeChanged(0, choices.size)
                    onChoiceSelected(choice)
                }
            }
        }
    }
}
