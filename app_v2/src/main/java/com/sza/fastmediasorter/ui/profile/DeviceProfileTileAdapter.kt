package com.sza.fastmediasorter.ui.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.databinding.ItemDeviceProfileTileBinding
import com.sza.fastmediasorter.ui.common.DeviceProfileUi

/**
 * Grid adapter for the device-profile picker. Renders one pressable tile per profile
 * (icon + title, optional Recommended badge) and highlights the currently-selected tile with a
 * primary-colored stroke. Tapping a tile reports it through [onClick]; the dialog updates the
 * selection via [setSelected].
 *
 * The selected tile shows its description in full while the others stay clamped, so the picker grid
 * stays compact yet the chosen profile can be read end to end (S1383).
 *
 * [onReselect] is optional: when set, a tap on the tile that is already selected reports there
 * instead of through [onClick], which lets a host treat the second tap as "confirm". Left null the
 * adapter behaves exactly as before - every tap goes to [onClick].
 */
class DeviceProfileTileAdapter(
    private val profiles: List<DeviceProfileType>,
    private val recommended: DeviceProfileType?,
    private var selected: DeviceProfileType,
    private val onClick: (DeviceProfileType) -> Unit,
    private val onReselect: ((DeviceProfileType) -> Unit)? = null,
) : RecyclerView.Adapter<DeviceProfileTileAdapter.TileViewHolder>() {

    /** Move the selection highlight to [type], refreshing only the affected tiles. */
    fun setSelected(type: DeviceProfileType) {
        if (type == selected) return
        val previousIndex = profiles.indexOf(selected)
        val newIndex = profiles.indexOf(type)
        selected = type
        if (previousIndex >= 0) notifyItemChanged(previousIndex)
        if (newIndex >= 0) notifyItemChanged(newIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileViewHolder {
        val binding = ItemDeviceProfileTileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TileViewHolder(binding)
    }

    override fun getItemCount(): Int = profiles.size

    override fun onBindViewHolder(holder: TileViewHolder, position: Int) {
        holder.bind(profiles[position])
    }

    inner class TileViewHolder(
        private val binding: ItemDeviceProfileTileBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(type: DeviceProfileType) {
            val context = binding.root.context
            val title = context.getString(DeviceProfileUi.titleRes(type))
            val description = context.getString(DeviceProfileUi.descRes(type))

            binding.ivProfileTileIcon.setImageResource(DeviceProfileUi.iconRes(type))
            binding.tvProfileTileTitle.text = title
            binding.tvProfileTileDescription.text = description

            val isRecommended = type == recommended
            binding.tvProfileTileRecommended.visibility = if (isRecommended) View.VISIBLE else View.GONE
            if (isRecommended) {
                binding.tvProfileTileRecommended.text = context.getString(R.string.welcome_recommended_badge)
            }

            val isSelected = type == selected
            // The stroke means "selected" and nothing else, so exactly one tile ever carries it and
            // picking another profile clears it off the previous one. The auto-detected profile is
            // marked by its badge instead - tying the stroke to it too left two framed tiles after
            // the user picked anything other than the recommendation.
            val density = context.resources.displayMetrics.density
            binding.cardProfileTile.strokeWidth =
                if (isSelected) (SELECTED_STROKE_DP * density).toInt() else 0
            binding.cardProfileTile.isSelected = isSelected
            binding.cardProfileTile.contentDescription = "$title. $description"

            // Both branches are assigned: RecyclerView hands this holder an already-expanded TextView
            // when the previously-bound tile was the selected one.
            binding.tvProfileTileDescription.maxLines =
                if (isSelected) Int.MAX_VALUE else COLLAPSED_DESCRIPTION_LINES

            binding.cardProfileTile.setOnClickListener {
                // Compare against the live selection, not the bind-time copy - the host may have
                // moved the highlight through setSelected() since this tile was bound.
                val confirming = onReselect != null && type == selected
                if (confirming) onReselect?.invoke(type) else onClick(type)
            }
        }
    }

    private companion object {
        /** Matches android:maxLines on tvProfileTileDescription in item_device_profile_tile.xml. */
        const val COLLAPSED_DESCRIPTION_LINES = 2

        /** Stroke width, in dp, of the frame drawn around the selected tile. */
        const val SELECTED_STROKE_DP = 2
    }
}
