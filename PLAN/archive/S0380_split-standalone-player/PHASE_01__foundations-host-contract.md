# Phase 01 - Foundations: player-host contract

**Strategic spec:** [`../S0380_split-standalone-player.md`](../S0380_split-standalone-player.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⏭️ Skipped
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** - (skipped)
**Started:** -
**Completed:** -

---

> **⏭️ SKIPPED (2026-06-07).** This phase's premise is already satisfied in code. `PlayerHostCapabilities` exists at `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlayerHostCapabilities.kt` and is implemented **directly** by both `PlayerActivity` and `StandalonePlayerActivity` (legacy of the `standalone-vs-inapp-player-parity` spec). The contract KDoc mandates "both activities implement this interface directly", so Step 01.2's separate `StandalonePlayerHostController` would contradict the established architecture and Step 01.1 would duplicate an existing interface (build break). Steps below are retained for history only - do not execute. The reuse seam Phase 04 needs is the existing `PlayerHostCapabilities` + standalone helpers.

---

## Objective

Introduce a shared player-host capabilities contract in `src/main` and route the existing `StandalonePlayerActivity` through it, behavior-preserving, so the future specialized activities reuse one contract instead of cloning logic.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.
- [ ] `StandalonePlayerActivity.kt` (>500 LOC) backed up to `temp/` before edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/host/PlayerHostCapabilities.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/host/StandalonePlayerHostController.kt` | New | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt` | Modified | ≤ 950 |

> `StandalonePlayerActivity.kt` >500 LOC → timestamped backup in `temp/` required before edit.

---

## Steps

### Step 01.1 - Define the host-capabilities contract

**Files:** `ui/player/host/PlayerHostCapabilities.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create interface `PlayerHostCapabilities` exposing the operations a player host must support and that are common to both the standalone and internal players: file operations (delete, share, info, rename), fullscreen toggle, gesture/keyboard event entry points, and the current media descriptor accessor. No implementation - declarations only. This is the seam the specialized activities will reuse.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/host/PlayerHostCapabilities.kt` exists.
- `Grep` - `interface PlayerHostCapabilities` matches exactly once.
- `Grep` - method declarations for `fun deleteCurrent`, `fun shareCurrent`, `fun renameCurrent`, `fun showInfo` present.

**Status:** `[ ]` not done

---

### Step 01.2 - Extract the standalone host controller

**Files:** `ui/player/host/StandalonePlayerHostController.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `StandalonePlayerHostController` that wraps the existing standalone helpers (`StandaloneFileOperationsHandler`, `StandaloneFullscreenManager`, `StandaloneViewManager`, `StandaloneVideoControlsManager`, `StandaloneVideoTouchDelegate`) and implements `PlayerHostCapabilities` by delegating to them. Do not reimplement any helper logic - only orchestrate the existing ones. Behavior must be identical to today.

**Verification:**

- `Glob` - `StandalonePlayerHostController.kt` exists.
- `Grep` - `class StandalonePlayerHostController` matches once and `: PlayerHostCapabilities` present.
- `Grep` - references to `StandaloneFileOperationsHandler` and `StandaloneFullscreenManager` present (delegation, not reimplementation).
- `Grep -n "Log\.d\("` returns zero hits in the new file.

**Status:** `[ ]` not done

---

### Step 01.3 - Route StandalonePlayerActivity through the controller

**Files:** `ui/player/StandalonePlayerActivity.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Back up `StandalonePlayerActivity.kt` to `temp/` first. Then make the activity obtain `PlayerHostCapabilities` via `StandalonePlayerHostController` and route its file-op / fullscreen / gesture calls through the controller. No user-visible behavior change. Keep all existing flows (audio/video/image/doc/text) working through the single activity exactly as before - this phase does not split anything yet.

**Verification:**

- `Grep` - `StandalonePlayerHostController` referenced in `StandalonePlayerActivity.kt`.
- Build: `/build` standardDebug passes (`expected: BUILD SUCCESSFUL | actual: <record>`).
- Manual smoke deferred to Phase 05; this phase keeps the existing launch path intact.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] Project compiles - run `/build` (standardDebug).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `scripts/catalog_sync.ps1`.

---

## Handoff Notes to Next Phase

Establishes `PlayerHostCapabilities` + `StandalonePlayerHostController` as the reuse seam. Specialized activities (Phase 04) construct the controller; internal player (Phase 02) aligns to the same contract where it is the parity blocker.

---

## Rollback Plan

Revert phase commit(s). The two new files are additive; the activity edit is behavior-preserving wiring - reverting restores the prior direct-helper calls. No data migration or manifest change in this phase.
