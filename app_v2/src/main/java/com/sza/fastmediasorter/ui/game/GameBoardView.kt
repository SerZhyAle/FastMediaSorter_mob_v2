package com.sza.fastmediasorter.ui.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import com.sza.fastmediasorter.domain.game.GameDirection
import com.sza.fastmediasorter.ui.game.helpers.GameBoardActorCell
import com.sza.fastmediasorter.ui.game.helpers.GameBoardBaseCell
import com.sza.fastmediasorter.ui.game.helpers.GameBoardDefeatConnection
import com.sza.fastmediasorter.ui.game.helpers.GameBoardHighlightCell
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
    var onTapDirection: ((GameDirection) -> Unit)? = null

    private val scalingManager = GameScalingManager()
    private var renderState: GameBoardRenderState? = null
    private var renderedLevelNumber: Int? = null
    private var renderedIntroHighlightKey: Int? = null
    private var introHighlightUntilMs: Long = 0L
    private var tapCandidate = false
    private var downX = 0f
    private var downY = 0f
    private val tapSlop = ViewConfiguration.get(context).scaledTouchSlop

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
    private val startHighlightFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55FFD54F")
        style = Paint.Style.FILL
    }
    private val startHighlightStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFD54F")
        style = Paint.Style.STROKE
    }
    private val defeatHighlightFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FF5252")
        style = Paint.Style.FILL
    }
    private val defeatHighlightStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFF7043")
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
        val nextIntroKey = nextRenderState?.introHighlightKey
        if (nextIntroKey != null && nextIntroKey != renderedIntroHighlightKey) {
            renderedIntroHighlightKey = nextIntroKey
            introHighlightUntilMs = SystemClock.uptimeMillis() + START_HIGHLIGHT_MS
        } else if (nextIntroKey == null) {
            renderedIntroHighlightKey = null
            introHighlightUntilMs = 0L
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
        drawIntroHighlights(canvas, scale, currentRenderState)
        currentRenderState.defeatConnection?.let { connection -> drawDefeatHighlights(canvas, scale, connection) }
        drawBoardBorder(canvas, scale, currentRenderState)
        currentRenderState.defeatConnection?.let { connection -> drawDefeatConnection(canvas, scale, connection) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        trackTapCandidate(event)
        val scaleHandled = scaleGestureDetector.onTouchEvent(event)
        val gestureHandled = gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val tapDirection = if (tapCandidate && !scaleGestureDetector.isInProgress) {
                directionFromTap(event.x, event.y)
            } else {
                null
            }
            performClick()
            tapDirection?.let { direction ->
                onTapDirection?.invoke(direction)
                return true
            }
        }
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

    private fun drawIntroHighlights(canvas: Canvas, scale: GameBoardScale, renderState: GameBoardRenderState) {
        if (SystemClock.uptimeMillis() >= introHighlightUntilMs) return
        drawCellHighlights(
            canvas,
            scale,
            renderState.startHighlights,
            startHighlightFillPaint,
            startHighlightStrokePaint
        )
        postInvalidateOnAnimation()
    }

    private fun drawDefeatHighlights(canvas: Canvas, scale: GameBoardScale, connection: GameBoardDefeatConnection) {
        drawCellHighlights(
            canvas,
            scale,
            listOf(
                GameBoardHighlightCell(connection.playerRow, connection.playerColumn),
                GameBoardHighlightCell(connection.enemyRow, connection.enemyColumn)
            ).distinct(),
            defeatHighlightFillPaint,
            defeatHighlightStrokePaint
        )
    }

    private fun drawCellHighlights(
        canvas: Canvas,
        scale: GameBoardScale,
        highlights: List<GameBoardHighlightCell>,
        fillPaint: Paint,
        strokePaint: Paint
    ) {
        strokePaint.strokeWidth = max(MIN_HIGHLIGHT_WIDTH, scale.cellSize * HIGHLIGHT_WIDTH_FACTOR)
        highlights.forEach { highlight ->
            val left = scale.offsetX + highlight.column * scale.cellSize
            val top = scale.offsetY + highlight.row * scale.cellSize
            cellRect.set(left, top, left + scale.cellSize, top + scale.cellSize)
            canvas.drawRoundRect(cellRect, scale.cellSize * 0.12f, scale.cellSize * 0.12f, fillPaint)
            canvas.drawRoundRect(cellRect, scale.cellSize * 0.12f, scale.cellSize * 0.12f, strokePaint)
        }
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

    private fun trackTapCandidate(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                tapCandidate = true
            }
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_CANCEL -> tapCandidate = false
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > tapSlop || abs(event.y - downY) > tapSlop) {
                    tapCandidate = false
                }
            }
        }
    }

    private fun directionFromTap(x: Float, y: Float): GameDirection? {
        val currentRenderState = renderState ?: return null
        val scale = scalingManager.compute(
            width,
            height,
            currentRenderState.boardWidth,
            currentRenderState.boardHeight,
            currentRenderState.isLargeBoard
        )
        if (scale == GameBoardScale.EMPTY) return null

        val boardLeft = scale.offsetX
        val boardTop = scale.offsetY
        val boardRight = boardLeft + currentRenderState.boardWidth * scale.cellSize
        val boardBottom = boardTop + currentRenderState.boardHeight * scale.cellSize
        if (x < boardLeft || x > boardRight || y < boardTop || y > boardBottom) return null

        val playerCenterX = boardLeft + (currentRenderState.playerColumn + 0.5f) * scale.cellSize
        val playerCenterY = boardTop + (currentRenderState.playerRow + 0.5f) * scale.cellSize
        val deltaX = x - playerCenterX
        val deltaY = y - playerCenterY
        if (abs(deltaX) < scale.cellSize * MIN_TAP_VECTOR_FACTOR &&
            abs(deltaY) < scale.cellSize * MIN_TAP_VECTOR_FACTOR) {
            return null
        }

        return if (abs(deltaY) >= abs(deltaX)) {
            if (deltaY < 0f) GameDirection.UP else GameDirection.DOWN
        } else {
            if (deltaX < 0f) GameDirection.LEFT else GameDirection.RIGHT
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
        private const val MIN_HIGHLIGHT_WIDTH = 4f
        private const val HIGHLIGHT_WIDTH_FACTOR = 0.07f
        private const val MIN_TAP_VECTOR_FACTOR = 0.18f
        private const val START_HIGHLIGHT_MS = 1_000L
    }
}
