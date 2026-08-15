# Phase 03 - Docs & catalog cleanup

**Strategic spec:** [`../S0936_bugfix-stream-freeze-native-heap-snapshot.md`](../S0936_bugfix-stream-freeze-native-heap-snapshot.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-07-11
**Completed:** 2026-07-11

---

## Objective

Regenerate the class catalog for the new `StreamStallWatchdog` symbol and record the change in the dev log. No `docs/FEATURES` update (strategic §8 = internal resilience, not a user-visible capability).

---

## Prerequisites

- [x] Phase 01 ✅ Done.
- [x] Phase 02 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `dev/CATALOG/app_v2.md` | Regenerated | - |
| `dev/CHANGELOG.md` | Appended (via script) | - |

---

## Steps

### Step 03.1 - Regenerate the app_v2 catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` so the new `StreamStallWatchdog` extension file is indexed. `StreamStallWatchdog` lives in `src/main` (shared, not flavor-gated) - no `set.ps1 -NoFlavors` hint needed.

**Verification:**

- `Grep` - `StreamStallWatchdog` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

---

### Step 03.2 - Dev log the change set

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add one dev-log entry for the S0936 change via `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamStallWatchdog.kt" "S0936" "Stream stall watchdog: detect silent freeze (position frozen / buffering timeout) and bounded re-anchor recovery"`. Do not edit `dev/CHANGELOG.md` by hand.

**Verification:**

- `Grep` - `Stream stall watchdog` present in `dev/CHANGELOG.md`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `dev/CATALOG/app_v2.jsonl` contains `StreamStallWatchdog` (regenerated at closure).
- [x] `dev/CHANGELOG.md` has the S0936 entry (via close-and-log).
- [x] `/spec-check S0936` runnable (all code phases done).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Catalog and dev-log are regenerated artifacts - re-run `catalog_sync.ps1` to restore consistency. No code impact.
