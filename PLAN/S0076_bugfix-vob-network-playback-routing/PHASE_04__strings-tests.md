# Phase 04 - Strings and Tests

**Strategic spec:** [`../S0076_bugfix-vob-network-playback-routing.md`](../S0076_bugfix-vob-network-playback-routing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Add localized VOB route-error strings and regression tests for the new routing and callback behavior.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] The route-error callback from Phase 03 is implemented and buildable.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | <= 20 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | <= 20 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | <= 20 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/VideoPlayerManagerRouteErrorTest.kt` | New | <= 260 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImplTest.kt` | New | <= 240 |

> File projected >500 lines after change -> backup step required (timestamped copy in `temp/`). File >1000 lines -> split via Manager pattern first.

---

## Steps

### Step 04.1 - Add EN/RU/UK VOB route-error strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a dedicated title and message for the VOB/DVD route-error dialog in all three languages. Keep the text specific to the routing issue: do not claim the file is corrupted, and do not suggest an automatic fallback that the app does not perform.

**Verification:**

- `Grep` - `error_vob_route_title` present in `app_v2/src/main/res/values/strings.xml`.
- `Grep` - `error_vob_route_title` present in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` - `error_vob_route_title` present in `app_v2/src/main/res/values-uk/strings.xml`.

**Status:** `[ ]` not done

---

### Step 04.2 - Add regression tests for route and callback behavior

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/VideoPlayerManagerRouteErrorTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImplTest.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add focused regression tests that prove two things: a network `.vob` route error is routed to the dedicated callback instead of the generic playback-error path, and the callback implementation does not trigger auto-next handling. Keep the tests narrow and avoid a full player integration fixture unless the callback contract requires it.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/VideoPlayerManagerRouteErrorTest.kt` exists.
- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImplTest.kt` exists.
- `Grep` - `vob route error uses dedicated callback` present in `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/VideoPlayerManagerRouteErrorTest.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [ ] All new VOB route-error strings exist in EN, RU, and UK resource sets.

---

## Handoff Notes to Next Phase

The implementation now has localized user-facing text and targeted regression coverage. The final phase can limit itself to documentation, catalog regeneration, bookkeeping, and verification.

---

## Rollback Plan

Revert phase commit(s) - removes only localized text and test coverage.