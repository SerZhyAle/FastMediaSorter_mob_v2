# Phase 01 — Foundation And Stop Path

**Strategic spec:** [../S0188_slideshow-stop-on-resource-unavailable.md](../S0188_slideshow-stop-on-resource-unavailable.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Introduce a player-session helper that owns S0188 failure state, subscribes to network-loss events while slideshow is active, and reuses the existing slideshow stop path.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SlideshowResourceAvailabilityManager.kt` | New | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ 120 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 01.1 — Add the player-session availability helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SlideshowResourceAvailabilityManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `SlideshowResourceAvailabilityManager` under `ui/player/helpers`. The helper must observe slideshow state, register `NetworkStateMonitor.NetworkChangeCallback` only while a remote-resource slideshow is active, keep consecutive image/playback failure counters in memory, detect quick playback endings, and expose reset hooks for manual navigation plus success callbacks for image/playback readiness.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SlideshowResourceAvailabilityManager.kt` exists.
- `Grep` — `class SlideshowResourceAvailabilityManager` matches exactly once in that file.
- `Grep` — `override fun onNetworkLost()` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. Files: ui/player/helpers/SlideshowResourceAvailabilityManager.kt (+306 LOC). Dev log recorded.

---

### Step 01.2 — Reuse the existing stop/resume path from PlayerActivity init

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Inject `NetworkStateMonitor` into `PlayerActivity`, add minimal helper-facing methods for manual reset, forced slideshow stop, and cleanup, then construct the helper in `PlayerManagerInitializer` after `PlayerNavigationManager` is ready.

**Verification:**

- `Grep` — `lateinit var networkStateMonitor: NetworkStateMonitor` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`.
- `Grep` — `internal fun stopSlideshowDueToResourceIssue` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`.
- `Grep` — `SlideshowResourceAvailabilityManager(` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. Files: ui/player/PlayerActivity.kt, ui/player/PlayerManagerInitializer.kt. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 02 can now treat S0188 as a pure signal-wiring task: image, playback, and manual navigation paths only need to forward success/failure events into the shared helper.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.