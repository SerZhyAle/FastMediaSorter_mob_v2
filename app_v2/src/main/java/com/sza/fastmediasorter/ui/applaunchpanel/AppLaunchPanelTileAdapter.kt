package com.sza.fastmediasorter.ui.applaunchpanel

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemAppLaunchPanelTileBinding
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileUi

/**
 * Renders the fixed 15-slot quick-launch grid. A filled tile shows the resolved icon + label; an empty
 * slot shows a dimmed ghost "add" tile inviting configuration. Taps are reported via [onTileClick];
 * the host decides whether to launch the target or route an empty slot to the Edit screen.
 */
class AppLaunchPanelTileAdapter(
    private val onTileClick: (AppLaunchPanelTileUi) -> Unit,
) : RecyclerView.Adapter<AppLaunchPanelTileAdapter.TileViewHolder>() {

    private var tiles: List<AppLaunchPanelTileUi> = emptyList()

    fun submit(newTiles: List<AppLaunchPanelTileUi>) {
        tiles = newTiles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileViewHolder {
        val binding = ItemAppLaunchPanelTileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TileViewHolder(binding)
    }

    override fun getItemCount(): Int = tiles.size

    override fun onBindViewHolder(holder: TileViewHolder, position: Int) {
        holder.bind(tiles[position])
    }

    inner class TileViewHolder(
        private val binding: ItemAppLaunchPanelTileBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tile: AppLaunchPanelTileUi) {
            val context = binding.root.context
            if (tile.isEmpty) {
                binding.ivPanelTileIcon.setImageResource(R.drawable.ic_add)
                val emptyLabel = context.getString(R.string.app_launch_panel_empty_slot)
                binding.tvPanelTileLabel.text = emptyLabel
                binding.cardPanelTile.contentDescription = emptyLabel
                // Ghost look: a dimmed placeholder reads as "add here", distinct from a filled tile.
                binding.cardPanelTile.alpha = GHOST_ALPHA
            } else {
                binding.ivPanelTileIcon.setImageDrawable(tile.icon)
                binding.tvPanelTileLabel.text = tile.label
                binding.cardPanelTile.contentDescription = tile.label
                binding.cardPanelTile.alpha = 1f
            }
            binding.cardPanelTile.isClickable = true
            binding.cardPanelTile.isFocusable = true
            binding.cardPanelTile.setOnClickListener { onTileClick(tile) }
        }
    }

    private companion object {
        private const val GHOST_ALPHA = 0.45f
    }
}
