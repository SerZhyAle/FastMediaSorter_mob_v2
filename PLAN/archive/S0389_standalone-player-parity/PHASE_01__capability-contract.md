# Phase 01 - Capability Contract Extension

**Strategic spec:** [`../S0389_standalone-player-parity.md`](../S0389_standalone-player-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-09
**Completed:** 2026-06-09

---

## Objective

Extend the host-capability contract so standalone hosts can declare folder-paging availability and type-specific command-panel groups, without changing any runtime behavior yet.

---

## Prerequisites

- [ ] Strategic §6 research items blocking this phase are Resolved (none block Phase 01).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlayerHostCapabilities.kt` | Modified | ≤ 140 |

---

## Steps

### Step 01.1 - Add folder-paging capability flag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlayerHostCapabilities.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a capability that signals whether the host may page through neighbor files of the current file's folder (`supportsFolderPaging`). This is distinct from the existing list-navigation flag, which reflects a resource-backed playlist. The in-app host keeps its current behavior; standalone hosts will set this dynamically once a local folder is resolvable (Phase 03). Default it to `false` for standalone so behavior is unchanged until Phase 03 wires it.

**Verification:**

- `Grep` - `supportsFolderPaging` matches in `PlayerHostCapabilities.kt`.
- `Grep` - the standalone capability factory/instances set `supportsFolderPaging = false` by default (no runtime change yet).

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Verification 2/2 PASS. Added `supportsFolderPaging: Boolean get() = false` to interface. Files: PlayerHostCapabilities.kt (+7 LOC). Dev log recorded.

---

### Step 01.2 - Add type-specific command-panel group capability

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlayerHostCapabilities.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a capability expressing that the host may show single-file type-specific command-panel actions (`supportsTypeSpecificActions`). Standalone hosts declare it `true`; the in-app host already shows them. This flag is the seam Phase 04 reads to render parity buttons - no `if standalone` checks. Keep the change declaration-only; no panel code reads it yet.

**Verification:**

- `Grep` - `supportsTypeSpecificActions` matches in `PlayerHostCapabilities.kt`.
- `Grep -n "Log\.d\("` returns zero hits in `PlayerHostCapabilities.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Verification 2/2 PASS. Added `supportsTypeSpecificActions: Boolean get() = true` to interface. Files: PlayerHostCapabilities.kt (+8 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `PlayerHostCapabilities.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public contract changed).

---

## Handoff Notes to Next Phase

The contract now carries `supportsFolderPaging` and `supportsTypeSpecificActions`. Phase 03 flips `supportsFolderPaging` at runtime when a local folder is resolved; Phase 04 reads `supportsTypeSpecificActions` to render parity buttons. No host yet acts on the new flags.

---

## Rollback Plan

Revert phase commit - additive contract change only, no user-facing surface touched.
