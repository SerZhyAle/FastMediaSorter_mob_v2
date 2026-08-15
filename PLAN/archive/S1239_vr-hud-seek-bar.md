# S1239 - The immersive HUD has no way to seek within a film

**Status:** Archived
**Priority:** 70

<!-- auto-approved by /spec-all - 2026-07-29 -->

## 0. Raw capture

Owner, 2026-07-28:

> в HUD показывать полосу прокрутки чтобы можно было прокрутить позицию в кино

## 1. Problem

The immersive strip has PREV / PLAY-PAUSE / NEXT, volume and stereo depth, but no position. A film can be started and paused in VR and not navigated - to skip a scene the user must leave immersive, seek in the flat player, and re-enter, which costs an OpenXR session teardown and rebuild each time.

## 2. What is missing technically

`VrDiagnosticPlaybackController` owns the `ExoPlayer` but exposes only lifecycle (`start`, `release`) and the listener callbacks. Neither `currentPosition` nor `duration` is surfaced to the HUD, and nothing polls them.

So this ticket needs three things that do not exist yet:

1. **Position/duration exposure** from the playback controller.
2. **A repaint cadence** for a moving value. The strip repaints on state change only, deliberately - S0290's rule is that native-driven UI updates must be rare, never per frame, and S0964 debounces repaint bursts to ~100 ms. A progress bar that advances needs a periodic repaint, which is exactly what that rule forbids. The resolution is probably a slow tick (once or twice per second) that only runs while the strip is visible - but it must be designed against the rule, not in spite of it.
3. **Scrub interaction.** The ray already drives the volume and depth sliders through `HudInteractionDispatcher`, so the mechanism exists; seek differs in that the value must not fight the playback updates while the user is dragging (the classic seek-bar race: the bar jumps back to the player position mid-drag).

## 3. Owner decisions (2026-07-28)

- **Seek on release only.** A drag moves the handle and previews the target position; the actual seek is issued when the handle is let go. Rationale the owner accepted: continuous seeking on a 44 GB network file thrashes the buffer, and seek-on-release is what most video UIs do for scrub.
- **Show elapsed/total as text next to the bar.** The strip's horizontal budget must therefore be laid out together with **S1238** (which frees space by hiding inapplicable controls) and with **S1232**, whose two terminal buttons now sit at opposite ends of the strip and reserve a slot on each edge. Three tickets share one row - do not size this bar in isolation.
- **Thumbstick left/right also seeks** (**S1240** decision 1). This bar is no longer the only seek entry point. Both must drive one seek path, so a scrub begun on the stick and finished on the bar cannot fight itself, and the stick must respect the same seek-on-release contract rather than issuing a seek per axis sample.

Still a requirement, not a question: live/unbounded sources have no duration. Streams cannot enter immersive today (**S1218**), so this is not urgent, but the bar - and now the stick axis - must degrade rather than divide by zero if that changes.

### 3.3 Owner inputs (Approval gate)

- **Placement:** the bar gets its own band in the 44 px of dead strip between the header line and the control row, not a slot inside the control row - see §4 for the arithmetic that forces this.
- **Flavors:** `vr` only. Every file this ticket touches lives in `app_v2/src/vr/`, which no other flavor compiles.
- **Localization:** none. The immersive HUD is deliberately unlocalized (`DECODE_FAILED_LABEL` comment in the host), and elapsed/total is digits plus a slash.
- **Related tickets:** S1228, S1232, S1238, S1240, S1218.

## 4. Where the bar can physically go

The control row is full. Measured against the current constants in `HudCanvasRenderer`, the reflow area is 1836 px wide (`ROW_AREA_LEFT` 700 to `ROW_AREA_RIGHT` 2536), and the worst case S1238 already packs into it - audio block 608 + subs block 608 + volume slider 240 + depth slider 240, with the 40 px minimum gap five times - lands the last slider's right edge at 2556. That is 20 px past the panel background and 4 px from the texture edge. There is no width to give a seek bar, and taking width from the row would make that overflow worse.

Vertically there is room. The header band ends at y=132 (`TERMINAL_TOP` 24 + `TERMINAL_H` 108) and the control row starts at y=176 (`ROW_TOP`), leaving a 44 px gap that today paints nothing. A 24 px track centred in it spans 140..164; a 20 px knob radius spans 132..172, clearing the terminal buttons above and the control row below without moving either.

Horizontally the band inherits the S1232 constraint for free: it starts after the exit button (`MARGIN` + `TERMINAL_W` + `MARGIN` = 268) and stops before the hide button, reserving a 400 px zone at the right end for the elapsed/total text. That gives a 1624 px track - wider than any control in the row, which is what a scrub target should be.

This is the answer to the owner's "three tickets share one row": the bar does not join that row at all, so S1238's reflow and S1232's terminal buttons are untouched.

## 5. Non-goals

- Fixing the 20 px control-row overflow found in §4. That is S1238's `relayout()`, parked as **S1278** because the fix is a trade-off (tighter gaps, narrower zones, or a smaller slider) rather than a one-liner.
- Any change to the thumbstick seek binding. S1240 owns it; this ticket only makes both callers share one code path.
- A scrub preview frame (thumbnail) - the immersive decoder has no cheap seek-preview path.

## 6. Phases

### Phase 01 - One seek path and a position readout

