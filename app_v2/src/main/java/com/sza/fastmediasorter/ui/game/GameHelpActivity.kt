package com.sza.fastmediasorter.ui.game

import android.view.View
import androidx.activity.OnBackPressedCallback
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityGameHelpBinding
import com.sza.fastmediasorter.domain.game.GameMode
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GameHelpActivity : BaseActivity<ActivityGameHelpBinding>() {

    override fun shouldEnableEdgeToEdge(): Boolean = false

    override fun getViewBinding(): ActivityGameHelpBinding = ActivityGameHelpBinding.inflate(layoutInflater)

    override fun setupViews() {
        binding.root.applySystemBarInsetPadding()
        applyMode(GameMode.fromStorageName(intent.getStringExtra(EXTRA_MODE)))
        binding.btnGameHelpBack.setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            }
        )
    }

    // S0804: the legend and explanation follow the active skin (icons swap to silhouettes/door, text retunes).
    // S0993: CONTRAST intentionally reuses the classic (coloured-ball) legend - it matches the solid tiles.
    private fun applyMode(mode: GameMode) {
        val kryvavitsa = mode == GameMode.KRYVAVITSA
        binding.txtGameHelpIntro.setText(
            if (kryvavitsa) R.string.game_help_intro_kryvavitsa else R.string.game_help_intro
        )
        binding.txtGameHelpRules.setText(
            if (kryvavitsa) R.string.game_help_rules_kryvavitsa else R.string.game_help_rules
        )
        val legend = binding.gameHelpLegend
        legend.icLegendPlayer.setBackgroundResource(
            if (kryvavitsa) R.drawable.ic_game_fig_monster else R.drawable.bg_game_ball_player
        )
        legend.txtLegendPlayer.setText(
            if (kryvavitsa) R.string.game_help_legend_player_kryvavitsa else R.string.game_help_legend_player
        )
        legend.icLegendKryvavitsa.setBackgroundResource(
            if (kryvavitsa) R.drawable.ic_game_fig_kryvavitsa else R.drawable.bg_game_ball_kryvavitsa
        )
        legend.txtLegendKryvavitsa.setText(
            if (kryvavitsa) R.string.game_help_legend_kryvavitsa_kryvavitsa else R.string.game_help_legend_kryvavitsa
        )
        legend.icLegendShadow.setBackgroundResource(
            if (kryvavitsa) R.drawable.ic_game_fig_shadow else R.drawable.bg_game_ball_shadow
        )
        legend.txtLegendShadow.setText(
            if (kryvavitsa) R.string.game_help_legend_shadow_kryvavitsa else R.string.game_help_legend_shadow
        )
        legend.icLegendExit.setBackgroundResource(
            if (kryvavitsa) R.drawable.ic_game_door else R.drawable.bg_game_ball_exit
        )
        legend.txtLegendExit.setText(
            if (kryvavitsa) R.string.game_help_legend_exit_kryvavitsa else R.string.game_help_legend_exit
        )
        legend.icLegendWall.setBackgroundResource(
            if (kryvavitsa) R.drawable.bg_game_ball_wall_kryvavitsa else R.drawable.bg_game_ball_wall
        )
        legend.txtLegendWall.setText(
            if (kryvavitsa) R.string.game_help_legend_wall_kryvavitsa else R.string.game_help_legend_wall
        )
    }

    override fun observeData() = Unit

    override fun getInitialFocusView(): View = binding.btnGameHelpBack

    companion object {
        const val EXTRA_MODE = "extra_game_mode"
    }
}
