package com.sza.fastmediasorter.ui.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.sza.fastmediasorter.domain.game.GameDirection
import com.sza.fastmediasorter.ui.game.helpers.GameBoardActorCell
import com.sza.fastmediasorter.ui.game.helpers.GameBoardBaseCell
import com.sza.fastmediasorter.ui.game.helpers.GameBoardDefeatConnection
import com.sza.fastmediasorter.ui.game.helpers.GameBoardRenderState
import com.sza.fastmediasorter.ui.game.helpers.GameBoardScale
import com.sza.fastmediasorter.ui.game.helpers.GameScalingManager
import kotlin.math.abs
import kotlin.math.max

class GameBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onSwipeDirection: ((GameDirection) -> Unit)? = null

    private val scalingManager = GameScalingManager()
    private var renderState: GameBoardRenderState? = null
    private var renderedLevelNumber: Int? = null

    private val cellRect = RectF()
    private val boardRect = RectF()
    private val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFF5F5F5") }
    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF5F6368") }
    private val exitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF2E7D32") }
    private val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF1976D2") }
    private val kryvavitsaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFD32F2F") }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF7B1FA2") }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF3C4043")
        style = Paint.Style.STROKE
    }
    private val defeatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFE53935")
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33000000")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val changed = scalingManager.zoomBy(detector.scaleFactor)
                if (changed) invalidate()
                return changed
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onScroll(
                firstEvent: MotionEvent?,
                currentEvent: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                val currentRenderState = renderState ?: return false
                if (!currentRenderState.isLargeBoard || scaleGestureDetector.isInProgress) return false
                val changed = scalingManager.panBy(-distanceX, -distanceY)
                if (changed) invalidate()
                return changed
            }

            override fun onFling(
                firstEvent: MotionEvent?,
                currentEvent: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (firstEvent == null || scalingManager.isViewportTransformed()) return false
                val direction = directionFromVelocity(velocityX, velocityY) ?: return false
                onSwipeDirection?.invoke(direction)
                return true
            }
        }
    )

    init {
        isFocusable = true
        isClickable = true
    }

    fun setRenderState(nextRenderState: GameBoardRenderState?) {
        if (nextRenderState?.levelNumber != renderedLevelNumber) {
            scalingManager.reset()
            renderedLevelNumber = nextRenderState?.levelNumber
        }
        renderState = nextRenderState
        contentDescription = nextRenderState?.contentDescription
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentRenderState = renderState ?: return
        val scale = scalingManager.compute(
            width,
            height,
            currentRenderState.boardWidth,
            currentRenderState.boardHeight,
            currentRenderState.isLargeBoard
        )
        if (scale == GameBoardScale.EMPTY) return

        currentRenderState.cells.forEach { cell ->
            val left = scale.offsetX + cell.column * scale.cellSize
            val top = scale.offsetY + cell.row * scale.cellSize
            cellRect.set(left, top, left + scale.cellSize, top + scale.cellSize)
            canvas.drawRect(cellRect, paintFor(cell.baseCell))
            canvas.drawRect(cellRect, gridPaint)
            cell.actorCell?.let { actorCell -> drawActor(canvas, cellRect, actorCell) }
        }
        drawBoardBorder(canvas, scale, currentRenderState)
        currentRenderState.defeatConnection?.let { connection -> drawDefeatConnection(canvas, scale, connection) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scaleHandled = scaleGestureDetector.onTouchEvent(event)
        val gestureHandled = gestureDetector.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP) performClick()
        return scaleHandled || gestureHandled || true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun paintFor(baseCell: GameBoardBaseCell): Paint = when (baseCell) {
        GameBoardBaseCell.FLOOR -> floorPaint
        GameBoardBaseCell.WALL -> wallPaint
        GameBoardBaseCell.EXIT -> exitPaint
    }

    private fun drawActor(canvas: Canvas, bounds: RectF, actorCell: GameBoardActorCell) {
        val radius = bounds.width() * 0.34f
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        val paint = when (actorCell) {
            GameBoardActorCell.PLAYER -> playerPaint
            GameBoardActorCell.KRYVAVITSA -> kryvavitsaPaint
            GameBoardActorCell.SHADOW -> shadowPaint
        }
        canvas.drawCircle(centerX, centerY, radius, paint)
    }

    private fun drawBoardBorder(canvas: Canvas, scale: GameBoardScale, renderState: GameBoardRenderState) {
        borderPaint.strokeWidth = max(MIN_BORDER_WIDTH, scale.cellSize * BORDER_WIDTH_FACTOR)
        val halfStroke = borderPaint.strokeWidth / 2f
        boardRect.set(
            scale.offsetX + halfStroke,
            scale.offsetY + halfStroke,
            scale.offsetX + renderState.boardWidth * scale.cellSize - halfStroke,
            scale.offsetY + renderState.boardHeight * scale.cellSize - halfStroke
        )
        canvas.drawRect(boardRect, borderPaint)
    }

    private fun drawDefeatConnection(canvas: Canvas, scale: GameBoardScale, connection: GameBoardDefeatConnection) {
        defeatPaint.strokeWidth = max(MIN_DEFEAT_WIDTH, scale.cellSize * DEFEAT_WIDTH_FACTOR)
        val playerCenterX = scale.offsetX + (connection.playerColumn + 0.5f) * scale.cellSize
        val playerCenterY = scale.offsetY + (connection.playerRow + 0.5f) * scale.cellSize
        val enemyCenterX = scale.offsetX + (connection.enemyColumn + 0.5f) * scale.cellSize
        val enemyCenterY = scale.offsetY + (connection.enemyRow + 0.5f) * scale.cellSize
        if (playerCenterX == enemyCenterX && playerCenterY == enemyCenterY) {
            canvas.drawCircle(playerCenterX, playerCenterY, scale.cellSize * 0.46f, defeatPaint)
        } else {
            canvas.drawLine(playerCenterX, playerCenterY, enemyCenterX, enemyCenterY, defeatPaint)
        }
    }

    private fun directionFromVelocity(velocityX: Float, velocityY: Float): GameDirection? {
        if (abs(velocityX) < FLING_THRESHOLD && abs(velocityY) < FLING_THRESHOLD) return null
        return if (abs(velocityX) > abs(velocityY)) {
            if (velocityX > 0f) GameDirection.RIGHT else GameDirection.LEFT
        } else {
            if (velocityY > 0f) GameDirection.DOWN else GameDirection.UP
        }
    }

    companion object {
        private const val FLING_THRESHOLD = 500f
        private const val MIN_BORDER_WIDTH = 6f
        private const val BORDER_WIDTH_FACTOR = 0.08f
        private const val MIN_DEFEAT_WIDTH = 5f
        private const val DEFEAT_WIDTH_FACTOR = 0.10f
    }
}