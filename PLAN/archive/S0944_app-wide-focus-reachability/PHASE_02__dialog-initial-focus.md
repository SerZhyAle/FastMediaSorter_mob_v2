# Phase 02 - Dialog / bottom-sheet initial focus

**Strategic spec:** [`../S0944_app-wide-focus-reachability.md`](../S0944_app-wide-focus-reachability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Steps done:** 0 / 2

---

## Objective

Extend the universal initial-focus fallback to `DialogFragment` / bottom-sheet windows (their own `Window`, not covered by `BaseActivity`), so a dialog opened with a remote focuses a real control on show.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| dialog/bottom-sheet base or a shared helper reusing `FocusTargetResolver` | Modified | ≤ 80 |

---

## Steps

### Step 02.1 - Initial focus on dialog show

**Prompt for developer:**

> On a dialog/bottom-sheet becoming visible in non-touch mode, resolve the dialog content root through `FocusTargetResolver.resolveToLeafFocusable` and request focus, mirroring the Activity fallback. Reuse the existing `FocusDecorationFragmentCallbacks` attach point if practical.

**Verification:**

- `Grep` - initial-focus resolution present for the dialog window path.
- `/build` - compiles.

**Status:** `[ ]` not done

### Step 02.2 - Device verification

**Prompt for developer:**

> Open a dialog with the remote; a control is focused on show (S0943 outline visible), first key acts immediately.

**Verification:**

- Dialog shows a focused control on open in non-touch.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Steps `[x]`; `/build` passes; dev log added.

---

## Rollback Plan

Revert phase commit(s) - additive. No data migration.
