# Phase 01 - Draw-overlay reach-through

**Strategic spec:** [`../S1637_activity-logic-debt-player-hosts.md`](../S1637_activity-logic-debt-player-hosts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02 - the field cannot leave the host until the factory exists to supply it
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Stop `PlayerDrawingSaveHelper` and `DrawCropCompositor` reading `activity.mergeDrawOverlayUseCase` off the host, so the field can leave `PlayerActivity`.

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
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDrawingSaveHelper.kt` | Modified | ≤ 680 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DrawCropCompositor.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1425 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ 965 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).

---

## Steps

### Step 01.1 - Take `MergeDrawOverlayUseCase` by constructor in `DrawCropCompositor`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DrawCropCompositor.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `MergeDrawOverlayUseCase` as a constructor parameter of `DrawCropCompositor` and replace both `activity.mergeDrawOverlayUseCase` reads (lines 25 and 104 at plan time) with that parameter. Update every construction site of `DrawCropCompositor` to pass the use case it already has in scope. Do not add an `@Inject` field to any Activity to satisfy this.
>
> **Correction found at implementation time:** the class already takes the use case by constructor and has since S0679. Lines 25 and 104 are that parameter and its single use, not reach-through reads - the strategic §6.1 line naming them was a mis-attributed grep. Both Verification predicates below pass against the tree as written, so this step is satisfied without an edit and removes no field.

**Why:**

Strategic §5.1 records that this use case is neither a constructor hand-off nor an activity-side behaviour call but a third form - a field read through the host from outside - and that the field cannot leave `PlayerActivity` while these two readers exist.

**Verification:**

- `Grep` - `activity.mergeDrawOverlayUseCase` returns zero hits in `DrawCropCompositor.kt`.
- `Grep` - `mergeDrawOverlayUseCase` appears in the constructor parameter list of `DrawCropCompositor`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - PRE-RESOLVED - no edit needed. DrawCropCompositor has taken MergeDrawOverlayUseCase by constructor since S0679. Verified against the tree: zero 'activity.mergeDrawOverlayUseCase' hits in the file, one 'private val mergeDrawOverlayUseCase: MergeDrawOverlayUseCase' constructor parameter. Both Verification predicates PASS as written; the strategic sites named at lines 25 and 104 are the parameter and its single use, not reach-through reads. Removes no field - budget unchanged.

---

### Step 01.2 - Take `MergeDrawOverlayUseCase` by constructor in `PlayerDrawingSaveHelper`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDrawingSaveHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `MergeDrawOverlayUseCase` as a constructor parameter of `PlayerDrawingSaveHelper` and replace all four `activity.mergeDrawOverlayUseCase.execute(..)` call sites (lines 230, 324, 512 and 597 at plan time) with that parameter. Update every construction site to pass it - there is exactly one, `PlayerManagerInitializer.kt:307`, which is why that file joins Files Touched. Where the helper constructs a `DrawCropCompositor`, forward the same instance rather than creating a second path to the use case.

**Why:**

Strategic §5.1 names these four call sites as the reason the field survives in the host, and §2 goal 1 requires the host to declare no domain `@Inject` field at all.

**Verification:**

- `Grep` - `activity.mergeDrawOverlayUseCase` returns zero hits in `PlayerDrawingSaveHelper.kt`.
- `Grep` - `mergeDrawOverlayUseCase` appears in the constructor parameter list of `PlayerDrawingSaveHelper`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - PlayerDrawingSaveHelper now takes MergeDrawOverlayUseCase by constructor; all four reach-through sites (the DrawCropCompositor construction at 230 plus three execute calls) read the parameter. Sole construction site PlayerManagerInitializer.kt:307 updated to pass it - still from the host field, which step 01.3 replaces with the factory. Verified: zero 'activity.mergeDrawOverlayUseCase' hits, one constructor parameter, five bare uses (parameter plus four sites), 613 LOC against the corrected 620 budget.
- 2026-08-14 - Correction to the previous entry: the 613/609 figures came from Measure-Object -Line, which does not count blank lines. Real counts are 671 -> 675 for PlayerDrawingSaveHelper.kt and 956 -> 959 for PlayerManagerInitializer.kt; both Files Touched budgets corrected accordingly. The +4 / +3 deltas and the step verdict are unchanged.

---

### Step 01.3 - Drop the field from `PlayerActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Delete the `@Inject lateinit var mergeDrawOverlayUseCase` declaration (line 886 at plan time) together with its import if no other reference remains. Pass the use case to the two helpers from wherever they are constructed, taking it from `ImageEditFactory` (Phase 02), which the host injects as `@Inject lateinit var imageEditFactory: ImageEditFactory` - the same shape the four standalone hosts already use for `StandaloneHostFactory`. A `*Factory` field is not a domain type, so it does not count as an `ActivityLogicViolation`.

**Why:**

Strategic §2 goal 1 states that neither host may declare an `@Inject` field of a domain type; this is the first of the 32 to go, and the strategic §5.1 sequencing puts it first because it is the only one whose fix reaches outside the two host files.

**Verification:**

- `Grep` - `mergeDrawOverlayUseCase` returns zero hits in `PlayerActivity.kt`.
- `Grep` - `@Suppress("ActivityLogicViolation")` returns zero hits in `PlayerActivity.kt`.
- `pwsh -NoProfile -File scripts/quality/assert-activity-logic-not-growing.ps1` reports `actual 31`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Field deleted from PlayerActivity; the host now injects ImageEditFactory instead, and PlayerManagerInitializer takes the use case from activity.imageEditFactory.mergeDrawOverlay. Verified: zero mergeDrawOverlayUseCase hits in PlayerActivity.kt, zero @Suppress(ActivityLogicViolation), gate reports actual 31 and ratcheted its baseline 32 -> 31. PlayerActivity back to 1424 lines, exactly where it started.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] `PlayerActivity.kt` is no longer than 1425 lines (INDEX line-budget constraint).
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The edit cluster now has exactly one shape left: an object handed to a constructor. Phase 02 can build a factory without a special case.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
