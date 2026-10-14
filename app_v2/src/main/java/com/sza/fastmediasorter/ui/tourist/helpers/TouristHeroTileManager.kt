package com.sza.fastmediasorter.ui.tourist.helpers

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityTouristInfoBinding
import com.sza.fastmediasorter.domain.model.tourist.TouristDashboardState
import com.sza.fastmediasorter.domain.model.tourist.TouristTileType
import timber.log.Timber
import java.util.Locale

/**
 * S2922/S3000: binds the prominent Hero focus card on the Tourist dashboard.
 *
 * S4145: what a tile shows comes from [TouristTileContentManager], the same source the grid reads; this
 * class only decides which of the card's pictures - coordinates, compass rose, arrow or level - is shown.
 * S4202: it also owns the STOPWATCH tile's controls row - visibility, the toggle's label and icon - while
 * the actions themselves stay callbacks, so no stopwatch logic lives in the activity.
 */
class TouristHeroTileManager(
    private val binding: ActivityTouristInfoBinding,
    private val contentManager: TouristTileContentManager,
    private val onResetClicked: (TouristTileType) -> Unit = {},
    private val onStopwatchToggleClicked: () -> Unit = {},
    private val onStopwatchResetClicked: () -> Unit = {},
    private val onStopwatchOpenClicked: () -> Unit = {},
) {

    fun bind(state: TouristDashboardState, context: Context) {
        val tile = state.focusedTile
        val content = contentManager.content(tile, state)
        Timber.d("S4145: hero bound tile=%s value=%s detail=%s", tile, content.value, content.detail)

        binding.cardHeroTile.setCardBackgroundColor(ContextCompat.getColor(context, content.backgroundRes))
        binding.cardHeroTile.contentDescription = content.spokenDescription
        binding.btnHeroReset.visibility = if (contentManager.supportsReset(tile)) View.VISIBLE else View.GONE
        binding.btnHeroReset.setOnClickListener { onResetClicked(tile) }

        binding.ivHeroIcon.setImageResource(content.iconRes)
        binding.tvHeroTitle.setText(content.labelRes)
        binding.tvHeroPrimaryValue.text = content.value
        binding.tvHeroPrimaryUnit.text = content.unit
        binding.tvHeroCompassHeading.text = content.value
        binding.tvHeroCompassCardinal.text = content.unit
        binding.tvHeroSecondaryDetail.text = content.detail.orEmpty()

        bindStopwatchControls(state, tile)

        val isCompass = tile == TouristTileType.COMPASS
        if (isCompass && binding.layoutHeroCompass.visibility != View.VISIBLE) {
            Timber.d("S4183: Tourist compass focused; rose left, heading right, equal halves")
        }
        bindVisual(content.visual, isCompass, context)
    }

    /**
     * The toggle is one button that changes its label and icon with the running flag, because start and
     * stop are the same control on a primitive single-player stopwatch; the row stays hidden for every
     * other tile so their card height is unchanged.
     */
    private fun bindStopwatchControls(state: TouristDashboardState, tile: TouristTileType) {
        val isStopwatch = tile == TouristTileType.STOPWATCH
        binding.layoutHeroStopwatch.visibility = if (isStopwatch) View.VISIBLE else View.GONE
        if (!isStopwatch) return

        if (state.stopwatchRunning) {
            binding.btnStopwatchToggle.setText(R.string.tourist_stopwatch_action_stop)
            binding.btnStopwatchToggle.setIconResource(R.drawable.ic_stop)
        } else {
            binding.btnStopwatchToggle.setText(R.string.tourist_stopwatch_action_start)
            binding.btnStopwatchToggle.setIconResource(R.drawable.ic_play)
        }
        binding.btnStopwatchToggle.setOnClickListener { onStopwatchToggleClicked() }
        binding.btnStopwatchReset.setOnClickListener { onStopwatchResetClicked() }
        binding.btnStopwatchOpen.setOnClickListener { onStopwatchOpenClicked() }
    }

    private fun bindVisual(visual: HeroVisual, isCompass: Boolean, context: Context) {
        binding.layoutHeroTextMetrics.visibility =
            if (visual is HeroVisual.Coordinates || isCompass) View.GONE else View.VISIBLE
        binding.layoutHeroCoords.visibility = if (visual is HeroVisual.Coordinates) View.VISIBLE else View.GONE
        binding.layoutHeroCompass.visibility = if (isCompass) View.VISIBLE else View.GONE
        binding.ivHeroCompassRose.rotation = if (visual is HeroVisual.CompassRose) -visual.azimuthDegrees else 0f
        binding.layoutHeroVisual.visibility =
            if (visual is HeroVisual.Arrow || visual is HeroVisual.Level) View.VISIBLE else View.GONE
        binding.ivHeroDirectionArrow.visibility = if (visual is HeroVisual.Arrow) View.VISIBLE else View.GONE
        binding.layoutHeroLevel.visibility = if (visual is HeroVisual.Level) View.VISIBLE else View.GONE

        when (visual) {
            is HeroVisual.Coordinates -> bindCoordinates(visual)
            is HeroVisual.CompassRose -> Unit
            is HeroVisual.Arrow -> binding.ivHeroDirectionArrow.rotation = visual.rotationDegrees
            is HeroVisual.Level -> bindLevel(visual, context)
            HeroVisual.None -> Unit
        }
    }

    private fun bindCoordinates(visual: HeroVisual.Coordinates) {
        val lat = visual.latitude
        val lon = visual.longitude
        if (lat != null && lon != null) {
            binding.tvHeroLat.text = String.format(Locale.US, "Lat: %.4f°", lat)
            binding.tvHeroLon.text = String.format(Locale.US, "Lon: %.4f°", lon)
        } else {
            binding.tvHeroLat.text = "Lat: --"
            binding.tvHeroLon.text = "Lon: --"
        }
    }

    /**
     * The bubble floats to the high side, as in a spirit level; [FULL_SCALE_DEGREES] of tilt puts it on
     * the ring, so a phone lying flat reads as centred and a steep slope pins it to the edge.
     */
    private fun bindLevel(visual: HeroVisual.Level, context: Context) {
        val res = context.resources
        val radius = (
            res.getDimension(R.dimen.tourist_level_ring_size) - res.getDimension(R.dimen.tourist_level_bubble_size)
            ) / 2f
        binding.ivHeroLevelBubble.translationX = offset(-visual.rollDegrees, radius)
        binding.ivHeroLevelBubble.translationY = offset(visual.pitchDegrees, radius)
    }

    private fun offset(degrees: Float, radius: Float): Float =
        (degrees / FULL_SCALE_DEGREES).coerceIn(-1f, 1f) * radius

    private companion object {
        const val FULL_SCALE_DEGREES = 30f
    }
}
