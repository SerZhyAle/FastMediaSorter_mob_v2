# Phase 03 - Scroll follows focus

**Strategic spec:** [`../S0944_app-wide-focus-reachability.md`](../S0944_app-wide-focus-reachability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Steps done:** 0 / 2

---

## Objective

Ensure moving focus in any scroll container brings the focused child fully into view. RecyclerView / ScrollView do this by default on `requestChildFocus`; this phase audits for containers that clip focus and applies a shared fix only where the default fails.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| scroll hosts that clip focus (identified on-device) | Modified | ≤ 60 each |

---

## Steps

### Step 03.1 - Audit + fix clipped scroll hosts

**Prompt for developer:**

> On the TV emulator, D-pad through the app's scrolling screens; for any container where the focused item stays clipped/off-screen, ensure `android:descendantFocusability` and default scroll-on-focus behave (or add `requestRectangleOnScreen`/`isFocusableInTouchMode` handling). Prefer a shared helper over per-screen code.

**Verification:**

- `Grep` - shared scroll-into-view handling referenced where added.
- `/build` - compiles.

**Status:** `[ ]` not done

### Step 03.2 - Device verification

**Prompt for developer:**

> Focused item always fully visible while navigating a long list/form with the remote.

**Verification:**

- No focused item stays clipped during D-pad navigation of a scroll host.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Steps `[x]`; `/build` passes; dev log added.

---

## Rollback Plan

Revert phase commit(s) - additive. No data migration.
