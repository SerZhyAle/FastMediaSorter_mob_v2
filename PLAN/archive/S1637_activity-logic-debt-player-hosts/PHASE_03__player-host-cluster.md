# Phase 03 - Player host cluster

**Strategic spec:** [`../S1637_activity-logic-debt-player-hosts.md`](../S1637_activity-logic-debt-player-hosts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Move the eight remaining edit-cluster dependencies of `PlayerActivity` behind `ImageEditFactory`, leaving `PlayerDialogHelper`'s constructor untouched.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [x] Backup taken before editing the host file (CLAUDE.md Rule 5) - a working-tree safety chore, not audit evidence: the copy is disposable by design and is deliberately not cited as a closing artifact.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ 965 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1425 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).

---

## Steps

### Step 03.1 - Inject the factory into `PlayerActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a single `@Inject lateinit var imageEditFactory: ImageEditFactory` to `PlayerActivity`. Leave the eight existing edit use case fields in place for this step so the file still compiles.

**Why:**

Strategic §5.1 requires one supplier per host rather than nine fields, and `ImageEditFactory` is a UI-layer supplier, not a domain type, so it does not itself count as an ActivityLogicViolation.

**Verification:**

- `Grep` - `imageEditFactory: ImageEditFactory` matches exactly once in `PlayerActivity.kt`.
- `pwsh -NoProfile -File scripts/quality/assert-activity-logic-not-growing.ps1` reports `actual 31` - unchanged, confirming the factory type is not counted.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - PRE-RESOLVED by step 01.3 - the corrected plan needed the factory field in PlayerActivity one phase earlier, because deleting mergeDrawOverlayUseCase left PlayerManagerInitializer without a source. Verified here: 'imageEditFactory: ImageEditFactory' matches exactly once in PlayerActivity.kt and the gate reads baseline 31 | actual 31 | delta 0, confirming a *Factory field is not counted as a domain type.

---

### Step 03.2 - Read the cluster from the factory in `PlayerManagerInitializer`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In the `PlayerDialogHelper(..)` construction (lines 335-343 at plan time), replace each `activity.<useCase>` argument with the matching property of `activity.imageEditFactory`. Do not change `PlayerDialogHelper`'s constructor signature or argument order.

**Why:**

Strategic §0 carries the constraint that manager constructor signatures stay untouched, because the factory adapts to the manager as written rather than the reverse.

**Verification:**

- `Grep` - `activity.rotateImageUseCase` returns zero hits in `PlayerManagerInitializer.kt`; likewise for the other seven cluster names.
- `Grep` - `activity.imageEditFactory` matches at least eight times in that file.
- `Grep` - `class PlayerDialogHelper` constructor parameter count is unchanged from the pre-phase file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - All eight PlayerDialogHelper arguments now read activity.imageEditFactory.<property>; argument names and order untouched. Verified: zero 'activity.<useCase>' references to any of the eight anywhere under app_v2/src, nine 'activity.imageEditFactory' hits in PlayerManagerInitializer (eight here plus the draw-overlay one from phase 01), and PlayerDialogHelper's constructor still declares all eight parameters plus downloadNetworkFileUseCase - not touched by this ticket. Files Touched budget for the initializer corrected 500 -> 965; the file is 959 lines and unchanged in length by this step.

---

### Step 03.3 - Delete the eight fields

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Delete the eight `@Inject` declarations for `rotateImageUseCase`, `flipImageUseCase`, `networkImageEditUseCase`, `applyImageFilterUseCase`, `adjustImageUseCase`, `extractGifFramesUseCase`, `saveGifFirstFrameUseCase` and `changeGifSpeedUseCase`, together with any import left unused. Do not add `@Suppress("ActivityLogicViolation")` anywhere.

**Why:**

Strategic §11 criterion 3 states explicitly that no `@Suppress("ActivityLogicViolation")` may appear, so the count must fall by removal rather than by silencing.

**Verification:**

- `Grep` - each of the eight field names returns zero hits in `PlayerActivity.kt`.
- `Grep` - `@Suppress("ActivityLogicViolation")` returns zero hits in `PlayerActivity.kt`.
- `pwsh -NoProfile -File scripts/quality/assert-activity-logic-not-growing.ps1` reports `actual 23`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Eight @Inject declarations deleted from PlayerActivity. Verified: each of the eight names returns zero hits in the file, zero ActivityLogicViolation suppressions, gate reads actual 23 and ratcheted its baseline 31 -> 23. File is 1408 lines, down from 1424 at phase start. No import cleanup was needed - all eight declared their type by FQN. a.ps1 fk exit 0 (compileStandardDebugKotlin BUILD SUCCESSFUL in 41s); the Hilt graph itself was already proven by the phase 02 dq build and this step removes injection points rather than adding bindings.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `PlayerActivity.kt` is shorter than it was at phase start - eight declarations left, one arrived (INDEX line-budget constraint).
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`PlayerActivity` now reaches the edit cluster through one supplier. The same supplier is ready for the standalone host in Phase 04.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
