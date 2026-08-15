# Phase 02 - Flavor overlay implementations

**Strategic spec:** [`../S0930_quick-audio-recorder-stop-overlay.md`](../S0930_quick-audio-recorder-stop-overlay.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-07-04
**Completed:** 2026-07-04

---

## Objective

Implement `QuickRecorderIndicatorController` for `standardScreenCapture` and `noLegal` - the exact two source sets that already carry the `SYSTEM_ALERT_WINDOW` permission for the S0796 gesture engine (research §2) - each drawing the reused S0774 pill layout over the current foreground app.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done and the project builds.
- [ ] Research artifact [`research/01__overlay-hosting-mechanism.md`](research/01__overlay-hosting-mechanism.md) read - do not re-derive the permission matrix or layout-reuse decision, they are settled there.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/standardScreenCapture/java/com/sza/fastmediasorter/widget/QuickRecorderIndicatorControllerImpl.kt` | New | ≤ 70 |
| `app_v2/src/standardScreenCapture/java/com/sza/fastmediasorter/di/ScreenCaptureModule.kt` | Modified | ≤ 30 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/widget/QuickRecorderIndicatorControllerImpl.kt` | New | ≤ 70 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/ScreenCaptureModule.kt` | Modified | ≤ 30 |

> **Flavor placement.** Both new classes are flavor-only per `dev/FLAVOR_DEVELOPMENT_RULES.md` - they live under their flavor's `src/<flavor>/java/...`, never under `src/main/java/`. The contract interface (Phase 01) already lives in `src/main/java/`. No layout file is touched - `R.layout.view_recording_indicator` (S0774, `src/main/res/`) is reused verbatim, orientation-agnostic, no `layout-land` counterpart needed (confirmed in research §3).

---

## Steps

### Step 02.1 - standardScreenCapture implementation

**Files:** `app_v2/src/standardScreenCapture/java/com/sza/fastmediasorter/widget/QuickRecorderIndicatorControllerImpl.kt`, `app_v2/src/standardScreenCapture/java/com/sza/fastmediasorter/di/ScreenCaptureModule.kt`
**Depends on:** - start of phase (consumes Phase 01's interface)

**Prompt for developer:**

> Create `QuickRecorderIndicatorControllerImpl` implementing `com.sza.fastmediasorter.widget.QuickRecorderIndicatorController`, constructor `@Inject constructor(@ApplicationContext private val appContext: Context)`. Hold `private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager` and `private var indicatorView: View? = null`.
>
> `isAvailable(context)` returns `Settings.canDrawOverlays(context)`.
>
> `show(context, onStop)`: no-op if `indicatorView != null`; else inflate `R.layout.view_recording_indicator` via `LayoutInflater.from(appContext)` with a null root; set `view.contentDescription` to `appContext.getString(R.string.quick_recorder_notification_recording)`; find `R.id.recordingIndicatorPauseResume` and `R.id.recordingIndicatorCancel` and set both `visibility = View.GONE` (this indicator only ever stops+saves, no pause/cancel); find `R.id.recordingIndicatorStop`, set its `contentDescription` to `appContext.getString(R.string.quick_recorder_action_stop)` and `setOnClickListener { onStop() }`. Build `WindowManager.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, TYPE_APPLICATION_OVERLAY, FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)` with `gravity = Gravity.TOP or Gravity.END` and `title = "quick_recorder_indicator"`. Call `windowManager.addView(view, params)`, store `indicatorView = view`.
>
> `updateElapsed(text)`: `indicatorView?.findViewById<TextView>(R.id.recordingIndicatorTimer)?.text = text`.
>
> `hide()`: if `indicatorView` non-null, `windowManager.removeViewImmediate(it)` then set `indicatorView = null`; no-op otherwise.
>
> KDoc: S0930, reuses the S0774 pill layout verbatim so the app never shows two different recording-pill styles; not built on `ScreenGestureOverlayManager` - that class exists to intercept drag gestures (`FLAG_NOT_TOUCH_MODAL` pass-through), the opposite of a clickable pill (research §1).
>
> Then add a second binding to the existing `ScreenCaptureModule.kt` in this source set (do not create a new module file - it already exists with one `@Binds @IntoSet` method for the gesture-overlay controller): `@Binds @IntoSet abstract fun bindQuickRecorderIndicatorController(impl: QuickRecorderIndicatorControllerImpl): QuickRecorderIndicatorController`.

**Verification:**

- `Glob` - `app_v2/src/standardScreenCapture/java/com/sza/fastmediasorter/widget/QuickRecorderIndicatorControllerImpl.kt` exists.
- `Grep` - `class QuickRecorderIndicatorControllerImpl` and `Settings.canDrawOverlays` both present in that file.
- `Grep` - `bindQuickRecorderIndicatorController` present in `app_v2/src/standardScreenCapture/java/com/sza/fastmediasorter/di/ScreenCaptureModule.kt`, alongside the pre-existing `bindController`.
- `Grep -n "Log\.d\("` in the new file returns zero hits (Timber only).

**Status:** `[x]` done

**Step Log:**

- 2026-07-04 - Verification 4/4 PASS. Files: `app_v2/src/standardScreenCapture/java/com/sza/fastmediasorter/widget/QuickRecorderIndicatorControllerImpl.kt` (new, 62 LOC), `app_v2/src/standardScreenCapture/java/com/sza/fastmediasorter/di/ScreenCaptureModule.kt` (+7 LOC).

---

### Step 02.2 - noLegal implementation

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/widget/QuickRecorderIndicatorControllerImpl.kt`, `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/ScreenCaptureModule.kt`
**Depends on:** - start of phase (consumes Phase 01's interface; independent of Step 02.1 - different source set, same interface)

**Prompt for developer:**

> Mirror Step 02.1 exactly in the `noLegal` source set - same class body, same package `com.sza.fastmediasorter.widget`, same constructor, same four overrides. `noLegal` declares `SYSTEM_ALERT_WINDOW` unconditionally (research §2), so `isAvailable` needs no additional accessibility-path fallback beyond `Settings.canDrawOverlays(context)` - the indicator is a passive view, not a touch-intercepting strip, so it does not need the `TYPE_ACCESSIBILITY_OVERLAY` path that `ScreenGestureOverlayControllerImpl`'s noLegal variant uses for its gesture strip.
>
> Add the same second binding to the existing `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/ScreenCaptureModule.kt` (it already exists with one `@Binds @IntoSet` method for the gesture-overlay controller - do not create a new module file).

**Verification:**

- `Glob` - `app_v2/src/noLegal/java/com/sza/fastmediasorter/widget/QuickRecorderIndicatorControllerImpl.kt` exists.
- `Grep` - `class QuickRecorderIndicatorControllerImpl` present in that file.
- `Grep` - `bindQuickRecorderIndicatorController` present in `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/ScreenCaptureModule.kt`.
- `.\a.ps1 fkn` passes (exit 0) - noLegal-flavor Kotlin compile, per project convention for flavor-touched files.

**Status:** `[x]` done

**Step Log:**

- 2026-07-04 - Verification 4/4 PASS (`.\a.ps1 fkn` -> BUILD SUCCESSFUL in 36s). Files: `app_v2/src/noLegal/java/com/sza/fastmediasorter/widget/QuickRecorderIndicatorControllerImpl.kt` (new, 63 LOC), `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/ScreenCaptureModule.kt` (+7 LOC).

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles on both touched flavors - `.\a.ps1 fkn` (noLegal, BUILD SUCCESSFUL) and a targeted `gradlew.bat :app_v2:compileStandardDebugKotlin -Pfms.edgeGestureOverlay=on` (standard-with-flag, BUILD SUCCESSFUL - proves the `standardScreenCapture` source set compiles; the default-flag-off `.\a.ps1 fc` would not even mount that source set).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entries added for all four files via `.\scripts\add_to_dev_log.ps1` (via `post-change.ps1`).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (two new flavor-only classes; `-NoFlavors` hint deferred to Phase 04 per plan).

---

## Handoff Notes to Next Phase

Both flavor bindings exist; `Set<QuickRecorderIndicatorController>` resolves to exactly one controller on standard-with-`fms.edgeGestureOverlay=on` and on noLegal, and to an empty set everywhere else. Phase 03 wires `QuickAudioRecorderService` (src/main) to consume this set - no flavor-specific code is added there.

---

## Rollback Plan

Revert the phase commit - two new flavor-only classes plus one added `@Binds` method per flavor's existing module; no data migration, no user-facing surface changed (nothing calls `show()` yet until Phase 03).
