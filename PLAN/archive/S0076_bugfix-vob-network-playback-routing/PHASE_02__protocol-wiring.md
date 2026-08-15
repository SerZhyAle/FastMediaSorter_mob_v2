# Phase 02 - Protocol Wiring

**Strategic spec:** [`../S0076_bugfix-vob-network-playback-routing.md`](../S0076_bugfix-vob-network-playback-routing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04, Phase 05
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Apply the shared container hint to SMB, SFTP, FTP, and cloud playback setup so VOB stays on the pass-through route.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] Phase 01 helper tests are green.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt` | Modified | <= 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt` | Modified | <= 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt` | Modified | <= 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt` | Modified | <= 60 |

> File projected >500 lines after change -> backup step required (timestamped copy in `temp/`). File >1000 lines -> split via Manager pattern first.

---

## Steps

### Step 02.1 - Replace ad-hoc extension checks with the shared route helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Replace raw `.m2ts/.m2t` extension checks with the shared container-hint helper. Probe TS format only for `M2TS_TS_CANDIDATE`. Keep cloud on the same helper contract even though `.vob` remains out of scope there.

**Verification:**

- `Grep` - `NetworkPlaybackContainerHint` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt`.
- `Grep` - `NetworkPlaybackContainerHint` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt`.
- `Grep` - `NetworkPlaybackContainerHint` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt`.
- `Grep` - `NetworkPlaybackContainerHint` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt`.

**Status:** `[ ]` not done

---

### Step 02.2 - Add explicit route diagnostics and keep VOB on pass-through

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add one structured debug log per protocol helper that reports the chosen container hint and TS format result. Ensure `DVD_PS_VOB` never reaches `buildBdTsMediaSourceFactory` with a stripping decision enabled.

**Verification:**

- `Grep` - `routeHint=` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt`.
- `Grep` - `routeHint=` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt`.
- `Grep` - `routeHint=` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt`.
- `Grep` - `DVD_PS_VOB` present in at least one explicit branch across those helpers.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [ ] SMB, SFTP, and FTP helpers only probe TS format for `M2TS_TS_CANDIDATE` inputs.

---

## Handoff Notes to Next Phase

Protocol helpers now agree on one route-selection contract; route errors can be intercepted in player code without revisiting protocol setup.

---

## Rollback Plan

Revert phase commit(s) - no data migration or persisted state changed.# Phase 02 - Protocol Wiring

**Strategic spec:** [`../S0076_bugfix-vob-network-playback-routing.md`](../S0076_bugfix-vob-network-playback-routing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04, Phase 05
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Apply the shared route foundation to SMB, SFTP, FTP, and cloud helpers so VOB stays on pass-through media sources and TS probing runs only where intended.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] Phase 01 helper tests pass.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt` | Modified | <= 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt` | Modified | <= 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt` | Modified | <= 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt` | Modified | <= 60 |

> File projected >500 lines after change -> backup step required (timestamped copy in `temp/`). File >1000 lines -> split via Manager pattern first.

---

## Steps

### Step 02.1 - Replace ad-hoc extension checks with the shared route helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Replace raw `.m2ts/.m2t` branching in all network playback helpers with the shared container hint API from Phase 01. Keep TS probing only for TS candidates, keep cloud behavior aligned with the same helper, and do not introduce any VOB-only probing path.

**Verification:**

- `Grep` - `NetworkPlaybackContainerHint` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt`.
- `Grep` - `NetworkPlaybackContainerHint` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt`.
- `Grep` - `NetworkPlaybackContainerHint` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt`.
- `Grep` - `NetworkPlaybackContainerHint` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt`.

**Status:** `[ ]` not done

---

### Step 02.2 - Add explicit route diagnostics and keep VOB on the plain path

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add one concise route log in every network helper that records the container hint and the chosen TS-probe decision. Ensure `.vob` never reaches a stripping factory through an `UNKNOWN` fallback path, and keep `.m2ts/.m2t` behavior unchanged except for the corrected helper semantics.

**Verification:**

- `Grep` - `routeHint=` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt`.
- `Grep` - `routeHint=` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt`.
- `Grep` - `routeHint=` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt`.
- `Grep` - `routeHint=` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

All network helpers now share one route decision vocabulary. The next phase must use that route information to distinguish VOB route failures from generic playback failures.

---

## Rollback Plan

Revert phase commit(s) - network helpers return to their previous per-file branching.