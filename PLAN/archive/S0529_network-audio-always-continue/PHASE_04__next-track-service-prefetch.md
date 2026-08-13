# Phase 04 - Next-track service prefetch (no startup pause)

**Strategic spec:** [`../S0529_network-audio-always-continue.md`](../S0529_network-audio-always-continue.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02
**Blocks:** -
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Prepare the next network track for seamless auto-advance via service streaming, bounded by the existing network throttle, with a longer prefetch budget than the manual-start path - so transitions between tracks don't reintroduce a startup pause.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PrefetchPolicyManager.kt` | Modified | ≤ 250 |

> `PlayerMediaLoaderManager.kt` exceeds 500 LOC - timestamped backup in `temp/` before editing.

---

## Steps

### Step 04.1 - Separate prefetch budget for the next track

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PrefetchPolicyManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a next-track prefetch policy distinct from the manual-start policy: it does not use the short manual-start connect-fallback bound (the next track has the whole current track's playtime to get ready). Express it as a generous transfer budget reusing the adaptive computation from Phase 01. Keep `nextTrackPrefetchRecovery` semantics.

**Verification:**

- `Grep` - a next-track policy/budget function distinct from `audioStartupPolicyFor` present in `PrefetchPolicyManager.kt`.

**Status:** `[ ]` not done

---

### Step 04.2 - Prefetch next track via service streaming through the throttle

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> For network/cloud next-track preparation, prefer queuing the upcoming track to the service streaming path (no mandatory full local copy). Route both the current stream and the next-track preparation through `ConnectionThrottleManager.withThrottle(<protocol>, resourceKey)` so concurrent network operations stay within the user's `networkParallelism` (no hardcoded "2"). Do not delete a partial cache that is still usable; avoid duplicate re-downloads when the auto-advance fires.

**Verification:**

- `Grep` - next-track preparation wrapped in `ConnectionThrottleManager.withThrottle` in `PlayerMediaLoaderManager.kt`.
- `Grep` - no new hardcoded concurrency literal (e.g. `Semaphore(2)`) introduced in this file.
- `Grep -n "Log\.d\("` - zero hits in `PlayerMediaLoaderManager.kt`.

**Status:** `[ ]` not done

---

### Step 04.3 - Use the next-track budget on the prepare path

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt`
**Depends on:** Step 04.1, Step 04.2

**Prompt for developer:**

> Make the next-track prepare path consume the Phase 04.1 budget rather than the manual-start short bound, so a slow next file is not abandoned early. Preserve the `currentTrackUnaffected` recovery behaviour (a failed prefetch must never disturb the currently playing track).

**Verification:**

- `Grep` - the next-track prepare path references the next-track budget (not `audioStartupPolicyFor`).
- `.\a.ps1 fk` compiles.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

All three grains (exit, manual start, next-track) now route through service streaming bounded by the network throttle. Final phase regenerates catalog and dev log.

---

## Rollback Plan

Revert phase commit(s) - prefetch policy/budget only. No data migration or user-facing surface beyond reduced inter-track gaps.
