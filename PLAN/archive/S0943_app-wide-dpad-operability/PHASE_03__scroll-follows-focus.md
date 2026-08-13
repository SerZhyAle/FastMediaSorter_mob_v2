# Phase 03 - Scroll follows focus

**Strategic spec:** [`../S0943_app-wide-dpad-operability.md`](../S0943_app-wide-dpad-operability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** none
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Guarantee that moving focus in any scroll container (list, grid, scrolling form) brings the focused item into the visible area, so directional navigation never focuses an off-screen control.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/focus/<ScrollFocusFollower>.kt` | New | ≤ 200 |
| scroll-hosting screens (browse grid, settings scroll, welcome pages) | Modified | ≤ 150 each |

---

## Steps

### Step 03.1 - Scroll-into-view follower

**Prompt for developer:**

> Introduce a reusable helper that, on focus change inside a scroll container, requests the container to bring the newly focused child fully into view (respecting the container padding), covering both RecyclerView-based and ScrollView-based hosts.

**Verification:**

- `Glob` - the follower file exists.
- `/build` - project compiles.

**Status:** `[ ]` not done

### Step 03.2 - Apply to scroll-hosting screens

**Prompt for developer:**

> Attach the follower to the app's scroll-hosting screens (browse grid, settings, welcome scrolling pages) so a focused item never stays clipped or off-screen.

**Verification:**

- `Grep` - the follower is referenced from each scroll-hosting screen.
- `/build` - project compiles.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Focused controls are always on-screen; Phase 04 custom surfaces can rely on this for their own scroll areas.

---

## Rollback Plan

Revert phase commit(s) - helper is additive; screens revert to default scroll behavior. No data migration.
