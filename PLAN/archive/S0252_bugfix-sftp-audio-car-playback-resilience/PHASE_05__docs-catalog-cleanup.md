# Phase 05 - Docs Catalog Cleanup

**Strategic spec:** [`../S0252_bugfix-sftp-audio-car-playback-resilience.md`](../S0252_bugfix-sftp-audio-car-playback-resilience.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03, Phase 04
**Blocks:** final verification
**Steps done:** 4 / 4
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Close S0252 with catalog sync, logs, feature-doc decision, and on-device acceptance instructions.

---

## Prerequisites

- [x] Phase 02 is ✅ Done.
- [x] Phase 03 is ✅ Done.
- [x] Phase 04 is ✅ Done.
- [x] Working tree contains only S0252-related changes or unrelated changes are documented: existing S0250/S0251/S0249 VR/settings changes remain outside S0252 and were not reverted.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified if Kotlin changed | generated |
| `dev/CATALOG/app_v2.md` | Modified if Kotlin changed | generated |
| `dev/CHANGELOG.md` | Modified | generated |
| `PLAN/S0252_bugfix-sftp-audio-car-playback-resilience.md` | Modified | ≤ 500 |
| `PLAN/S0252_bugfix-sftp-audio-car-playback-resilience/INDEX.md` | Modified | ≤ 250 |

---

## Steps

### Step 05.1 - Run catalog sync

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** all implementation phases

**Prompt for developer:**

> If any `.kt` file changed, run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Do not run separate scan/render scripts.

**Verification:**

- `Command` - catalog sync exits 0 if Kotlin changed.
- `Grep` - touched public classes appear in `dev/CATALOG/app_v2.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 2/2 PASS. `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exited 0; `AudioStartupPreCachePolicy`, `AudioNextTrackPrefetchRecovery`, and `AudioPreCacheSourceType` appear in `dev/CATALOG/app_v2.md`.

### Step 05.2 - Record dev log

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add dev log entries for every code/config/spec file changed using `.\scripts\add_to_dev_log.ps1`. Do not edit `dev/CHANGELOG.md` manually.

**Verification:**

- `Grep` - `S0252` appears in `dev/CHANGELOG.md`.
- `Grep` - every changed source/spec path has a dev log entry.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 2/2 PASS. `S0252` appears in `dev/CHANGELOG.md`; changed source/spec/catalog/build paths received dev-log entries.

### Step 05.3 - Decide feature docs

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Confirm whether S0252 introduced any new user-visible capability. Expected decision: no docs update, because this is a bugfix for existing SFTP/audio features.

**Verification:**

- `Grep` - S0252 final note states `docs/FEATURES unchanged`.
- If docs changed, EN/RU/UK mirror entries exist.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 1/1 PASS. Strategic Last Audit states `docs/FEATURES unchanged`; no EN/RU/UK feature docs were changed because S0252 is a bugfix for existing SFTP/audio behavior.

### Step 05.4 - Prepare on-device acceptance

**Files:** `PLAN/S0252_bugfix-sftp-audio-car-playback-resilience.md`, `PLAN/S0252_bugfix-sftp-audio-car-playback-resilience/INDEX.md`
**Depends on:** Step 05.3

**Prompt for developer:**

> Add the exact on-device acceptance scenario and expected log predicates to S0252. Include the two original log file names as regression references.

**Verification:**

- `Grep` - `logs/fastmediasorter_20260519_101908.log` appears in S0252.
- `Grep` - `logs/fastmediasorter_20260519_102218.log` appears in S0252.
- `Grep` - `SftpDataSource: Error closing InputStream` appears as a negative predicate.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Strategic §13 includes both original log filenames and the negative predicate `SftpDataSource: Error closing InputStream`.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - run `/build`. `build-debug.PS1` / `assembleStandardDebug` succeeded on 2026-05-19.
- [x] `/spec-check S0252` outcome recorded in strategic Last Audit as `BlockNeedUserTest`.
- [x] Strategic spec status moved to `BlockNeedUserTest` pending real head-unit/home-SFTP acceptance.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s). Generated catalog files may be regenerated from the surviving Kotlin state.
