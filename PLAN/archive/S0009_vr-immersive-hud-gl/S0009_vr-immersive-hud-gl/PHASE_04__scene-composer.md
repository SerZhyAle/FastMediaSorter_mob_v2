# Phase 04 — Scene Composer (Canvas layout for all HUD elements)

**Strategic spec:** [`../spec_vr-immersive-hud-gl.md`](../spec_vr-immersive-hud-gl.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05, Phase 06, Phase 07
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Introduce a pure `VrHudState` snapshot plus a `VrHudSceneComposer` that renders the snapshot into a Canvas. Cover every indicator that the existing phone-screen video controls + `VrHudIndicatorManager` produce: progress bar (position + buffered + duration), pause/play icon, seek indicator, volume bar, zoom factor, file name + index, recenter flash, immersive-mode badge, repeat-mode icon, action badges for prev/next file and rewind/forward. Latin and Cyrillic text via the default system `Typeface`. No hookup into the activity yet — Phase 05 does that.

---

## Prerequisites

Check each before starting Step 1:

- [ ] Phase 03 is `✅ Done` — `VrHudRenderer.submit { ..canvas.. }` paints onto the quad.
- [ ] The current `VrHudIndicatorManager` API (reference only) lists the full set of indicator methods: see `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudIndicatorManager.kt`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudState.kt` | New | ≤ 150 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt` | New | ≤ 450 |
| `app_v2/src/main/res/drawable/ic_hud_*.xml` (optional vector assets) | New | ≤ 50 each |

> No XML string additions in this phase — labels are composed programmatically from existing strings (`R.string.vr_hud_*`). New user-facing strings are added in Phase 06.

---

## Steps

### Step 4.1 — Create the immutable `VrHudState` snapshot

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudState.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `VrHudState.kt` as a Kotlin `data class` with the following fields (nullable where the indicator is off):
>
> - `isPaused: Boolean` — drives the pause/play icon slot.
> - `positionMs: Long`, `bufferedMs: Long`, `totalMs: Long` — drives the progress bar. Use `-1L` to mean "no data".
> - `volumePercent: Int?` — 0..100 when the volume bar is visible, null when hidden.
> - `zoomFactor: Float?` — e.g. `1.0`, `1.5`; null when not visible.
> - `fileLabel: String?`, `fileIndex: Int?`, `fileTotal: Int?` — top-right file badge; all three null hides the badge.
> - `repeatMode: RepeatMode?` — enum `OFF / ONE / ALL`; null hides the icon.
> - `seekDeltaSec: Int?` — `±N` seconds; null hides the seek overlay.
> - `recenterFlashUntilMs: Long` — 0 when inactive.
> - `immersiveBadgeUntilMs: Long` — 0 when inactive.
> - `actionBadge: ActionBadge?` — enum `PREV_FILE / NEXT_FILE / REWIND / FORWARD / OPEN_CONTROLS / REPEAT_TOGGLE`; null when none. Acts as a momentary "button was pressed" pulse.
> - `bannerText: String?` — transient transitional-guard banner from Phase 06 (e.g. "Panel unavailable in immersive"); null hides the banner.
> - `visibleUntilMs: Long` — timestamp after which the HUD quad layer should be hidden entirely if all sub-slots expired.
>
> Mark the file with a KDoc one-liner: "Immutable snapshot of HUD state for one compose pass."

**Verification:**

- `Glob` — file exists.
- `Grep` — pattern `data class VrHudState` returns exactly one hit.
- `Grep` — pattern `enum class RepeatMode` returns exactly one hit.
- `Grep` — pattern `enum class ActionBadge` returns exactly one hit.
- File ≤ 150 LOC.
- `/build` skill compiles `vrDebug`.

**Status:** `[x]` done

---

### Step 4.2 — Implement `VrHudSceneComposer.draw(state, canvas)`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt`
**Depends on:** Step 4.1

**Prompt for developer:**

> Create `VrHudSceneComposer.kt` with class `VrHudSceneComposer(private val context: Context, private val width: Int = 1024, private val height: Int = 256)`. Expose one public method `fun draw(state: VrHudState, canvas: Canvas)`. The class owns reusable `Paint` objects (no allocations inside `draw`):
>
> - `textPaint` — default `Typeface.DEFAULT` (covers Latin + Cyrillic via system fallback stack), size ~28 px, colour white, antialias on.
> - `iconPaint` — monochrome vector icons drawn via `drawPath` or `drawText` with glyph fallback (e.g. `❚❚`, `▶`, `◀◀`, `▶▶`).
> - `barBgPaint` — black at alpha 180 for slot backgrounds.
> - `progressBgPaint` / `progressBufferedPaint` / `progressPositionPaint` — greys + accent colour.
>
> Layout (bottom strip of the HUD quad, coords in Canvas pixels):
>
> - **Progress bar** — full width minus 48 px margin, 8 px tall, y = height − 32. Draw buffered segment on top of background, then position segment on top. Time label at left (current), right (duration), 24 px above bar, 18 px text.
> - **Pause/play icon** — centre of Canvas, 64 px glyph, only when `isPaused != null`. Fades per Phase 05 ticker.
> - **Seek overlay** — centred 100 px above progress bar, text `[MM:SS / MM:SS] +N ▶▶`, only when `seekDeltaSec != null`.
> - **Volume bar** — right-edge vertical bar 16 px wide, 160 px tall, only when `volumePercent != null`. Number label above.
> - **Zoom label** — centre of Canvas, below pause icon, format `×1.5`, only when `zoomFactor != null`.
> - **File label** — top-right, format `{name_truncated}  {index} / {total}`, only when triplet present.
> - **Repeat icon** — top-right under file label (24 px offset), only when `repeatMode != null`.
> - **Recenter flash** — full-width semi-transparent white overlay for 400 ms when `recenterFlashUntilMs > now`.
> - **Immersive badge** — centred banner `Immersive ON / OFF` for 1200 ms when active.
> - **Action badge** — centre-top, pulse-style 300 ms, icon glyph matching the enum.
> - **Banner text** — centre of Canvas, black pill with text, when `bannerText != null`. Wrap to two lines if it exceeds 60 glyphs.
>
> Clear the canvas at the start of every `draw` call via `canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)` to avoid stale pixels from the previous frame.
>
> Truncate long file names to 40 glyphs + `..` (matches the existing `VrHudIndicatorManager` behaviour).

**Verification:**

- `Glob` — file exists.
- `Grep` — pattern `class VrHudSceneComposer` returns exactly one hit.
- `Grep` — pattern `fun draw(state: VrHudState, canvas: Canvas)` returns exactly one hit.
- `Grep -n "Log\.d\(" app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt` returns zero hits (Timber-only rule applies even though this file does not need logging).
- File ≤ 450 LOC.
- `/build` skill compiles `vrDebug`.

**Status:** `[x]` done

---

### Step 4.3 — Unit / preview harness: render each indicator to disk for visual diff

**Files:** `app_v2/src/vr/test/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposerTest.kt` (new, unit test)
**Depends on:** Step 4.2

**Prompt for developer:**

> Add a JVM unit test (Robolectric, which the project already uses — confirm via grep for `robolectric` in `app_v2/build.gradle.kts` before choosing; if not present, use instrumented test instead under `androidTest`). Create eight snapshot states covering:
>
> 1. All indicators off → empty transparent frame (pixel `Color.TRANSPARENT` at centre).
> 2. Progress bar only, paused, position 01:23 / 14:20, buffered 03:00.
> 3. Seek +10s.
> 4. Volume 72%.
> 5. File "18VR_The_Best_is_Yet_to_Come_7K_180.mp4" 3 / 12.
> 6. Zoom ×1.5.
> 7. Banner text "File ops unavailable in immersive".
> 8. Recenter flash active.
>
> For each, render to an `Bitmap` via `VrHudSceneComposer.draw`, write PNG to `temp/hud_preview_{N}.png` for manual inspection, and assert at least one non-transparent pixel (or zero, in case #1).

**Verification:**

- `Glob` — test file exists.
- `Grep` — pattern `fun test_progressBar_paused_rendersPositionAndBuffer` (or similar naming) returns exactly one hit.
- Manual inspection: `temp/hud_preview_*.png` files are legible, both Latin and Cyrillic glyphs render cleanly (add one Cyrillic file name to case #5 variant for the visual check).
- `/build` skill runs `./gradlew :app_v2:testVrDebugUnitTest` (or the instrumented equivalent) green.

**Status:** `[ ]` not done

---

### Step 4.4 — Wire `VrHudSceneComposer` under a manual debug launch from `VrPlayerActivity`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` (temporary debug block, removed in Phase 05 Step 5.1)
**Depends on:** Step 4.3

**Prompt for developer:**

> Backup first: `Copy-Item app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt temp/VrPlayerActivity_phase04_$(Get-Date -Format yyyyMMdd_HHmm).kt.bak` — the file is at 1443 LOC, backup is mandatory. Then in `VrPlayerActivity.onSessionReady()` callback body, add a temporary block under `#if BuildConfig.DEBUG`-style guard (Kotlin: `if (BuildConfig.DEBUG)`) that:
>
> 1. Creates `VrHudRenderer(sessionManager)` and `VrHudSceneComposer(this)`.
> 2. Calls `renderer.ensureSwapchainCreated()`.
> 3. Calls `renderer.setVisible(true)`.
> 4. Runs `renderer.submit { canvas -> composer.draw(sampleState, canvas) }` where `sampleState` is a hard-coded `VrHudState(isPaused = true, positionMs = 83_000, totalMs = 860_000, bufferedMs = 180_000, fileLabel = "18VR_The_Best_is_Yet_to_Come_7K_180_180x180_3dh.mp4", fileIndex = 3, fileTotal = 12, ..)`.
>
> Mark the block with `// TODO(phase-05): remove debug HUD sample — wiring moves into VrHudIndicatorManager delegator`.
>
> This block is proof the composer renders correctly through the real pipeline. It is removed in Phase 05 Step 5.1 when the real routing takes over.

**Verification:**

- `Grep` — pattern `TODO(phase-05): remove debug HUD sample` returns exactly one hit in `VrPlayerActivity.kt`.
- On-device test: launch an immersive video session; the HUD quad now shows a legible mock progress bar + file label + pause icon instead of the dark rectangle or the Phase 02 debug-visibility flash.
- `/build` skill compiles `vrDebug`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

All of the following must hold for this phase to flip to `✅ Done`:

- [ ] Every `Step 4.*` above is `[x] done`.
- [ ] Project compiles — `/build` on `vrDebug`.
- [ ] Unit test / preview harness passes (Step 4.3).
- [ ] On-device: the debug sample state renders legibly; Cyrillic glyphs are not replaced by squares.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] `Grep` for `TODO(phase-05): remove debug HUD sample` returns exactly one hit.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Phase 05 assumes:

- `VrHudSceneComposer.draw(state, canvas)` is a pure function — same state in, same pixels out.
- `VrHudState` is an immutable snapshot. The event router (Phase 05) produces a new state on every indicator change.
- The renderer pipeline is frame-rate-independent: `submit` is safe to call from a 2 Hz ticker AND from event callbacks on the main thread.

---

## Rollback Plan

Revert the phase commit(s). `VrHudState.kt` and `VrHudSceneComposer.kt` are newly added files; they can be deleted. The debug block in `VrPlayerActivity` reverts cleanly from the backup. Phases 01–03 remain intact and safe.
