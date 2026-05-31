package com.sza.fastmediasorter.ui.game.helpers

import kotlin.math.max
import kotlin.math.min

class GameScalingManager {

    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var panningEnabled = false

    fun reset() {
        zoom = 1f
        panX = 0f
        panY = 0f
    }

    fun compute(
        viewWidth: Int,
        viewHeight: Int,
        boardWidth: Int,
        boardHeight: Int,
        largeBoard: Boolean
    ): GameBoardScale {
        if (viewWidth <= 0 || viewHeight <= 0 || boardWidth <= 0 || boardHeight <= 0) {
            return GameBoardScale.EMPTY
        }

        panningEnabled = largeBoard
        if (!largeBoard) reset()

        val fitCellSize = min(viewWidth / boardWidth.toFloat(), viewHeight / boardHeight.toFloat())
        val cellSize = fitCellSize * if (largeBoard) zoom else 1f
        val boardPixelWidth = cellSize * boardWidth
        val boardPixelHeight = cellSize * boardHeight
        clampPan(viewWidth.toFloat(), viewHeight.toFloat(), boardPixelWidth, boardPixelHeight)

        return GameBoardScale(
            cellSize = cellSize,
            offsetX = (viewWidth - boardPixelWidth) / 2f + panX,
            offsetY = (viewHeight - boardPixelHeight) / 2f + panY
        )
    }

    fun zoomBy(scaleFactor: Float): Boolean {
        if (!panningEnabled) return false
        val nextZoom = (zoom * scaleFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (nextZoom == zoom) return false
        zoom = nextZoom
        return true
    }

    fun panBy(deltaX: Float, deltaY: Float): Boolean {
        if (!panningEnabled) return false
        panX += deltaX
        panY += deltaY
        return true
    }

    fun isViewportTransformed(): Boolean = panningEnabled && (zoom > MIN_ZOOM || panX != 0f || panY != 0f)

    private fun clampPan(viewWidth: Float, viewHeight: Float, boardPixelWidth: Float, boardPixelHeight: Float) {
        val maxPanX = max(0f, (boardPixelWidth - viewWidth) / 2f)
        val maxPanY = max(0f, (boardPixelHeight - viewHeight) / 2f)
        panX = panX.coerceIn(-maxPanX, maxPanX)
        panY = panY.coerceIn(-maxPanY, maxPanY)
    }

    companion object {
        private const val MIN_ZOOM = 1f
        private const val MAX_ZOOM = 4f
    }
}

data class GameBoardScale(
    val cellSize: Float,
    val offsetX: Float,
    val offsetY: Float
) {
    companion object {
        val EMPTY = GameBoardScale(0f, 0f, 0f)
    }
}