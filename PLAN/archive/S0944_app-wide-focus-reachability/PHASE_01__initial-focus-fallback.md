# Phase 01 - Universal initial-focus fallback

**Strategic spec:** [`../S0944_app-wide-focus-reachability.md`](../S0944_app-wide-focus-reachability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-07-04
**Completed:** 2026-07-04

> **Device-verified 2026-07-04:** on `SettingsActivity` (which does NOT override `getInitialFocusView`), recreated in non-touch mode, initial focus landed on a real control (`backButton`) - before the change it would have been null (no initial focus). Verified on the gphone emulator (Android 17); the change is device-agnostic (BaseActivity, all devices).

---

## Objective

Give every `BaseActivity` screen a sensible initial focus in non-touch mode even when it does not override `getInitialFocusView()`, by falling back to the first real focusable in the content root via the existing `FocusTargetResolver`. Removes "dead screen, first key wasted / nothing focused" for all screens at once, no per-screen edits.

---

## Prerequisites

- [x] `FocusTargetResolver.resolveToLeafFocusable` already resolves a container/root to the first real focusable and avoids traps (S0504).
- [x] `BaseActivity` already applies initial focus when `getInitialFocusView()` is non-null and `shouldRequestInitialFocus()` is true.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt` | Modified | ≤ 5 |

> No layout / flavor divergence. Reuses existing `FocusTargetResolver`.

---

## Steps

### Step 01.1 - Fall back to content root when no explicit initial focus

**Files:** `core/ui/BaseActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In the initial-focus block (guarded by `shouldRequestInitialFocus()`), when `getInitialFocusView()` returns null fall back to the content root (`_binding?.root`) before resolving: `(getInitialFocusView() ?: _binding?.root)?.let { FocusTargetResolver.resolveToLeafFocusable(it) ?: it }?.requestFocus()`. `resolveToLeafFocusable` already skips scroll containers and EditText and returns the first real control, so a screen with no explicit target still lands focus on a usable control instead of nothing.

**Verification:**

- `Grep` - `_binding?.root` appears in the initial-focus block of `BaseActivity.kt`.
- `/build` - project compiles.

**Status:** `[x]` done

### Step 01.2 - Device verification on TV emulator

**Files:** (verification only)
**Depends on:** Step 01.1

**Prompt for developer:**

> On the TV emulator, open a screen that does NOT override `getInitialFocusView()` (e.g. a settings sub-screen) with D-pad; a control must be focused on open (visible via the S0943 focus outline) and the first directional key must act immediately, not be wasted waking focus.

**Verification:**

- On open of a non-overriding screen in non-touch mode, a real control shows the focus outline (initial focus set).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

Activity-level initial focus is now universal. Phase 02 extends the same fallback to dialog / bottom-sheet windows (their own `Window`, not covered by `BaseActivity`).

---

## Rollback Plan

Revert the one-line change - screens revert to prior behavior (initial focus only when explicitly overridden). No data migration.
