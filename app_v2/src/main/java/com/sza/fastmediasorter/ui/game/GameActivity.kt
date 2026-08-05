package com.sza.fastmediasorter.ui.game

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityGameBinding
import com.sza.fastmediasorter.domain.game.GameMode
import com.sza.fastmediasorter.domain.game.GameStatus
import com.sza.fastmediasorter.ui.game.helpers.GameBoardAccessibilityLabels
import com.sza.fastmediasorter.ui.game.helpers.GameBoardRenderMapper
import com.sza.fastmediasorter.ui.game.helpers.GameInputManager
import com.sza.fastmediasorter.ui.game.helpers.GameModeMenuManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.utils.getStatusBarHeightSafe
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GameActivity : BaseActivity<ActivityGameBinding>() {

    private val viewModel: GameViewModel by viewModels()
    private val renderMapper = GameBoardRenderMapper()
    private lateinit var boardView: GameBoardView
    private lateinit var inputManager: GameInputManager
    private lateinit var modeMenuManager: GameModeMenuManager
    private var previousStatus: GameStatus? = null
    private var autoAdvanceJob: Job? = null

    private val accessibilityLabels by lazy(LazyThreadSafetyMode.NONE) {
        GameBoardAccessibilityLabels(
            floor = getString(R.string.game_cell_floor),
            wall = getString(R.string.game_cell_wall),
            exit = getString(R.string.game_cell_exit),
            player = getString(R.string.game_cell_player),
            kryvavitsa = getString(R.string.game_cell_kryvavitsa),
            shadow = getString(R.string.game_cell_shadow),
            summary = { actorLabel, row, column, width, height ->
                getString(R.string.game_board_accessibility_summary, actorLabel, row, column, width, height)
            }
        )
    }

    override fun shouldEnableEdgeToEdge(): Boolean = false

    override fun getViewBinding(): ActivityGameBinding = ActivityGameBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (shouldEnableEdgeToEdge() || Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            applySystemBarInsets()
        }
    }

    override fun setupViews() {
        boardView = GameBoardView(this)
        binding.gameBoardContainer.addView(
            boardView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        inputManager = GameInputManager(
            onDirection = { direction -> viewModel.move(direction) },
            onPrimaryAction = { handleResetAction() },
            onBack = { finish() }
        )
        inputManager.attachBoard(boardView)
        inputManager.bindDirectionControls(
            up = binding.btnGameUp,
            down = binding.btnGameDown,
            left = binding.btnGameLeft,
            right = binding.btnGameRight
        )
        inputManager.bindPrimaryAction(binding.btnGameReset)
        binding.btnGameSkip.setOnClickListener { viewModel.skipTurn() } // S0928
        binding.btnGameRestartLevel.setOnClickListener { viewModel.restartCurrentLevel() }
        modeMenuManager = GameModeMenuManager(this, this)
        binding.btnGameBack.setOnClickListener { finish() }
        binding.btnGameMode.setOnClickListener {
            modeMenuManager.show(currentMode()) { selected -> viewModel.setMode(selected) }
        }
        binding.btnGameHelp.setOnClickListener {
            startActivity(
                Intent(this, GameHelpActivity::class.java)
                    .putExtra(GameHelpActivity.EXTRA_MODE, currentMode().name)
            )
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            }
        )
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.state) { state -> render(state) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::inputManager.isInitialized && inputManager.handleKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun getInitialFocusView(): View? = binding.btnGameUp

    override fun onLayoutConfigurationChanged(newConfig: Configuration) {
        boardView.requestLayout()
    }

    private fun currentMode(): GameMode = (viewModel.state.value as? GameUiState.Ready)?.mode ?: GameMode.CLASSIC

    private fun handleResetAction() {
        val ready = viewModel.state.value as? GameUiState.Ready
        if (ready == null) {
            viewModel.startNewGame()
            return
        }
        when (ready.levelState.status) {
            GameStatus.LEVEL_WON -> viewModel.advanceLevel()
            GameStatus.GAME_OVER -> viewModel.restartLevel()
            GameStatus.PLAYING -> viewModel.startNewGame()
        }
    }

    private fun render(state: GameUiState) {
        when (state) {
            GameUiState.Loading -> renderLoading()
            is GameUiState.Ready -> renderReady(state)
            is GameUiState.InvalidBoard -> renderInvalidBoard()
            is GameUiState.Error -> renderError()
        }
    }

    private fun renderLoading() {
        binding.txtGameBoardPlaceholder.isVisible = true
        binding.txtGameBoardPlaceholder.setText(R.string.game_loading)
        boardView.setRenderState(null)
        binding.btnGameReset.isEnabled = false
        binding.btnGameReset.setText(R.string.game_new_game)
        binding.btnGameRestartLevel.isVisible = false
        cancelAutoAdvance()
        previousStatus = null
    }

    private fun renderReady(ready: GameUiState.Ready) {
        binding.txtGameBoardPlaceholder.isVisible = false
        boardView.setRenderState(renderMapper.map(ready, accessibilityLabels))
        binding.txtGameLevel.text = getString(R.string.game_level, ready.levelNumber)
        binding.txtGameScore.text = getString(R.string.game_score, ready.score)
        binding.txtGameTurns.text = getString(R.string.game_turns, ready.turns)
        binding.btnGameReset.isEnabled = true
        binding.btnGameReset.text = resetActionText(ready)
        binding.btnGameRestartLevel.isVisible = ready.levelState.status == GameStatus.PLAYING
        val modeLabel = getString(GameModeMenuManager.labelRes(ready.mode))
        binding.btnGameMode.text = modeLabel
        binding.btnGameMode.contentDescription = getString(R.string.game_mode_button_content_description, modeLabel)
        maybeShowDefeatToast(ready)
        maybeScheduleAutoAdvance(ready)
        previousStatus = ready.levelState.status
    }

    private fun renderInvalidBoard() {
        binding.txtGameBoardPlaceholder.isVisible = true
        binding.txtGameBoardPlaceholder.setText(R.string.game_status_invalid_board)
        boardView.setRenderState(null)
        binding.btnGameReset.isEnabled = false
        binding.btnGameReset.setText(R.string.game_new_game)
        binding.btnGameRestartLevel.isVisible = false
        cancelAutoAdvance()
        previousStatus = null
    }

    private fun renderError() {
        binding.txtGameBoardPlaceholder.isVisible = true
        binding.txtGameBoardPlaceholder.setText(R.string.game_status_storage_reset)
        boardView.setRenderState(null)
        binding.btnGameReset.isEnabled = true
        binding.btnGameReset.setText(R.string.game_new_game)
        binding.btnGameRestartLevel.isVisible = false
        cancelAutoAdvance()
        previousStatus = null
    }

    private fun resetActionText(ready: GameUiState.Ready): String = when {
        ready.levelState.status == GameStatus.LEVEL_WON -> getString(R.string.game_next_level)
        ready.levelState.status == GameStatus.GAME_OVER -> getString(R.string.game_restart_level)
        else -> getString(R.string.game_reset)
    }

    private fun maybeShowDefeatToast(ready: GameUiState.Ready) {
        if (previousStatus == GameStatus.GAME_OVER) return
        if (ready.levelState.status != GameStatus.GAME_OVER || ready.defeatConnection == null) return
        Toast.makeText(this, R.string.game_defeat_toast, Toast.LENGTH_SHORT).show()
    }

    private fun applySystemBarInsets() {
        val root = binding.root
        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom

        fun apply(insets: WindowInsetsCompat) {
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            root.setPadding(
                baseLeft + maxOf(systemBars.left, cutout.left),
                baseTop + maxOf(systemBars.top, cutout.top, insets.getStatusBarHeightSafe(resources)),
                baseRight + maxOf(systemBars.right, cutout.right),
                baseBottom + maxOf(systemBars.bottom, cutout.bottom)
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            apply(insets)
            insets
        }
        ViewCompat.getRootWindowInsets(root)?.let(::apply) ?: ViewCompat.requestApplyInsets(root)
    }

    private fun maybeScheduleAutoAdvance(ready: GameUiState.Ready) {
        if (ready.levelState.status != GameStatus.LEVEL_WON) {
            cancelAutoAdvance()
            return
        }
        if (previousStatus == GameStatus.LEVEL_WON || autoAdvanceJob?.isActive == true) return

        val levelNumber = ready.levelNumber
        autoAdvanceJob = lifecycleScope.launch {
            delay(AUTO_ADVANCE_DELAY_MS)
            val current = viewModel.state.value as? GameUiState.Ready ?: return@launch
            if (current.levelState.status == GameStatus.LEVEL_WON && current.levelNumber == levelNumber) {
                viewModel.advanceLevel()
            }
        }
    }

    private fun cancelAutoAdvance() {
        autoAdvanceJob?.cancel()
        autoAdvanceJob = null
    }

    companion object {
        private const val AUTO_ADVANCE_DELAY_MS = 1_000L
    }
}
