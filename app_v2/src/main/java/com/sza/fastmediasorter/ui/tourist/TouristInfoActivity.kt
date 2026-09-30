package com.sza.fastmediasorter.ui.tourist

import android.content.Context
import android.content.Intent
import androidx.activity.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.sza.fastmediasorter.core.format.QuantityFormatter
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityTouristInfoBinding
import com.sza.fastmediasorter.domain.model.tourist.TouristTileType
import com.sza.fastmediasorter.domain.unit.UnitSystemProvider
import com.sza.fastmediasorter.ui.tourist.helpers.TouristActionsManager
import com.sza.fastmediasorter.ui.tourist.helpers.TouristHeroTileManager
import com.sza.fastmediasorter.ui.tourist.helpers.TouristSecondaryTilesAdapter
import com.sza.fastmediasorter.ui.tourist.helpers.TouristTileValueFormatter
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * S2922: Tourist dashboard subprogram displaying live telemetry and navigational tiles.
 */
@AndroidEntryPoint
class TouristInfoActivity : BaseActivity<ActivityTouristInfoBinding>() {

    private val viewModel: TouristInfoViewModel by viewModels()

    @Inject
    lateinit var quantityFormatter: QuantityFormatter

    @Inject
    lateinit var unitSystemProvider: UnitSystemProvider

    private lateinit var heroTileManager: TouristHeroTileManager
    private lateinit var secondaryTilesAdapter: TouristSecondaryTilesAdapter
    private lateinit var actionsManager: TouristActionsManager

    override fun getViewBinding(): ActivityTouristInfoBinding =
        ActivityTouristInfoBinding.inflate(layoutInflater)

    override fun setupViews() {
        binding.touristRoot.applySystemBarInsetPadding()
        binding.toolbar.setUpNavigation(this)

        val valueFormatter = TouristTileValueFormatter(this, quantityFormatter) {
            unitSystemProvider.value
        }

        heroTileManager = TouristHeroTileManager(binding, valueFormatter) { focusedTile ->
            when (focusedTile) {
                TouristTileType.SPEED -> viewModel.resetSpeedAndTrip()
                TouristTileType.STEPS -> viewModel.resetSteps()
                TouristTileType.TRIP_DISTANCE -> viewModel.resetTrip()
                else -> {}
            }
        }
        actionsManager = TouristActionsManager(this)

        secondaryTilesAdapter = TouristSecondaryTilesAdapter(valueFormatter) { tileType ->
            viewModel.selectTile(tileType)
        }

        binding.rvSecondaryTiles.layoutManager = GridLayoutManager(this, SECONDARY_TILE_SPAN_COUNT)
        binding.rvSecondaryTiles.adapter = secondaryTilesAdapter
        // Tiles are re-bound on every telemetry emission; the default change animation cross-fades each
        // one and reads as a once-per-second blink that also keeps the GPU busy.
        (binding.rvSecondaryTiles.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

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

        // S3216: the dashboard is where the owner already is when something goes wrong outdoors, so
        // the distress signal is one tap from it rather than back through the programs menu.
        binding.btnSos.setOnClickListener { actionsManager.launchSos() }

        binding.cardHeroTile.setOnClickListener {
            val state = viewModel.state.value
            if (state.focusedTile == TouristTileType.COORDINATES) {
                actionsManager.copyCoordinates(state.latitude, state.longitude)
            }
        }
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.state) {
            renderDashboard()
        }
        // The measurement system can flip while this screen is open, and every tile's unit depends on
        // it, so the same state is re-bound rather than waiting for the next telemetry emission.
        collectOnLifecycle(unitSystemProvider.current) {
            renderDashboard()
        }
    }

    private fun renderDashboard() {
        val state = viewModel.state.value
        heroTileManager.bind(state, this)
        secondaryTilesAdapter.updateState(state)
    }

    companion object {
        private const val SECONDARY_TILE_SPAN_COUNT = 2

        fun createIntent(context: Context): Intent = Intent(context, TouristInfoActivity::class.java)
    }
}
