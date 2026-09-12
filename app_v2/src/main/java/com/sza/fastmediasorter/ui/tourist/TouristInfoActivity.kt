package com.sza.fastmediasorter.ui.tourist

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.activity.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityTouristInfoBinding
import com.sza.fastmediasorter.domain.model.tourist.TouristTileType
import com.sza.fastmediasorter.ui.tourist.helpers.TouristActionsManager
import com.sza.fastmediasorter.ui.tourist.helpers.TouristHeroTileManager
import com.sza.fastmediasorter.ui.tourist.helpers.TouristSecondaryTilesAdapter
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * S2922: Tourist dashboard subprogram displaying live telemetry and navigational tiles.
 */
@AndroidEntryPoint
class TouristInfoActivity : BaseActivity<ActivityTouristInfoBinding>() {

    private val viewModel: TouristInfoViewModel by viewModels()

    private lateinit var heroTileManager: TouristHeroTileManager
    private lateinit var secondaryTilesAdapter: TouristSecondaryTilesAdapter
    private lateinit var actionsManager: TouristActionsManager

    override fun getViewBinding(): ActivityTouristInfoBinding =
        ActivityTouristInfoBinding.inflate(layoutInflater)

    override fun setupViews() {
        Timber.d("S2922: Tourist dashboard initialized with remedial fixes")
        binding.touristRoot.applySystemBarInsetPadding()
        binding.toolbar.setNavigationOnClickListener { finish() }

        heroTileManager = TouristHeroTileManager(binding) { focusedTile ->
            when (focusedTile) {
                TouristTileType.SPEED -> viewModel.resetSpeedAndTrip()
                TouristTileType.STEPS -> viewModel.resetSteps()
                TouristTileType.TRIP_DISTANCE -> viewModel.resetTrip()
                else -> {}
            }
        }
        actionsManager = TouristActionsManager(this)

        secondaryTilesAdapter = TouristSecondaryTilesAdapter { tileType ->
            viewModel.selectTile(tileType)
        }

        val spanCount = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 2 else 2
        binding.rvSecondaryTiles.layoutManager = GridLayoutManager(this, spanCount)
        binding.rvSecondaryTiles.adapter = secondaryTilesAdapter

        binding.btnOpenMap.setOnClickListener {
            val state = viewModel.state.value
            actionsManager.openMap(state.latitude, state.longitude)
        }

        binding.btnShareLocation.setOnClickListener {
            val state = viewModel.state.value
            actionsManager.shareLocation(state.latitude, state.longitude)
        }

        binding.btnResetTrip.setOnClickListener {
            val state = viewModel.state.value
            when (state.focusedTile) {
                TouristTileType.SPEED -> viewModel.resetSpeedAndTrip()
                TouristTileType.STEPS -> viewModel.resetSteps()
                else -> viewModel.resetTrip()
            }
        }

        binding.cardHeroTile.setOnClickListener {
            val state = viewModel.state.value
            if (state.focusedTile == TouristTileType.COORDINATES) {
                actionsManager.copyCoordinates(state.latitude, state.longitude)
            }
        }
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.state) { state ->
            heroTileManager.bind(state, this)
            secondaryTilesAdapter.updateState(state)
        }
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, TouristInfoActivity::class.java)
    }
}
