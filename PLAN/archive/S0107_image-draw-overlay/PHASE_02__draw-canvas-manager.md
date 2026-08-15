# Phase 02 — Draw Canvas Manager

**Strategic spec:** [`../S0107_image-draw-overlay.md`](../S0107_image-draw-overlay.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Started:** 2026-05-09
**Completed:** 2026-05-09
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 5 / 5
**Started:** —
**Completed:** —

---

## Objective

Implement `ImageDrawOverlayManager` — the core manager responsible for maintaining the Draw Mode state machine, the transparent draw canvas, touch-event routing (brush / rectangle / eraser), and ViewPager swipe blocking.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Blocker "Verify ImageLoadingManager exposes loaded Bitmap" from INDEX is resolved or deferred to Phase 04.
- [ ] Working tree is clean or on a feature branch.
- [ ] Pre-edit backup: `PlayerActivity.kt` is >500 LOC — create `temp/PlayerActivity_S0107_<timestamp>.kt.bak` before any edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt` | **New** | ≤ 450 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ current + 20 |

> `PlayerActivity.kt` is currently 1070 LOC. The additions in this phase are minimal (wiring only); keep delta ≤ 30 lines. If the file would exceed 1100 lines post-edit, extract the init block to `PlayerManagerInitializer.kt` instead.

---

## Steps

### Step 2.1 — Create `ImageDrawOverlayManager` skeleton with state machine

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt` (New)
**Depends on:** — start of phase

**Prompt for developer:**

> Create `ImageDrawOverlayManager` in `ui/player/helpers/`. It must satisfy:
>
> - Constructor: `(activity: Activity, imageContainer: ViewGroup)` — `imageContainer` is the `FrameLayout` or `ViewGroup` that wraps the player's `ImageView` / `PhotoView`.
> - Internal `enum class DrawTool { BRUSH, RECTANGLE, ERASER }`.
> - Internal `enum class DrawColor(val argb: Int)` with 7 entries: WHITE(`0xFFFFFFFF`), BLACK(`0xFF000000`), GRAY(`0xFF808080`), RED(`0xFFE53935`), BLUE(`0xFF1E88E5`), GREEN(`0xFF43A047`), YELLOW(`0xFFFDD835`).
> - `var isDrawModeActive: Boolean = false` (private set).
> - `var selectedTool: DrawTool = DrawTool.BRUSH` (public mutable).
> - `var selectedColor: DrawColor = DrawColor.BLACK` (public mutable).
> - `fun enterDrawMode()`: sets `isDrawModeActive = true`, adds the canvas view to `imageContainer`, blocks swipe. Calls `Timber.d("S0107: enterDrawMode")`.
> - `fun exitDrawMode(save: Boolean)`: if `save = false`, clears the canvas and hides it; if `save = true`, delegates to the save callback (wired in Phase 04). Sets `isDrawModeActive = false`. Calls `Timber.d("S0107: exitDrawMode save=$save")`.
> - `fun getOverlayBitmap(): Bitmap?` — returns the current canvas Bitmap (null if draw mode was never entered or canvas not initialised).
> - Internal callback interface `DrawOverlaySaveCallback { fun onSaveRequested(overlayBitmap: Bitmap) }`.
> - A `var saveCallback: DrawOverlaySaveCallback? = null` property.
> - No business logic beyond state transitions — no file I/O, no UseCase calls.

**Verification:**

- `Glob` — `ImageDrawOverlayManager.kt` exists at the expected path.
- `Grep` — `class ImageDrawOverlayManager` matches exactly once.
- `Grep` — `fun enterDrawMode` and `fun exitDrawMode` present.
- `Grep` — `Timber.d("S0107: enterDrawMode")` present.
- `Grep` — `Log\.d\(` returns zero hits in `ImageDrawOverlayManager.kt`.

**Status:** `[ ]` not done

---

### Step 2.2 — Implement `DrawCanvasView` (inner custom View for touch drawing)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> Inside `ImageDrawOverlayManager.kt`, add a private inner class `DrawCanvasView(context: Context)` extending `View`. It must:
>
> - Hold a `Bitmap` (ARGB_8888, same dimensions as the view on first layout) and a `Canvas` wrapping it.
> - Handle `onSizeChanged` to re-create the Bitmap at the correct size.
> - Handle `onDraw` to draw the Bitmap onto the provided `Canvas`.
> - Handle `onTouchEvent`:
>   - `ACTION_DOWN`: start a new stroke or shape.
>   - `ACTION_MOVE`: extend the stroke (BRUSH: draw a line segment from last point; RECTANGLE: invalidate to show preview).
>   - `ACTION_UP`: commit the shape (BRUSH: finalize; RECTANGLE: blit the rectangle outline onto the bitmap; ERASER: same as brush with `PorterDuff.Mode.CLEAR`).
> - Active `Paint` is configured from the parent manager's `selectedTool` and `selectedColor` on each `ACTION_DOWN`.
> - BRUSH stroke width: `12f` px. ERASER width: `24f` px. Both use `Paint.Cap.ROUND`.
> - RECTANGLE: drawn as outline only (`paint.style = Paint.Style.STROKE`, width `6f` px).
> - `fun clearCanvas()`: fills the bitmap with transparent pixels (`Color.TRANSPARENT` + `PorterDuff.Mode.CLEAR`).
> - `fun getBitmap(): Bitmap` returns the current Bitmap.

**Verification:**

- `Grep` — `class DrawCanvasView` present in `ImageDrawOverlayManager.kt`.
- `Grep` — `PorterDuff.Mode.CLEAR` present (eraser path).
- `Grep` — `onTouchEvent` present.
- `Grep` — `Log\.d\(` returns zero hits in the file.

**Status:** `[ ]` not done

---

### Step 2.3 — Block ViewPager swipe during Draw Mode

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> In `enterDrawMode()`, after adding the canvas view, call `imageContainer.requestDisallowInterceptTouchEvent(true)` to block parent ViewPager from stealing touch events. In `exitDrawMode()`, call `imageContainer.requestDisallowInterceptTouchEvent(false)` to restore normal swipe behaviour. If the player uses a `ViewPager2` or custom gesture detector rather than a `ViewGroup` intercept, locate the equivalent swipe-lock mechanism in `PlayerGestureManager`/`PlayerGestureSetupManager` and disable it for the duration of Draw Mode via a boolean flag set on that class.

**Verification:**

- `Grep` — `requestDisallowInterceptTouchEvent` present in `ImageDrawOverlayManager.kt`.
- `Grep` — `isDrawModeActive` toggled in both `enterDrawMode` and `exitDrawMode`.

**Status:** `[ ]` not done

---

### Step 2.4 — Instantiate and wire `ImageDrawOverlayManager` in player

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`

**Depends on:** Steps 2.1, 2.2, 2.3

**Prompt for developer:**

> In `PlayerManagerInitializer` (or in `PlayerActivity.setupViews()` if the initializer pattern is not used for this class), construct `ImageDrawOverlayManager`:
>
> ```kotlin
> imageDrawOverlayManager = ImageDrawOverlayManager(
>     activity = this,
>     imageContainer = binding.imageContainer  // identify the correct ViewGroup id from activity_player_unified.xml
> )
> ```
>
> Remove the `// TODO S0107 Phase 02` comment added in Step 1.3. The `lateinit var imageDrawOverlayManager` property in `PlayerActivity` is now satisfied.

**Verification:**

- `Grep` — `imageDrawOverlayManager = ImageDrawOverlayManager(` present in source.
- `Grep` — `TODO S0107 Phase 02` returns zero hits across the whole project.
- `Grep` — `Log\.d\(` returns zero hits in modified files.

**Status:** `[ ]` not done

---

### Step 2.5 — Handle Back press and orientation lock in Draw Mode

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt`
**Depends on:** Steps 2.1–2.4

**Prompt for developer:**

> Add `fun handleBackPress(): Boolean` — returns `true` if Draw Mode was active and was cancelled (calls `exitDrawMode(save = false)`), `false` otherwise. The caller (`PlayerActivity.onBackPressedDispatcher` or `PlayerKeyboardHandler`) must check this before its own back-navigation logic.
>
> In `PlayerActivity` (or the back-press handler manager), insert:
>
> ```kotlin
> if (::imageDrawOverlayManager.isInitialized && imageDrawOverlayManager.handleBackPress()) return
> ```
>
> For orientation lock: in `enterDrawMode()`, add `activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED`; in `exitDrawMode()`, add `activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED`. No `onConfigurationChanged` handler is needed — rotation is impossible while Draw Mode is active (strategic §6-3 resolved, ADR-4).

**Verification:**

- `Grep` — `fun handleBackPress` present in `ImageDrawOverlayManager.kt`.
- `Grep` — `imageDrawOverlayManager.handleBackPress()` present in `PlayerActivity.kt` or back-press handler.
- `Grep` — `SCREEN_ORIENTATION_LOCKED` present in `ImageDrawOverlayManager.kt`.
- `Grep` — `SCREEN_ORIENTATION_UNSPECIFIED` present in `ImageDrawOverlayManager.kt`.
- `Grep` — `Log\.d\(` returns zero hits in modified files.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 2.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` run — new `ImageDrawOverlayManager.kt` registered.
- [ ] `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2` run.

---

## Handoff Notes to Next Phase

- `ImageDrawOverlayManager` is constructed and wired; `enterDrawMode()` and `exitDrawMode()` are callable.
- `DrawCanvasView` handles all three tools and exposes `getBitmap()`.
- Back press cancels Draw Mode; rotation is impossible while Draw Mode is active (orientation is locked).
- Phase 03 can now add the toolbar layout that the manager will show/hide.

---

## Rollback Plan

Revert phase commit(s). `ImageDrawOverlayManager.kt` is new — deletion leaves no residue. `PlayerActivity.kt` changes are confined to construction and back-press wiring.
