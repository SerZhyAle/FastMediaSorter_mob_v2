# Phase 05 - Remaining domain fields

**Strategic spec:** [`../S1637_activity-logic-debt-player-hosts.md`](../S1637_activity-logic-debt-player-hosts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03, Phase 04
**Blocks:** Phase 06
**Steps done:** 2 / 2
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Remove the seventeen non-cluster domain fields - eleven in `PlayerActivity`, six in `PhotoVideoStandaloneActivity` - each by whichever of the four S1329 forms fits it.

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
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1425 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | Modified | ≤ 1322 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerHostFactory.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/StandaloneHostFactory.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ 965 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewerFactory.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerFileOpsInitializer.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivityLifecycleBridge.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDrawingSaveHelper.kt` | Modified | ≤ 680 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerImageTranslationManager.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SaveVideoFrameManager.kt` | Modified | ≤ 300 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> Further files will be named by step 05.1's classification - a ViewModel or a helper receiving a moved dependency is listed in the dev log of the step that touches it.

---

## Steps

### Step 05.1 - Remove the eleven `PlayerActivity` fields

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> For each of the eleven remaining fields, read every use site first and pick exactly one of the four S1329 forms - factory hand-off, move behind a ViewModel, inherited settings flow, or dead field - then remove the field by that form in the same step. A field whose use sites mix forms is split by use site rather than assigned by majority. Where a dependency moves into an existing helper or ViewModel, add it there by constructor or by `@Inject`, never by reading it back off the activity. Preserve `dagger.Lazy` wrapping wherever the host had it.

**Why:**

Strategic §5.1 leaves the choice of form per field to the tactical layer, and §3.2 requires that moving a dependency must not create it earlier than the activity did - which is exactly what unwrapping a `dagger.Lazy` would do (CLAUDE.md Rule 18).

**Verification:**

- `Grep` - each of the eleven field names returns zero hits in `PlayerActivity.kt`.
- `Grep` - `activity.` followed by any of those names returns zero hits across `app_v2/src/main`.
- `Grep` - every field that was `Lazy<..>` before is still `Lazy<..>` at its new home.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Form chosen by measurement, not preference: all eleven fields are read once or twice and handed straight to a constructor in PlayerManagerInitializer, PlayerViewerFactory, PlayerFileOpsInitializer or a player helper, so the S1329 factory form applies - PlayerHostFactory, an @Inject-constructor supplier alongside ImageEditFactory. 42 reach-through sites across 8 files now read activity.playerHostFactory.<property>, and the host's resolved credentialsRepository accessor unwraps the factory's Lazy instead of its own. dagger.Lazy preserved: credentialsRepository is Lazy<NetworkCredentialsRepository> in the factory, as it was in the host (Rule 18). Verified: zero hits of any of the eleven names in PlayerActivity.kt, zero 'activity.<name>' reach-through sites left under app_v2/src/main, gate actual 6 (ratcheted 17 -> 6), file 1424 -> 1385 lines, a.ps1 fk exit 0.

---

### Step 05.2 - Remove the six `PhotoVideoStandaloneActivity` fields

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Remove the six remaining fields of this host by the same per-field reading as step 05.1. Do not extend `StandalonePlayerViewModel` with the six-dependency surface the original S1329 plan proposed.

**Why:**

Strategic §2 non-goals and S1329 §9 ADR-1 both refuse that surface, because a behavioural surface cannot hand an object to a manager constructor and would force signature changes on five shared managers.

**Verification:**

- `Grep` - each of the six field names returns zero hits in `PhotoVideoStandaloneActivity.kt`.
- `pwsh -NoProfile -File scripts/quality/assert-activity-logic-not-growing.ps1` reports `actual 0`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - All six fields removed without re-adding a single dependency: StandaloneHostFactory already owned every one of them. Four hand-wired constructions moved to the factory entry points written for them - createViewManager, createFileOperationsHandler, createFileInfoDialog, createSettingsManager - which alone retired credentialsRepository, playbackPositionRepository, resolveOpenInFmsTargetUseCase and getDestinationsUseCase. The two that survive as direct reads, settingsRepository (seven UI decisions) and fileOperationUseCase (two ImageCropManager constructions), now read the factory's own properties, made public for exactly that. Four orphaned imports removed (Rule 20). Verified: zero hits of all six names, gate actual 0 - the ticket's goal 2 - ratcheted 6 -> 0; file 1321 -> 1278 lines; a.ps1 dq exit 0 with the Hilt graph regenerated.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done` - there are two.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Neither host file is longer than it was at phase start (INDEX line-budget constraint).
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The source count is zero. Phase 06 lowers the gate to match and closes the paperwork.

---

## Rollback Plan

Revert phase commit(s) per host - the two steps are independent and either can be reverted alone.
