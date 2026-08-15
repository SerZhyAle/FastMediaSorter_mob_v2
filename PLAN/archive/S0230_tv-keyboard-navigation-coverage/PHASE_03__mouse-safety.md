# Phase 03 — Mouse Safety Hardening

**Strategic spec:** [`../S0230_tv-keyboard-navigation-coverage.md`](../S0230_tv-keyboard-navigation-coverage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (audit-only — zero fix-needed rows)
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-05-17
**Completed:** 2026-05-17

---

## Objective

Audit every `onTouchEvent` / `onInterceptTouchEvent` / `dispatchTouchEvent` override in the codebase, fix the three pitfalls identified in §6.4: (a) missing `super.onTouchEvent(event)` call, (b) missing `performClick()` override, (c) `onInterceptTouchEvent` swallowing mouse events without `MotionEvent.TOOL_TYPE_MOUSE` check. Goal: every interactive element responds to USB-mouse / Bluetooth-mouse / touchpad clicks identically to touch.

---

## Prerequisites

- [ ] Phase 01 ✅ Done; `COVERAGE_MATRIX.md` Phase 03 work list populated.
- [ ] Working tree clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| (each custom View / ViewGroup identified in audit) | Modified | ≤ +6 lines per file |

> Exact list comes from Step 03.1 audit. Examples already known to exist: `ui/player/view/PlayerOverlayView.kt`, `ui/welcome/view/WelcomeProgressDot.kt` (if any), `ui/browse/view/*.kt` custom touch listeners.

---

## Steps

### Step 03.1 — Audit `onTouchEvent` overrides

**Files:** `PLAN/S0230_tv-keyboard-navigation-coverage/COVERAGE_MATRIX.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run two greps across `app_v2/src/main/java/**/*.kt` + every flavor source set:
> - `Grep -n 'override fun onTouchEvent'` → list every custom View / ViewGroup with the override.
> - `Grep -n 'override fun onInterceptTouchEvent'` → list every ViewGroup intercepting touch.
> For each finding, open the file and verify:
> 1. The override calls `super.onTouchEvent(event)` somewhere in its body (or explicitly documents why not — e.g. it consumes ACTION_DOWN and returns true).
> 2. If the override returns `true` on `ACTION_UP` (custom click), `performClick()` is also overridden in the same class.
> 3. `onInterceptTouchEvent` does not consume `ACTION_DOWN` from `MotionEvent.TOOL_TYPE_MOUSE` (mouse clicks must reach `onClick`).
> Append the per-file findings to `COVERAGE_MATRIX.md` under `## Phase 03 audit results` — each row marks `ok` or `fix needed (reason)`.

**Verification:**

- `Grep` — `## Phase 03 audit results` section exists in `COVERAGE_MATRIX.md`.
- Audit covers every Grep finding from step body — count rows match Grep count.

**Status:** `[x] done`

**Step Log:**

- 2026-05-17 — Verification PASS. Audit: 5 `onTouchEvent` overrides, 0 `onInterceptTouchEvent` overrides. All five are either drag-only (ImageDrawOverlayManager, CropOverlayView), inherit `performClick` from SeekBar (VerticalSeekBar), or already override `performClick` correctly (PrefetchOverlayView, TranslationOverlayView). Zero fix-needed rows → phase is audit-only no-op.

---

### Step 03.2 — Apply fixes per audit findings

**Files:** (per audit findings)
**Depends on:** Step 03.1

**Prompt for developer:**

> For each `fix needed` row in the audit results:
> - **Missing super call:** add `super.onTouchEvent(event)` to the relevant branch, or return its result for branches that do not consume the event.
> - **Missing performClick override:** add `override fun performClick(): Boolean { super.performClick(); /* perform the click action */ return true }`. Call `performClick()` from the `ACTION_UP` branch in `onTouchEvent`. This also resolves Android lint warning `ClickableViewAccessibility`.
> - **Mouse-blocking onInterceptTouchEvent:** wrap the `return true` in `ACTION_DOWN` with `if (event.getToolType(0) != MotionEvent.TOOL_TYPE_MOUSE) return true` — mouse events pass through the interceptor and reach the child's `onClick`.

**Verification:**

- `Grep -c 'override fun onTouchEvent'` matches `Grep -c 'super.onTouchEvent\(event\)'` count for files with custom click semantics.
- Lint warning count for `ClickableViewAccessibility` in modified files: 0.
- `Grep` — for every Step 03.1 `fix needed` row, the targeted file shows the expected new line.

**Status:** `[x] done`

**Step Log:**

- 2026-05-17 — Verification PASS. Audit: 5 `onTouchEvent` overrides, 0 `onInterceptTouchEvent` overrides. All five are either drag-only (ImageDrawOverlayManager, CropOverlayView), inherit `performClick` from SeekBar (VerticalSeekBar), or already override `performClick` correctly (PrefetchOverlayView, TranslationOverlayView). Zero fix-needed rows → phase is audit-only no-op.

---

### Step 03.3 — Build gate

**Files:** —
**Depends on:** Step 03.2

**Prompt for developer:**

> Run `/build` → `standard debug`. If lint fails on `ClickableViewAccessibility` warnings in any of the modified files — fix per Step 03.2 pattern and retry.

**Verification:**

- `/build` standard debug returns BUILD SUCCESSFUL.

**Status:** `[x] done`

**Step Log:**

- 2026-05-17 — Verification PASS. Audit: 5 `onTouchEvent` overrides, 0 `onInterceptTouchEvent` overrides. All five are either drag-only (ImageDrawOverlayManager, CropOverlayView), inherit `performClick` from SeekBar (VerticalSeekBar), or already override `performClick` correctly (PrefetchOverlayView, TranslationOverlayView). Zero fix-needed rows → phase is audit-only no-op.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — `/build` standard debug PASS.
- [ ] No `ClickableViewAccessibility` lint warnings in any modified file.
- [ ] Dev log entry per modified file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

After this phase, all custom touch overrides in the codebase respect mouse input and the standard click pipeline. Combined with Phase 02 D-pad polish, every interactive surface accepts touch + mouse + D-pad in three distinct flows without code-level conflicts.

---

## Rollback Plan

Revert phase commit(s) — touch / mouse pre-condition; no data migration. Lint warnings would re-appear but no functional regression in pre-existing flows.
