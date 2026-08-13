# Phase 01 - Startup timeout split (connect vs transfer)

**Strategic spec:** [`../S0529_network-audio-always-continue.md`](../S0529_network-audio-always-continue.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 04
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Split the single network-audio pre-cache timeout into a short fixed connect timeout (detect a dead server fast) and an adaptive transfer timeout derived from the last measured connection speed; no behavioural change to the playback path beyond timeout values.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PrefetchPolicyManager.kt` | Modified | ≤ 240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt` | Modified | ≤ 760 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/PlayerPrefetchManagerTest.kt` | Modified | ≤ 160 |

> `PlayerMediaLoaderManager.kt` exceeds 500 LOC - timestamped backup in `temp/` before editing.

---

## Steps

### Step 01.1 - Replace single startup timeout with connect + adaptive transfer in the policy

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PrefetchPolicyManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `AudioStartupPreCachePolicy`, replace the single `timeoutMs` with `connectTimeoutMs` (short, fixed - reuse the current `SFTP_AUDIO_STARTUP_PRECACHE_TIMEOUT_MS` value as the connect bound) and `transferTimeoutMs`. Add a companion function `transferTimeoutMsFor(speedMbps: Double?, fileSizeBytes: Long): Long` that returns a rough adaptive bound from the measured speed (estimated download time × a safety factor), clamped to a sensible floor and ceiling; when `speedMbps` is null return `DEFAULT_NETWORK_AUDIO_PRECACHE_TIMEOUT_MS`. Update `audioStartupPolicyFor` to populate both fields (it may take `speedMbps` and `fileSizeBytes` params; callers pass the last measured speed from `ConnectionThrottleManager.getLastSpeedMbps`). Keep `directStreamFallback`/`reason` semantics. No user-facing setting.

**Verification:**

- `Grep` - `connectTimeoutMs` present in `PrefetchPolicyManager.kt`.
- `Grep` - `transferTimeoutMsFor` declared exactly once.
- `Grep` - `timeoutMs` no longer a field of `AudioStartupPreCachePolicy` (replaced).

**Status:** `[ ]` not done

---

### Step 01.2 - Apply connect/transfer split in network pre-cache

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `preCacheNetworkAudio`, source the policy with the last measured speed for the resource key and the known file size. Bound the connection-establishment / first-byte wait by `connectTimeoutMs` so an unreachable server fails fast, and bound the remaining body download by `transferTimeoutMs`. Preserve the existing `TimeoutCancellationException` fallback and cache-delete behaviour. Update the timeout log line to report which bound expired (connect vs transfer) using plain English - no ticket id in the persistent log.

**Verification:**

- `Grep` - `connectTimeoutMs` referenced in `PlayerMediaLoaderManager.kt`.
- `Grep` - `transferTimeoutMs` referenced in `PlayerMediaLoaderManager.kt`.
- `Grep -n "Log\.d\("` - zero hits in `PlayerMediaLoaderManager.kt`.

**Status:** `[ ]` not done

---

### Step 01.3 - Update prefetch-policy unit tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/PlayerPrefetchManagerTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Update the assertions that referenced the old `timeoutMs`/`SFTP_AUDIO_STARTUP_PRECACHE_TIMEOUT_MS` to assert the new `connectTimeoutMs` and `transferTimeoutMs` semantics: SFTP connect bound equals the short fixed value; transfer bound equals `DEFAULT_NETWORK_AUDIO_PRECACHE_TIMEOUT_MS` when no measured speed is supplied; an adaptive transfer bound when a speed is supplied.

**Verification:**

- `Grep` - `transferTimeoutMs` asserted in `PlayerPrefetchManagerTest.kt`.
- `.\a.ps1 fk` compiles (test sources included by `fu`); run `.\gradlew.bat testStandardDebugUnitTest --tests *PlayerPrefetchManagerTest*` - the targeted class passes.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The policy now distinguishes connect from transfer time. Phase 02 reuses the same connect bound for the service-side streaming connection so a dead server is detected fast on the streaming path too.

---

## Rollback Plan

Revert phase commit(s) - timeout values only, no data migration or user-facing surface changed.
