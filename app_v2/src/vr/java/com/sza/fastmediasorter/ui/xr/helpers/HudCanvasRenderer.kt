package com.sza.fastmediasorter.ui.xr.helpers

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Model + Canvas painter of the immersive HUD panel.
 *
 * S0964: the panel is the visible HUD on user launches (`FILE_URI`) - it is rendered into the
 * HUD quad via `queueHud` on state changes (never per frame, S0290 rule). The diagnostic
 * playlist keeps the 1024x128 filename banner and uses this class as interaction model only.
 * All captions are injected localized by the Activity - the renderer stays Context-free.
 *
 * S1228 (owner, 2026-07-27, in-headset review): the panel was a 1024x640 block on a 0.48x0.30 m
 * quad centred in view - the text measured ~0.015 m tall at 1.5 m ("шрифт как маленькие кубики")
 * and the block sat on top of the film. It is now a single horizontal strip: a wider texture on a
 * wider quad gives ~1.9x the glyph height, one row of controls leaves the centre of view clear,
 * and one row of controls leaves the centre of view clear.
 *
 * S1232: the collapse-to-pill affordance is gone - the owner rejected the pill as "still an
 * obstruction". The header band now carries two terminal buttons at opposite ends: hide (the strip
 * disappears entirely, playback continues) and exit (leave the immersive session). A hidden strip
 * comes back via the controller trigger, not via anything painted on the quad.
 *
 * S1223: a third header-band button, HELP, sits immediately left of hide and reopens the one-time
 * controls legend. It is not terminal - it changes nothing about the session - but it shares the
 * band and therefore the same click-before-the-control-row precedence.
 */
class HudCanvasRenderer {

