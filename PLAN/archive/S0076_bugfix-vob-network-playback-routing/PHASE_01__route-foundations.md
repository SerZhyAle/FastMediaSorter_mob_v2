# Phase 01 - Route Foundations

**Strategic spec:** [`../S0076_bugfix-vob-network-playback-routing.md`](../S0076_bugfix-vob-network-playback-routing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Introduce explicit network container hints and make BD-TS stripping opt-in only for `TsPacketFormat.BD_192`.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] Current field-log signatures for `.vob` and `.m2ts` route failures are preserved in task notes.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NetworkPlaybackContainerHint.kt` | New | <= 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BdTsPlaybackHelper.kt` | Modified | <= 180 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/BdTsPlaybackHelperTest.kt` | New | <= 220 |

> File projected >500 lines after change -> backup step required (timestamped copy in `temp/`). File >1000 lines -> split via Manager pattern first.

---

## Steps

### Step 01.1 - Add an explicit network container hint model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NetworkPlaybackContainerHint.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a small enum or equivalent sealed model under `ui/player/helpers` for network playback container hints. Cover at least `M2TS_TS_CANDIDATE`, `DVD_PS_VOB`, and `OTHER`. Add one pure path-classification helper that works case-insensitively and performs no I/O.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NetworkPlaybackContainerHint.kt` exists.
- `Grep` - `enum class NetworkPlaybackContainerHint` matches exactly once in that file.
- `Grep` - `DVD_PS_VOB` present.

**Status:** `[ ]` not done

---

### Step 01.2 - Make BD-TS stripping opt-in only for `BD_192`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BdTsPlaybackHelper.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Refactor the helper so `TsPacketFormat.UNKNOWN` is neutral pass-through, not a reason to wrap with `BdTsStripDataSourceFactory`. Add a pure strip-decision function that returns true only for `TsPacketFormat.BD_192`. Keep the existing media-source factory entry points buildable for later phases.

**Verification:**

- `Grep` - `fun shouldUseBdTsStripper` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BdTsPlaybackHelper.kt`.
- `Grep` - `format == TsPacketFormat.BD_192` present in that file.
- `Grep` - `BdTsStripDataSourceFactory` present in that file.

**Status:** `[ ]` not done

---

### Step 01.3 - Add focused helper tests for route decisions

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/BdTsPlaybackHelperTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add unit tests for path classification and strip decisions. Cover `.m2ts`, `.m2t`, `.vob`, mixed-case extensions, and `TsPacketFormat.UNKNOWN`. Keep the test file independent from ExoPlayer runtime state.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/BdTsPlaybackHelperTest.kt` exists.
- `Grep` - `vob maps to DVD_PS_VOB` present in that file.
- `Grep` - `UNKNOWN stays pass-through` present in that file.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [ ] The helper layer exposes a pass-through decision for both `TsPacketFormat.UNKNOWN` and `TsPacketFormat.STANDARD_188`.

---

## Handoff Notes to Next Phase

Network container hints and BD-TS strip decisions are pure helper-level contracts that protocol helpers can reuse without duplicating extension checks.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.# Phase 01 - Route Foundations

**Strategic spec:** [`../S0076_bugfix-vob-network-playback-routing.md`](../S0076_bugfix-vob-network-playback-routing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Introduce explicit network container hints and make BD-TS stripping opt-in for `TsPacketFormat.BD_192` only.

---

## Prerequisites

- [ ] Strategic §6 research items are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] Existing BD-TS unit tests are green before editing helper logic.
- [ ] No unrelated playback refactor is in progress in the same files.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NetworkPlaybackContainerHint.kt` | New | <= 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BdTsPlaybackHelper.kt` | Modified | <= 180 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/BdTsPlaybackHelperTest.kt` | New | <= 220 |

> File projected >500 lines after change -> backup step required (timestamped copy in `temp/`). File >1000 lines -> split via Manager pattern first.

---

## Steps

### Step 01.1 - Add an explicit network container hint model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NetworkPlaybackContainerHint.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a small, pure model in `ui/player/helpers` that classifies network playback paths without touching I/O. Cover at least three cases: `.m2ts/.m2t` TS candidates, `.vob` DVD Program Stream, and everything else. Keep the API path-based and case-insensitive so protocol helpers can reuse it without duplicate extension logic.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NetworkPlaybackContainerHint.kt` exists.
- `Grep` - `enum class NetworkPlaybackContainerHint` matches exactly once in that file.
- `Grep` - `DVD_PS_VOB` present.

**Status:** `[x] done`

**Step Log:**
- 2026-05-04 — Verification 3/3 PASS. Files: app_v2/.../NetworkPlaybackContainerHint.kt (new, 17 LOC). Dev log pending end of phase.

---

### Step 01.2 - Make BD-TS stripping opt-in for BD_192 only

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BdTsPlaybackHelper.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Refactor `BdTsPlaybackHelper.kt` so `TsPacketFormat.UNKNOWN` is a neutral pass-through result, not a trigger for `BdTsStripDataSourceFactory`. Add a pure strip-decision helper that returns `true` only for `TsPacketFormat.BD_192`, and keep `STANDARD_188` plus `UNKNOWN` on the plain media-source path.

**Verification:**

- `Grep` - `fun shouldUseBdTsStripper` present.
- `Grep` - `format == TsPacketFormat.BD_192` present.
- `Grep` - `BdTsStripDataSourceFactory` present.

**Status:** `[x] done`

**Step Log:**
- 2026-05-04 — Verification 3/3 PASS. Files: app_v2/.../BdTsPlaybackHelper.kt (modified). Dev log pending end of phase.

---

### Step 01.3 - Add focused helper tests for the route foundation

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/BdTsPlaybackHelperTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add focused unit tests for the new path classifier and strip-decision helper. Cover `.m2ts`, `.m2t`, `.vob`, mixed-case extensions, `TsPacketFormat.BD_192`, `TsPacketFormat.STANDARD_188`, and `TsPacketFormat.UNKNOWN`.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/BdTsPlaybackHelperTest.kt` exists.
- `Grep` - `UNKNOWN stays pass-through` present.
- `Grep` - `vob maps to DVD_PS_VOB` present.

**Status:** `[x] done`

**Step Log:**
- 2026-05-04 — Verification 3/3 PASS. Files: app_v2/.../BdTsPlaybackHelperTest.kt (new, 87 LOC). Dev log pending end of phase.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`NetworkPlaybackContainerHint` and the strip-decision helper are available. Protocol helpers can stop branching on raw extension literals and must not treat `UNKNOWN` as BD-TS.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.