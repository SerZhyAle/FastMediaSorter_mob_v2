package com.sza.fastmediasorter.ui.applaunchpanel.edit

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemAppLaunchPanelEditTileBinding
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileType
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileUi
import com.sza.fastmediasorter.domain.model.panel.AppLaunchPanelRouteTarget

/**
 * Renders the fixed 15-slot Edit-panel grid. A filled tile shows the resolved icon + label; an empty
 * slot shows the "add" placeholder. Reports a tap via [onTileClick] (fill / replace flow) and a
 * long-press via [onTileLongClick] (move / replace / remove menu).
 */
class EditAppLaunchPanelTileAdapter(
    private val onTileClick: (AppLaunchPanelTileUi) -> Unit,
    private val onTileLongClick: (AppLaunchPanelTileUi) -> Unit,
) : RecyclerView.Adapter<EditAppLaunchPanelTileAdapter.TileViewHolder>() {

    private var tiles: List<AppLaunchPanelTileUi> = emptyList()

    fun submit(newTiles: List<AppLaunchPanelTileUi>) {
        tiles = newTiles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileViewHolder {
        val binding = ItemAppLaunchPanelEditTileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TileViewHolder(binding)
    }

    override fun getItemCount(): Int = tiles.size

    override fun onBindViewHolder(holder: TileViewHolder, position: Int) {
        holder.bind(tiles[position])
    }

    inner class TileViewHolder(
        private val binding: ItemAppLaunchPanelEditTileBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tile: AppLaunchPanelTileUi) {
            val context = binding.root.context
            if (tile.isEmpty) {
                binding.ivTileIcon.setImageResource(R.drawable.ic_add)
                val emptyLabel = context.getString(R.string.app_launch_panel_empty_slot)
                binding.tvTileLabel.text = emptyLabel
                binding.cardTile.contentDescription = emptyLabel
            } else {
                binding.ivTileIcon.setImageDrawable(tile.icon)
                binding.tvTileLabel.text = tile.label
                // Name the tile kind for TalkBack so the three paths are distinguishable without colour
                // (strategic S0663 §3.2): "<label>, <kind>".
                binding.cardTile.contentDescription = "${tile.label}, ${kindLabel(context, tile)}"
            }
            binding.cardTile.setOnClickListener { onTileClick(tile) }
            // Long-press menu only applies to a configured tile; an empty slot just opens the picker.
            binding.cardTile.setOnLongClickListener {
                if (tile.isEmpty) {
                    onTileClick(tile)
                } else {
                    onTileLongClick(tile)
                }
                true
            }
        }

        private fun kindLabel(context: Context, tile: AppLaunchPanelTileUi): String {
            val resId = when (tile.type) {
                AppLaunchPanelTileType.OWN_APP -> R.string.app_launch_panel_own_app_label
                AppLaunchPanelTileType.EXTERNAL_APP -> R.string.app_launch_panel_path_external_app
                AppLaunchPanelTileType.INTERNAL_ROUTE -> when (AppLaunchPanelRouteTarget.decode(tile.targetId)) {
                    is AppLaunchPanelRouteTarget.OsShortcut -> R.string.app_launch_panel_path_os_part
                    is AppLaunchPanelRouteTarget.Resource -> R.string.app_launch_panel_sub_resource
                    else -> R.string.app_launch_panel_sub_feature
                }
                AppLaunchPanelTileType.RESERVED -> R.string.app_launch_panel_empty_slot
            }
            return context.getString(resId)
        }
    }
}
