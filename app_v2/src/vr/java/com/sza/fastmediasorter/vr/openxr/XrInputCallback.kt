package com.sza.fastmediasorter.vr.openxr

/**
 * Callback invoked by the C++ OpenXR action system whenever an edge event
 * (button press, stick threshold crossing, grip zoom delta, pinch click, ..) is produced.
 *
 * Implementations run on the xr-render-thread and MUST NOT touch UI or
 * ViewModel state directly — use `mainHandler.post{}` to hop to the main
 * looper before dispatching commands.
 *
 * Two event families flow through this interface:
 *   - **Discrete events** via [onInputEvent] — rising-edge button / gesture signals.
 *   - **Continuous pointer stream** via [onPointerMove] — per-frame raycast NDC updates
 *     from hand-tracking aim pose; dispatched at the render cadence (≈72/90/120 Hz).
 *     Consumers should throttle on the main thread to avoid flooding the UI layer.
 */
interface XrInputCallback {
    /**
     * @param type one of [XrInputEventType] constants.
     * @param hand one of [XrHand] constants.
     * @param value analog payload for float/vector2f events (e.g. grip delta,
     *              stick magnitude, pinch strength). Zero for pure boolean events.
     * @param source one of [XrInputSource] constants — `CONTROLLER` for Touch input,
     *              `HAND` for hand-tracking-derived input. UI layers use this to
     *              decide whether to trigger haptics (controllers) or audio (hands).
     */
    fun onInputEvent(type: Int, hand: Int, value: Float, source: Int)

    /**
     * Hand-tracking aim ray hit-test result against the UI composition plane,
     * expressed in normalised device coordinates ∈ [-1, 1]² (origin = plane centre,
     * +X right, +Y up).
     *
     * Emitted every frame while hand tracking is the active input modality; no
     * call is made while controllers drive input. Consumers must throttle on the
     * main thread before forwarding to view hierarchies.
     *
     * @param hand one of [XrHand] constants.
     * @param ndcX horizontal position ∈ [-1, 1], or outside when the ray misses the plane.
     * @param ndcY vertical position ∈ [-1, 1], or outside when the ray misses the plane.
     */
    fun onPointerMove(hand: Int, ndcX: Float, ndcY: Float) {}

    /**
     * Controller aim-ray hit-test result against the UI composition plane.
     * Emitted every frame while Touch controllers are the active input source.
     * NDC semantics identical to [onPointerMove] for hands.
     *
     * @param hand one of [XrHand] constants.
     * @param ndcX horizontal NDC ∈ [-1,1] or outside when ray misses the plane.
     * @param ndcY vertical NDC ∈ [-1,1] or outside when ray misses the plane.
     */
    fun onControllerPointerMove(hand: Int, ndcX: Float, ndcY: Float) {}

    /**
     * Resolved interactive-panel hit zone under the controller aim ray.
     * Emitted every frame while the panel is visible (Phase 04).
     *
     * @param hand one of [XrHand] constants.
     * @param zoneId hit zone ID from [com.sza.fastmediasorter.vr.render.VrInteractivePanelComposer]
     *               constants, or -1 on miss.
     * @param seekFraction fractional seek position if hovering ZONE_SEEK_SLIDER, else -1f.
     */
    fun onControllerPanelHover(hand: Int, zoneId: Int, seekFraction: Float) {}
}