    companion object {
        // S1228: 1024x640 block -> 2560x360 strip (~7.1:1). Paired with a 1.40x0.197 m quad in
        // DiagnosticXrActivity, so glyph height goes 0.015 m -> 0.028 m at the 1.5 m watch distance.
        const val WIDTH = 2560
        const val HEIGHT = 360

        private const val MARGIN = 24f
        private const val CORNER_RADIUS = 28f
        private const val BUTTON_RADIUS = 18f
        private const val SLIDER_RADIUS = 12f

        // Header line: filename + status, spanning the strip above the control row. S1232 removed
        // the fixed left anchor - the text starts after the exit button now.
        private const val HEADER_BASELINE = 96f
        private const val HEADER_TEXT_SIZE = 52f
        private const val STATUS_TEXT_SIZE = 36f
        private const val STATUS_GAP = 32f

        // Single control row.
        private const val ROW_TOP = 176f
        private const val ROW_BOTTOM = 312f
        private const val ROW_CENTER = (ROW_TOP + ROW_BOTTOM) / 2f
        private const val CAPTION_BASELINE_SHIFT = 16f

        // Transport block (PREV / PLAY-PAUSE / NEXT).
        private const val TRANSPORT_LEFT = 32f
        private const val TRANSPORT_BTN_W = 196f
        private const val TRANSPORT_GAP = 20f

        // Track cycle blocks (AUDIO, SUBS): caption, `<`, value, `>`. S1238: the row is laid out
        // per state (relayout), so blocks carry intrinsic zone widths instead of fixed X anchors.
        private const val ARROW_W = 90f
        private const val CAPTION_ZONE_W = 188f
        private const val VALUE_ZONE_W = 240f
        private const val TRACK_BLOCK_W = CAPTION_ZONE_W + ARROW_W + VALUE_ZONE_W + ARROW_W

        // Slider columns (VOLUME, STEREO DEPTH): caption above a horizontal track.
        private const val SLIDER_CAPTION_BASELINE = 214f
        private const val SLIDER_TOP = 250f
        private const val SLIDER_H = 26f
        private const val SLIDER_KNOB_R = 22f
        private const val SLIDER_W = 240f

        // S1238: reflow area for the conditional blocks - everything right of the transport trio.
        private const val MIN_BLOCK_GAP = 40f
        private const val ROW_AREA_LEFT =
            TRANSPORT_LEFT + 3 * TRANSPORT_BTN_W + 2 * TRANSPORT_GAP + MIN_BLOCK_GAP
        private const val ROW_AREA_RIGHT = WIDTH - MARGIN

        // S1232: geometry shared by the two terminal buttons in the header band. Wider than the
        // S1228 "X" it replaces because both now carry a localized word, not a glyph.
        private const val TERMINAL_W = 220f
        private const val TERMINAL_TOP = 24f
        private const val TERMINAL_H = 108f

        // S1223: HELP sits immediately left of HIDE. EXIT keeps the far end to itself - S1232
        // isolated the destructive button on purpose, and a mis-tap between HELP and HIDE costs one
        // trigger pull to undo. Wider than TERMINAL_W because the localized word is longer in RU and
        // UK and drawButton does not ellipsize.
        private const val HELP_W = 300f

        // Everything right-aligned in the header and seek bands hangs off this one anchor, so the
        // button and the labels cannot drift apart when either width changes.
        private const val HEADER_RIGHT_ANCHOR = WIDTH - MARGIN - TERMINAL_W - MARGIN - HELP_W

        // S1239: the seek band takes the dead 44 px between the header band (ends at
        // TERMINAL_TOP + TERMINAL_H) and the control row (starts at ROW_TOP). It deliberately
        // stays out of that row's reflow - the row is already full, since S1238's worst case
        // (both track blocks plus both sliders) overruns ROW_AREA_RIGHT by 20 px, so there was no
        // width left to take. Horizontally the same two terminal buttons bound it, and the right
        // end reserves SEEK_TIME_ZONE_W for the elapsed/total label.
        private const val SEEK_TOP = 140f
        private const val SEEK_H = 24f
        private const val SEEK_KNOB_R = 20f
        private const val SEEK_TIME_ZONE_W = 400f
        private const val SEEK_LEFT = MARGIN + TERMINAL_W + MARGIN

        // S1223: shortened by the HELP button's width so the band and the button never overlap.
        private const val SEEK_RIGHT = HEADER_RIGHT_ANCHOR - MARGIN - SEEK_TIME_ZONE_W
        private const val SEEK_TIME_BASELINE = SEEK_TOP + SEEK_H

        private const val TEXT_SIZE = 48f
        private const val CAPTION_TEXT_SIZE = 40f
        private const val CURSOR_R = 16f

        // Ellipsize: keep at least this many chars, dropping this many per step before "..".
        private const val ELLIPSIZE_MIN_LEN = 4
        private const val ELLIPSIZE_DROP = 3

        private const val BG_ALPHA = 220
        private const val BG_R = 15
        private const val BG_G = 15
        private const val BG_B = 25
        private const val ACCENT_R = 66
        private const val ACCENT_G = 165
        private const val ACCENT_B = 245
        private const val EXIT_R = 198
        private const val EXIT_G = 82
        private const val EXIT_B = 92
        private const val TRACK_ALPHA = 100
        private const val TRACK_GREY = 100
        private const val TRACK_GREY_B = 120
        private const val HEADER_GREY_RG = 230
        private const val HEADER_GREY_B = 250
        private const val STATUS_R = 129
        private const val STATUS_G = 199
        private const val STATUS_B = 132
        private const val CURSOR_ALPHA = 160
        private const val CURSOR_R_CHANNEL = 255
        private const val CURSOR_G_CHANNEL = 64
        private const val CURSOR_B_CHANNEL = 129
    }

    // Interactive button rects (transport block)
    val prevRect = transportButtonRect(0)
    val playPauseRect = transportButtonRect(1)
    val nextRect = transportButtonRect(2)

    // S0964/S1238: track rows and sliders are laid out per state by relayout(); the dispatcher
    // hit-tests these same RectF instances, so mutating them in place keeps ray and paint in sync.
    val audioPrevRect = RectF()
    val audioNextRect = RectF()
    val subsPrevRect = RectF()
    val subsNextRect = RectF()
    val volumeTrackRect = RectF()
    val depthTrackRect = RectF()

    private var audioCaptionX = 0f
    private var subsCaptionX = 0f

    // S1232: the two terminal actions, at opposite ends of the header band so they cannot be
    // mistaken for each other. Hide keeps the S1228 close button's position - the only muscle
    // memory that exists here - and exit sits as far from it as the strip allows. The restore
    // pill is gone: the trigger summons a fully hidden panel back (S1240 decision 3).
    val exitRect = RectF(MARGIN, TERMINAL_TOP, MARGIN + TERMINAL_W, TERMINAL_TOP + TERMINAL_H)
    val hideRect = RectF(WIDTH - MARGIN - TERMINAL_W, TERMINAL_TOP, WIDTH - MARGIN, TERMINAL_TOP + TERMINAL_H)

