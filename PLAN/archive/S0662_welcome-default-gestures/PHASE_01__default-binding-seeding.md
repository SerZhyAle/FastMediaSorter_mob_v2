# Phase 01 - Default Gesture-Binding Seeding

**Strategic spec:** [`../S0662_welcome-default-gestures.md`](../S0662_welcome-default-gestures.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Seed the three left-edge gesture bindings once on a fresh install (UP -> open launch panel, RIGHT -> screenshot-with-edit, DOWN -> silent screenshot), gated by capability presence and a one-shot marker; no UI and no overlay enable.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.
- [ ] `ScreenGestureOverlayController` multibinding default exists (`src/main/.../di/ScreenGestureOverlayModule.kt`) - injecting the set compiles on every flavor.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SeedDefaultGestureBindingsUseCase.kt` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeViewModel.kt` | Modified | ≤ 390 |

> No new Hilt module: the use case uses constructor `@Inject`; `SettingsRepository` and the `Set<ScreenGestureOverlayController>` multibinding already resolve. No data-class default change (ADR-1) - the seed is an explicit runtime write so upgrade users are untouched.

---

## Steps

### Step 01.1 - Create SeedDefaultGestureBindingsUseCase

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SeedDefaultGestureBindingsUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a use case with an `@Inject` constructor taking `SettingsRepository` and `Set<@JvmSuppressWildcards ScreenGestureOverlayController>`. Expose `suspend operator fun invoke()`: return early when the controller set is empty (capability absent on this flavor); otherwise read the current settings and `updateSettings` with `screenshotGestureActionUp = OPEN_PANEL`, `screenshotGestureActionRight = OPEN_IN_DRAW`, `screenshotGestureActionDown = SILENT_SCREENSHOT`. Do NOT touch `gestureOverlayEnabled` (enabling stays the explicit toggle's job). Timber only; no broad catch.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SeedDefaultGestureBindingsUseCase.kt` exists.
- `Grep` - `class SeedDefaultGestureBindingsUseCase` matches exactly once.
- `Grep` - `ScreenshotGestureAction.OPEN_PANEL` and `ScreenshotGestureAction.OPEN_IN_DRAW` both present.
- `Grep` - `gestureOverlayEnabled` returns zero hits in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 4/4 PASS. Files: domain/usecase/SeedDefaultGestureBindingsUseCase.kt (New, +33 LOC). Capability-gated, no overlay enable.

---

### Step 01.2 - Wire one-shot first-run seeding into WelcomeViewModel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeViewModel.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Inject `SeedDefaultGestureBindingsUseCase`. Add a `welcome_prefs` key constant `KEY_GESTURE_DEFAULTS_SEEDED = "gesture_defaults_seeded"` and `isGestureDefaultsSeeded()` / `markGestureDefaultsSeeded()` helpers mirroring the existing prefs accessors (`StrictModeHelper.allowDiskReads/Writes`). Add a private `maybeSeedDefaultGestureBindings()` that returns early when `isWelcomeCompleted()` (scopes to fresh installs - upgrade users already completed onboarding) or `isGestureDefaultsSeeded()`; otherwise launches on `applicationScope`, awaits `seedDefaultGestureBindingsUseCase()`, then calls `markGestureDefaultsSeeded()`. Invoke it from `init {}`.

**Verification:**

- `Grep` - `SeedDefaultGestureBindingsUseCase` present as a constructor parameter.
- `Grep` - `KEY_GESTURE_DEFAULTS_SEEDED` and `maybeSeedDefaultGestureBindings` both present.
- `Grep` - `maybeSeedDefaultGestureBindings()` is invoked inside the `init` block.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 3/3 PASS. Files: ui/welcome/WelcomeViewModel.kt (+~30 LOC). One-shot marker `gesture_defaults_seeded` + `!isWelcomeCompleted()` gate; seeded on applicationScope from init.

---

## Phase Done Criteria

- [x] Every `Step 01.*` is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` (BUILD SUCCESSFUL, kapt/Hilt validated).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [~] Dev log entry - batched in Phase 04 finalization.
- [~] `dev/CATALOG/app_v2.jsonl` regenerated - batched in Phase 04 finalization.

---

## Handoff Notes to Next Phase

The default direction bindings now exist on fresh installs independent of the UI. Enabling the overlay is still off by default and is wired in Phase 03. The seeding is gated so existing/upgrade users keep their configuration.

---

## Rollback Plan

Revert the phase commit(s). No data migration; the seed is a settings write keyed by a one-shot marker - removing the use case and the marker leaves prior installs untouched.