- [ ] In `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/HudPlaybackController.kt`, extract the body of `seekBy` into a private `seekResolved(resolve: (ExoPlayer, Long) -> Long)` that posts to the main handler, reads `duration`, skips on `C.TIME_UNSET`/non-positive, and clamps the resolved target into `0..duration`.
  - Keep the existing `Timber.d("S1240: seek skipped, duration unknown")` line verbatim inside it - it is S1240's active probe tag and must stay greppable for that ticket's removal step.
- [ ] Re-express `seekBy(deltaMs)` as a call into `seekResolved`, so the relative and absolute seeks cannot diverge on clamping or the unset-duration guard.
- [ ] Add `seekToFraction(fraction: Float)` on the same helper, resolving to `duration * fraction`.
- [ ] Add `positionOrNull(): PlaybackPosition?` returning current position and duration, or `null` when there is no player or the duration is unset. Document it as main-thread only - it reads `ExoPlayer` synchronously rather than posting, because its only caller is a main-thread tick.
- **Verification:** `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Code -Flavor Vr` exits 0.

### Phase 02 - Paint the band and hit-test it

- [ ] In `HudCanvasRenderer`, add the band constants from §4 (`SEEK_TOP`, `SEEK_H`, `SEEK_KNOB_R`, `SEEK_LEFT`, `TIME_ZONE_W`) and the model fields: `seekRowVisible`, `seekProgress` (0..1), `timeLabel`.
- [ ] Add `seekTrackRect` as a `RectF` built from those constants, mirroring how `volumeTrackRect` is both painted and hit-tested from one instance.
- [ ] Paint the band in `render` between the header line and the control row: track, filled portion, knob, and the time text right-aligned to `hideRect.left - MARGIN`. Skip the whole band when `seekRowVisible` is false.
- [ ] In `HudInteractionDispatcher`, add `onSeekPreview(fraction: Float)` and `onSeekCommit(fraction: Float)` to `InteractionListener`, both with no-op defaults so the banner-only diagnostic call site is unaffected.
- [ ] Implement the scrub state machine in the dispatcher: a press inside the band starts a scrub, further motion while held updates `renderer.seekProgress` and calls `onSeekPreview`, and the release calls `onSeekCommit` once. Losing hover mid-scrub commits at the last previewed position rather than cancelling, so the bar and the player can never disagree about where the film is.
- [ ] Expand the band's hit target asymmetrically - up into the empty header gap, down only as far as `ROW_TOP` - because the band overlaps the transport buttons horizontally and a symmetric expansion like the sliders' would let one ray hit both.
- **Verification:** same fast VR compile exits 0; `seekTrackRect` is read by both `render` and the dispatcher.

### Phase 03 - Tick cadence and host wiring

- [ ] Add `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/HudSeekProgressTicker.kt`: a `Handler`-driven 1 Hz tick that re-arms itself only while a `shouldRun` predicate holds, so a state change the host forgot to report stops it within one tick instead of leaking.
- [ ] In `DiagnosticXrActivity`, hoist the per-repaint `ByteArray(buf.remaining())` in `renderPanelHud` into a reusable field. At 3.7 MB per copy this allocation was harmless while repaints were state-driven; a 1 Hz tick turns it into 3.7 MB/s of garbage on a 512 MB-heap headset.
- [ ] Wire the ticker: start it when video playback starts and on summon, stop it in `onPause`, and have its predicate require panel HUD mode, a visible strip, a playing player and no scrub in progress.
- [ ] On each tick, read `positionOrNull()`, write `seekProgress` and `timeLabel` (`android.text.format.DateUtils.formatElapsedTime`, which already yields `M:SS`/`H:MM:SS` and adds no seventh copy of the project's duplicated duration formatter), then `scheduleHudPanelRepaint()`.
- [ ] Implement `onSeekPreview` (update label from the previewed fraction, repaint, no seek) and `onSeekCommit` (`playbackController.seekToFraction`) on the host's listener; give the commit the same `hapticBridge.triggerClickFeedback()` the other controls use.
- [ ] Track HUD visibility in a host field, since `runtime.setHudVisible` is write-only and the ticker predicate needs to read it.
- **Verification:** fast VR compile exits 0; `pwsh -NoProfile -File scripts/post-change.ps1 -ChangeType Kotlin -ScopeToFile` reports PASS.

## 7. Risks

- **The band is the first periodic repaint the HUD has ever had.** A full 2560x360 texture upload once a second is the cost; the allocation fix in Phase 03 removes the garbage half of it, but the upload itself is unavoidable because `queueHud` has no partial-update path.
- **Quest-only verification.** Nothing here is observable on an emulator - the immersive session needs a headset. This ticket ends in `BlockNeedUserTest` and joins the S1228/S1232/S1238/S1240 stack already waiting on one headset session.
- **Sizing is arithmetic, not observation.** The 44 px band was measured from constants, not seen. If S1238's row proves wrong in the headset, the band is unaffected - it does not share that row - but the strip as a whole may still need a rethink.

## 8. User impact

New capability - the immersive HUD can seek within a film, with elapsed and total shown next to the bar.

## 9. Related

- **S1240** - shipped `HudPlaybackController.seekBy`, the first seek the immersive session ever had; this ticket makes the bar and the thumbstick share it.
- **S1238** - conditional rows; §4 measures its packing and finds the row full.
- **S1232** - the terminal buttons that bound the band horizontally.
- **S1228** - the strip itself.
- **S1233** - PREV/NEXT navigate a one-element playlist today; seeking within a film and moving between films are separate gaps that will be reported together by users.
- **S1218** - streams cannot enter immersive, which is why the unset-duration path is a guard rather than a feature.