    // S1223: reopens the one-time controls legend, which is otherwise unreachable after its single
    // automatic showing on the first immersive entry.
    val helpRect = RectF(
        HEADER_RIGHT_ANCHOR,
        TERMINAL_TOP,
        HEADER_RIGHT_ANCHOR + HELP_W,
        TERMINAL_TOP + TERMINAL_H
    )

    // S1239: fixed like the terminal buttons rather than relaid out like the row blocks - the band
    // owns its own strip of the strip. Painted and hit-tested from this one instance.
    val seekTrackRect = RectF(SEEK_LEFT, SEEK_TOP, SEEK_RIGHT, SEEK_TOP + SEEK_H)

    // S1239: only media with a known length gets a bar. An image, or a live source whose duration
    // is unset, leaves the band empty instead of parking a handle at zero and inviting a drag that
    // cannot go anywhere.
    var seekRowVisible = false
    var seekProgress = 0f
    var timeLabel = ""

    var isPlaying = true
    var volume = 1.0f // 0.0 to 1.0
    var depth = 0.5f  // 0.0 to 1.0
    var currentFilename = "Loading.."
    var fps = 0.0f

    // S0964: captions/labels are set by the Activity (localized); EN defaults are fallbacks.
    var audioCaption = "AUDIO"
    var subsCaption = "SUBS"
    var audioTrackLabel = "-"
    var subtitleTrackLabel = "-"

    // S1238: availability now means PRESENCE - a row that does not apply is not painted at all
    // (the S0964 dimming answer predates the single-strip layout, where dead controls cost width).
    // Setters relayout so the remaining blocks absorb the freed space; hidden rects go empty,
    // which makes RectF.contains() miss - belt on top of the dispatcher's flag guards.
    var audioRowEnabled = false
        set(value) {
            field = value
            relayout()
        }
    var subsRowEnabled = false
        set(value) {
            field = value
            relayout()
        }
    var depthRowVisible = true
        set(value) {
            field = value
            relayout()
        }
    var prevLabel = "PREV"
    var playLabel = "PLAY"
    var pauseLabel = "PAUSE"
    var nextLabel = "NEXT"
    var volumeCaption = "VOLUME"
    var depthCaption = "STEREO DEPTH"
    var hideLabel = "HIDE"
    var exitLabel = "EXIT"
    var helpLabel = "HELP"

    // Cursor position for rendering feedback (drawn from UV in dispatch)
    var hoverX = -1f
    var hoverY = -1f
    var hasHover = false

    init {
        relayout()
    }

    /**
     * S1238: packs the visible conditional blocks (audio, subs, volume, depth) across the row
     * area with evenly distributed gaps, so hiding a row widens breathing room instead of
     * leaving a hole. The quad itself stays fixed - the strip centres its content, keeping the
     * ray's muscle memory stable (spec section 4).
     */
    private fun relayout() {
        data class Block(val width: Float, val place: (Float) -> Unit)

        val blocks = mutableListOf<Block>()
        if (audioRowEnabled) {
            blocks.add(
                Block(TRACK_BLOCK_W) { x ->
                    audioCaptionX = x
                    audioPrevRect.set(arrowRect(x + CAPTION_ZONE_W))
                    audioNextRect.set(arrowRect(x + CAPTION_ZONE_W + ARROW_W + VALUE_ZONE_W))
                }
            )
        } else {
            audioPrevRect.setEmpty()
            audioNextRect.setEmpty()
        }
        if (subsRowEnabled) {
            blocks.add(
                Block(TRACK_BLOCK_W) { x ->
                    subsCaptionX = x
                    subsPrevRect.set(arrowRect(x + CAPTION_ZONE_W))
                    subsNextRect.set(arrowRect(x + CAPTION_ZONE_W + ARROW_W + VALUE_ZONE_W))
                }
            )
        } else {
            subsPrevRect.setEmpty()
            subsNextRect.setEmpty()
        }
        blocks.add(Block(SLIDER_W) { x -> volumeTrackRect.set(x, SLIDER_TOP, x + SLIDER_W, SLIDER_TOP + SLIDER_H) })
        if (depthRowVisible) {
            blocks.add(Block(SLIDER_W) { x -> depthTrackRect.set(x, SLIDER_TOP, x + SLIDER_W, SLIDER_TOP + SLIDER_H) })
        } else {
            depthTrackRect.setEmpty()
        }

        val totalWidth = blocks.sumOf { it.width.toDouble() }.toFloat()
        val free = ROW_AREA_RIGHT - ROW_AREA_LEFT - totalWidth
        val gap = maxOf(MIN_BLOCK_GAP, free / (blocks.size + 1))
        var cursor = ROW_AREA_LEFT + gap
        for (block in blocks) {
            block.place(cursor)
            cursor += block.width + gap
        }
    }

