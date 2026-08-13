# Phase 02 - BaseActivity generic-motion hook

**Strategic spec:** [`../S0508_gamepad-navigation-parity.md`](../S0508_gamepad-navigation-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-06-18
**Completed:** 2026-06-18

**Step Log:**

- 2026-06-18 - Steps 02.1-02.3 Verification PASS (greps: hooks, translator, FocusFinder, FocusMove/Scroll branches; opt-out=false in all 6 player-family files). BaseActivity.kt 505 LOC.
- 2026-06-18 - Step 02.4 build gate: `.\a.ps1 d` BUILD SUCCESSFUL. neuroslop delta 0.

---

## Objective

Wire the translator into the shared base Activity so the left stick moves focus and the right stick scrolls the active container on every in-house screen, with the player family opting out.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] `GamepadNavigationTranslator` greps in `core/input`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt` | Modified | ≤ 520 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ existing |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt` | Modified | ≤ existing |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | Modified | ≤ existing |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/AudioStandaloneActivity.kt` | Modified | ≤ existing |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/DocumentStandaloneActivity.kt` | Modified | ≤ existing |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/TextStandaloneActivity.kt` | Modified | ≤ existing |

> `BaseActivity.kt` is over 400 LOC - if the edit pushes it past 500, add a timestamped backup in `temp/` first (Constraints). Prefer extracting the gamepad glue into a small helper if it would breach 500.

---

## Steps

### Step 02.1 - Add opt-out + scroll-target hooks

**Files:** `core/ui/BaseActivity.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> Add two protected open hooks to `BaseActivity`, mirroring the S0506 `shouldGuardContainerFocus` pattern:
> - `protected open fun shouldHandleGamepadNavigation(): Boolean = true` - screens owning their own motion routing override to false.
> - `protected open fun getGamepadScrollTargetView(): View? = getMouseScrollTargetView()` - default reuses the existing mouse scroll target; screens can point at a different scroll container.
> Add a lazily-created `GamepadNavigationTranslator` instance field (one per Activity).

**Verification:**

- `Grep` - `fun shouldHandleGamepadNavigation` present in `BaseActivity.kt`.
- `Grep` - `fun getGamepadScrollTargetView` present.
- `Grep` - `GamepadNavigationTranslator` referenced in `BaseActivity.kt`.

**Status:** `[x]` done

---

### Step 02.2 - Translate motion into focus move / scroll

**Files:** `core/ui/BaseActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `BaseActivity.dispatchGenericMotionEvent`, before the existing mouse-helper routing, add a gamepad branch: when `shouldHandleGamepadNavigation()` is true and `_binding != null`, call `translator.translate(event)`. On a `GamepadNavIntent.FocusMove`, first apply the S0506 container guard (if `currentFocus` is a focus-trap container, redirect into it via `FocusTargetResolver`), then move focus with `FocusFinder.getInstance().findNextFocus(rootView, currentFocus, direction)` and `requestFocus()`; consume the event when focus moved. On a `GamepadNavIntent.Scroll`, scroll `getGamepadScrollTargetView()` by the intent's dx/dy (`View.scrollBy` / `RecyclerView.scrollBy` / `NestedScrollView.scrollBy` as applicable) and consume. Return false (fall through to mouse + super) when the translator yields null. Do not break the existing finger-guard or mouse routing.

**Verification:**

- `Grep` - `translate(event)` (or `translator.translate`) present in `BaseActivity.kt`.
- `Grep` - `FocusFinder` referenced in `BaseActivity.kt`.
- `Grep` - `is GamepadNavIntent.FocusMove` and `is GamepadNavIntent.Scroll` both present.
- `.\a.ps1 fk` -> exit 0.

**Status:** `[x]` done

---

### Step 02.3 - Opt the player family out

**Files:** `ui/player/PlayerActivity.kt`, `ui/player/StandalonePlayerActivity.kt`, `ui/player/standalone/PhotoVideoStandaloneActivity.kt`, `ui/player/standalone/AudioStandaloneActivity.kt`, `ui/player/standalone/DocumentStandaloneActivity.kt`, `ui/player/standalone/TextStandaloneActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In every player-host Activity that owns its own generic-motion routing (the six listed), override `shouldHandleGamepadNavigation(): Boolean = false` with a one-line WHY comment (transport/standalone surfaces route motion through their own dispatcher). Do not change their existing `dispatchGenericMotionEvent` overrides.

**Verification:**

- `Grep` - `fun shouldHandleGamepadNavigation` present in each of the six files, each returning `false`.
- `.\a.ps1 fk` -> exit 0.

**Status:** `[x]` done

---

### Step 02.4 - Build gate

**Files:** (no new edits)
**Depends on:** Steps 02.1-02.3

**Prompt for developer:**

> Build the standard debug APK to prove the dispatch change packages. `.\a.ps1 d`.

**Verification:**

- `.\a.ps1 d` -> BUILD SUCCESSFUL, APK produced.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles and packages - `.\a.ps1 d` SUCCESSFUL.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every modified file.
- [ ] `neuroslop` gate delta 0 (no unsafe-collect / empty-catch / hex introduced).

---

## Handoff Notes to Next Phase

Opt-out hook `shouldHandleGamepadNavigation()` and the unconsumed-event contract are the basis Phase 03 reuses for shoulder page-jump (same opt-out, same fallback-after-subclass ordering).

---

## Rollback Plan

Revert phase commit(s); the generic-motion branch is additive and guarded by `shouldHandleGamepadNavigation()` - reverting restores prior mouse-only routing with no data impact.
