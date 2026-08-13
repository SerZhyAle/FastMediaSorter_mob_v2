# Phase 04 - Post-capture action dispatcher

**Strategic spec:** [`../S0425_screenshot-gesture-actions.md`](../S0425_screenshot-gesture-actions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02
**Blocks:** Phase 05
**Steps done:** 0 / 4
**Started:** -
**Completed:** 2026-06-16

---

## Objective

Add `ScreenshotGestureActionDispatcher` that resolves a direction to its configured action (for the pre-capture gate) and runs a non-silent action against the saved URI (open in player / open in draw / OCR-translate / share). Teach `PhotoVideoStandaloneActivity` to auto-trigger draw or translate on launch via an extra. (ADR-3.)

---

## Prerequisites

- [ ] Phase 01 ✅ Done (enums).
- [ ] Phase 02 ✅ Done (`savedUri`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/ScreenshotGestureActionDispatcher.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | Modified | ≤ 950 |

> Dispatcher lives in `src/main` (flavor-agnostic; no `BuildConfig.IS_*` guards - OCR availability is a runtime `CapabilityAvailability` check). It is injected into the capture services (Phase 05). `PhotoVideoStandaloneActivity` is in `src/main`.

---

## Steps

### Step 04.1 - Create the dispatcher with action resolution

**Files:** `core/screencapture/ScreenshotGestureActionDispatcher.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `class ScreenshotGestureActionDispatcher @Inject constructor(private val settingsRepository: dagger.Lazy<SettingsRepository>)`. Add `suspend fun actionFor(direction: ScreenshotGestureDirection): ScreenshotGestureAction` that reads `settingsRepository.get().getSettings().first()` and returns the field for the direction (`screenshotGestureActionDown` / `Right` / `Up`). This is the pre-capture gate: callers skip capture entirely when it returns `DO_NOT_USE`. No Hilt `@Module` needed - constructor `@Inject` is sufficient.

**Verification:**

- `Glob` - `core/screencapture/ScreenshotGestureActionDispatcher.kt` exists.
- `Grep` - `class ScreenshotGestureActionDispatcher @Inject constructor` matches once.
- `Grep` - `suspend fun actionFor` present.
- `Grep` - `screenshotGestureActionDown`, `..Right`, `..Up` each referenced.

**Status:** `[ ]` not done

---

### Step 04.2 - Add `runPostSave` launching the configured route

**Files:** `core/screencapture/ScreenshotGestureActionDispatcher.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add `fun runPostSave(context: Context, action: ScreenshotGestureAction, savedUri: Uri?)`. For `SILENT_SCREENSHOT` and `DO_NOT_USE`: no-op (silent save already happened / capture was skipped upstream). For the rest, require a non-null `savedUri` (log at Timber.i and return if null - degrade to silent save). `OPEN_IN_PLAYER`: start `PhotoVideoStandaloneActivity` via `Intent(ACTION_VIEW)` with `data = savedUri`, `FLAG_ACTIVITY_NEW_TASK or FLAG_GRANT_READ_URI_PERMISSION`. `OPEN_IN_DRAW`: same plus `putExtra(PhotoVideoStandaloneActivity.EXTRA_AUTO_ACTION, AUTO_ACTION_DRAW)`. `OCR_TRANSLATE`: when `CapabilityAvailability.isTranslationAvailable()` is false, fall back to plain player open (logged); otherwise same intent plus `EXTRA_AUTO_ACTION = AUTO_ACTION_TRANSLATE`. `SHARE`: `SystemShareInvoker.invokeFiles(context, listOf(savedUri), mime = "image/png")` (the invoker adds `FLAG_ACTIVITY_NEW_TASK` for non-Activity contexts). Wrap activity starts in `runCatching` and log failures at Timber.w. No empty catch blocks.

**Verification:**

- `Grep` - `fun runPostSave` present.
- `Grep` - `FLAG_ACTIVITY_NEW_TASK` present.
- `Grep` - `SystemShareInvoker.invokeFiles` present.
- `Grep` - `CapabilityAvailability.isTranslationAvailable` present.
- `Grep` - `EXTRA_AUTO_ACTION` referenced.

**Status:** `[ ]` not done

---

### Step 04.3 - Declare auto-action extras on `PhotoVideoStandaloneActivity`

**Files:** `ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add companion constants: `const val EXTRA_AUTO_ACTION = "auto_action"`, `const val AUTO_ACTION_DRAW = "draw"`, `const val AUTO_ACTION_TRANSLATE = "translate"`. These name the post-launch one-shot action the dispatcher requests.

**Verification:**

- `Grep` - `EXTRA_AUTO_ACTION = "auto_action"` present.
- `Grep` - `AUTO_ACTION_DRAW`, `AUTO_ACTION_TRANSLATE` present.

**Status:** `[ ]` not done

---

### Step 04.4 - Trigger the auto-action once after the image is shown

**Files:** `ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> In `observeData`, inside the `if (file.path != lastShownPath)` block (after `viewManager.show(...)` and `lastShownPath = file.path`), read `intent?.getStringExtra(EXTRA_AUTO_ACTION)` once into a local guard flag so it fires a single time. For `AUTO_ACTION_DRAW` call `ensureDrawHelper().enterDrawMode()`; for `AUTO_ACTION_TRANSLATE` call `translateCurrentImage()`. Guard against re-entry on recomposition/state re-emission (e.g. set a `private var autoActionConsumed = false`). Only apply for IMAGE type.

**Verification:**

- `Grep` - `getStringExtra(EXTRA_AUTO_ACTION)` present.
- `Grep` - `enterDrawMode()` referenced from the auto-action branch.
- `Grep` - `translateCurrentImage()` referenced from the auto-action branch.
- `Grep` - `autoActionConsumed` (or equivalent one-shot guard) present.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fk`).
- [ ] `Grep` for `Log\.d\(` in touched files returns zero hits.
- [ ] No empty/broad swallowing `catch` introduced (neuroslop gate).
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`ScreenshotGestureActionDispatcher` is injectable. `actionFor(direction)` gates capture; `runPostSave(context, action, savedUri)` launches the route. `PhotoVideoStandaloneActivity` honours `EXTRA_AUTO_ACTION` for draw/translate. Phase 05 wires both into the capture pipelines.

---

## Rollback Plan

Revert phase commit. New class + additive extras only - no persisted state.