    private val bgPaint = Paint().apply {
        color = Color.argb(BG_ALPHA, BG_R, BG_G, BG_B)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val accentPaint = Paint().apply {
        color = Color.rgb(ACCENT_R, ACCENT_G, ACCENT_B)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val exitPaint = Paint().apply {
        color = Color.rgb(EXIT_R, EXIT_G, EXIT_B)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val trackPaint = Paint().apply {
        color = Color.argb(TRACK_ALPHA, TRACK_GREY, TRACK_GREY, TRACK_GREY_B)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = TEXT_SIZE
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val captionPaint = Paint().apply {
        color = Color.WHITE
        textSize = CAPTION_TEXT_SIZE
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val headerPaint = Paint().apply {
        color = Color.rgb(HEADER_GREY_RG, HEADER_GREY_RG, HEADER_GREY_B)
        textSize = HEADER_TEXT_SIZE
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val statusPaint = Paint().apply {
        color = Color.rgb(STATUS_R, STATUS_G, STATUS_B)
        textSize = STATUS_TEXT_SIZE
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }

    private val cursorPaint = Paint().apply {
        color = Color.argb(CURSOR_ALPHA, CURSOR_R_CHANNEL, CURSOR_G_CHANNEL, CURSOR_B_CHANNEL)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun render(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        canvas.drawRoundRect(
            RectF(MARGIN, MARGIN, WIDTH - MARGIN, HEIGHT - MARGIN),
            CORNER_RADIUS,
            CORNER_RADIUS,
            bgPaint
        )

        drawHeaderLine(canvas)

        if (seekRowVisible) {
            drawSeekBar(canvas)
        }

        drawButton(canvas, prevRect, prevLabel, accentPaint)
        drawButton(canvas, playPauseRect, if (isPlaying) pauseLabel else playLabel, accentPaint)
        drawButton(canvas, nextRect, nextLabel, accentPaint)

        // S1238: absent rows are not painted - one track is not a choice, mono has no depth.
        if (audioRowEnabled) {
            drawTrackBlock(canvas, audioCaptionX, audioCaption, audioTrackLabel, audioPrevRect, audioNextRect)
        }
        if (subsRowEnabled) {
            drawTrackBlock(canvas, subsCaptionX, subsCaption, subtitleTrackLabel, subsPrevRect, subsNextRect)
        }

        drawSlider(canvas, volumeCaption, volumeTrackRect, volume)
        if (depthRowVisible) {
            drawSlider(canvas, depthCaption, depthTrackRect, depth)
        }

        drawButton(canvas, exitRect, exitLabel, exitPaint)
        drawButton(canvas, helpRect, helpLabel, accentPaint)
        drawButton(canvas, hideRect, hideLabel, accentPaint)

        drawCursor(canvas)
    }

    private fun drawHeaderLine(canvas: Canvas) {
        val status = "FPS %.1f".format(fps)
        val statusWidth = statusPaint.measureText(status)
        // S1232: the header text now lives between the two terminal buttons, so its budget is
        // measured from exit's right edge rather than from a fixed left margin.
        // S1223: right-aligned against HELP rather than HIDE - HELP is now the leftmost of the
        // right-hand buttons, so it is what the text must stop short of.
        val nameLeft = exitRect.right + MARGIN
        val nameBudget = helpRect.left - nameLeft - statusWidth - STATUS_GAP - MARGIN
        val name = ellipsize("FILE: $currentFilename", headerPaint, nameBudget)
        canvas.drawText(name, nameLeft, HEADER_BASELINE, headerPaint)
        canvas.drawText(status, helpRect.left - STATUS_GAP - statusWidth, HEADER_BASELINE, statusPaint)
    }

    /**
     * S1239: position bar plus an elapsed/total label, sharing the slider paints so the strip has
     * one visual language. The label is right-aligned to the hide button rather than left-aligned
     * to the track's end, so a film crossing the one-hour mark grows the text leftwards into the
     * reserved zone instead of drifting towards the button.
     */
    private fun drawSeekBar(canvas: Canvas) {
        canvas.drawRoundRect(seekTrackRect, SLIDER_RADIUS, SLIDER_RADIUS, trackPaint)
        val fillRight = seekTrackRect.left + seekTrackRect.width() * seekProgress.coerceIn(0f, 1f)
        canvas.drawRoundRect(
            RectF(seekTrackRect.left, seekTrackRect.top, fillRight, seekTrackRect.bottom),
            SLIDER_RADIUS,
            SLIDER_RADIUS,
            accentPaint
        )
        canvas.drawCircle(fillRight, seekTrackRect.centerY(), SEEK_KNOB_R, textPaint)
        val labelWidth = statusPaint.measureText(timeLabel)
        canvas.drawText(timeLabel, helpRect.left - MARGIN - labelWidth, SEEK_TIME_BASELINE, statusPaint)
    }

    /** S0964: one cycle block - caption, `<` arrow, current value, `>` arrow, laid out inline. */
    private fun drawTrackBlock(
        canvas: Canvas,
        captionX: Float,
        caption: String,
        value: String,
        prevArrow: RectF,
        nextArrow: RectF
    ) {
        val baseline = ROW_CENTER + CAPTION_BASELINE_SHIFT
        val captionBudget = prevArrow.left - captionX - MARGIN
        canvas.drawText(ellipsize(caption, captionPaint, captionBudget), captionX, baseline, captionPaint)
        drawButton(canvas, prevArrow, "<", accentPaint)
        drawButton(canvas, nextArrow, ">", accentPaint)
        val shown = ellipsize(value, textPaint, nextArrow.left - prevArrow.right - MARGIN)
        val valueX = (prevArrow.right + nextArrow.left) / 2f - textPaint.measureText(shown) / 2f
        canvas.drawText(shown, valueX, baseline, textPaint)
    }

    private fun drawSlider(canvas: Canvas, caption: String, track: RectF, value: Float) {
        val shown = ellipsize(caption, captionPaint, track.width())
        canvas.drawText(shown, track.left, SLIDER_CAPTION_BASELINE, captionPaint)
        canvas.drawRoundRect(track, SLIDER_RADIUS, SLIDER_RADIUS, trackPaint)
        val fillRight = track.left + track.width() * value
        canvas.drawRoundRect(
            RectF(track.left, track.top, fillRight, track.bottom),
            SLIDER_RADIUS,
            SLIDER_RADIUS,
            accentPaint
        )
        canvas.drawCircle(fillRight, track.centerY(), SLIDER_KNOB_R, textPaint)
    }

    private fun drawCursor(canvas: Canvas) {
        if (!hasHover) return
        canvas.drawCircle(hoverX, hoverY, CURSOR_R, cursorPaint)
    }

    private fun drawButton(canvas: Canvas, rect: RectF, text: String, paint: Paint) {
        canvas.drawRoundRect(rect, BUTTON_RADIUS, BUTTON_RADIUS, paint)
        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        canvas.drawText(text, rect.centerX() - textBounds.centerX(), rect.centerY() - textBounds.centerY(), textPaint)
    }

    /** Canvas has no TextUtils here, so trim manually until the string fits [maxWidth]. */
    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        var shown = text
        while (shown.length > ELLIPSIZE_MIN_LEN && paint.measureText(shown) > maxWidth) {
            shown = shown.dropLast(ELLIPSIZE_DROP) + ".."
        }
        return shown
    }

    private fun transportButtonRect(index: Int): RectF {
        val left = TRANSPORT_LEFT + index * (TRANSPORT_BTN_W + TRANSPORT_GAP)
        return RectF(left, ROW_TOP, left + TRANSPORT_BTN_W, ROW_BOTTOM)
    }

    private fun arrowRect(left: Float) = RectF(left, ROW_TOP, left + ARROW_W, ROW_BOTTOM)
}
