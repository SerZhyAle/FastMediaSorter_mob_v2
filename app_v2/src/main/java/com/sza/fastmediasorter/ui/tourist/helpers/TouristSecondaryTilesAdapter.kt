package com.sza.fastmediasorter.ui.tourist.helpers

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.databinding.ItemTouristTileBinding
import com.sza.fastmediasorter.domain.model.tourist.TouristDashboardState
import com.sza.fastmediasorter.domain.model.tourist.TouristTileType
import com.sza.fastmediasorter.domain.model.tourist.isOffered

/**
 * S2922/S3000/S2995/S3011: renders the grid of secondary telemetry tiles.
 *
 * S4145: what each tile shows comes from [TouristTileContentManager], shared with the focused card; a tile
 * whose hardware is absent is left out of the grid rather than shown empty.
 */
class TouristSecondaryTilesAdapter(
    private val contentManager: TouristTileContentManager,
    private val onTileClicked: (TouristTileType) -> Unit,
) : RecyclerView.Adapter<TouristSecondaryTilesAdapter.TileViewHolder>() {

    private var state: TouristDashboardState = TouristDashboardState()
    private val visibleTiles: MutableList<TouristTileType> = mutableListOf()

    /**
     * The tile set changes only when the focus or the hardware answer changes; then the old range is
     * removed and the new one inserted. Every telemetry tick keeps the range update, which the grid's
     * disabled change animation turns into an in-place redraw.
     */
    fun updateState(newState: TouristDashboardState) {
        this.state = newState
        val tiles = TouristTileType.entries.filter { it.isOffered(newState) && it != newState.focusedTile }
        if (tiles != visibleTiles) {
            val oldSize = visibleTiles.size
            visibleTiles.clear()
            notifyItemRangeRemoved(0, oldSize)
            visibleTiles.addAll(tiles)
            notifyItemRangeInserted(0, visibleTiles.size)
        } else {
            notifyItemRangeChanged(0, visibleTiles.size)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileViewHolder {
        val binding = ItemTouristTileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TileViewHolder, position: Int) {
        val tile = visibleTiles[position]
        holder.bind(tile, contentManager.content(tile, state), onTileClicked)
    }

    override fun getItemCount(): Int = visibleTiles.size

    class TileViewHolder(
        private val binding: ItemTouristTileBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            tile: TouristTileType,
            content: TouristTileContent,
            onTileClicked: (TouristTileType) -> Unit,
        ) {
            val context = binding.root.context
            binding.cardTileRoot.setOnClickListener { onTileClicked(tile) }
            binding.cardTileRoot.setCardBackgroundColor(ContextCompat.getColor(context, content.backgroundRes))
            binding.cardTileRoot.contentDescription = content.spokenDescription
            binding.ivTileIcon.setImageResource(content.iconRes)
            binding.tvTileLabel.setText(content.labelRes)
            binding.tvTileValue.text = content.value
            binding.tvTileUnit.text = content.unit
        }
    }
}
