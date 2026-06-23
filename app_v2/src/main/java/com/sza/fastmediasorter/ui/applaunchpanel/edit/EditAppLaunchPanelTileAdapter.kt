package com.sza.fastmediasorter.ui.applaunchpanel.edit

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemAppLaunchPanelEditTileBinding
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileUi

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
                binding.cardTile.contentDescription = tile.label
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
    }
}
