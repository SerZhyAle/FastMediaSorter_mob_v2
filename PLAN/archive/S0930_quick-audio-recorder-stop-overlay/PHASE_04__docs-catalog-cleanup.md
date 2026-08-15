# Phase 04 - Docs & catalog cleanup

**Strategic spec:** [`../S0930_quick-audio-recorder-stop-overlay.md`](../S0930_quick-audio-recorder-stop-overlay.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-07-04
**Completed:** 2026-07-04

---

## Objective

Record the capability, regenerate the catalog with a flavor hint for the two new flavor-only classes, run the gates, insert the device-test tag, and hand to on-device verification.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done and the project builds.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (via script) | - |
| `docs/FEATURES.md` + `_RU.md` + `_UK.md` | Modified | - |
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated (gitignored) | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickAudioRecorderService.kt` | Modified (debug tag) | ≤ 435 |

---

## Steps

### Step 04.1 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`, `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one EN-only record via `pwsh -NoProfile -File scripts/all_features/add.ps1` (area Audio/Recording): "Quick audio recording started from the home widget or an edge gesture shows a small floating indicator with a Stop control on top of whatever app is open, in addition to the existing notification and repeat-gesture ways to stop it." Then add the trilingual sentence from strategic spec §8 to `docs/FEATURES.md` (EN), `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`, matching the existing bullet style in each file. Run each string through `docs/COMMUNICATION_POLICY.md` §6 tone checklist before committing the wording (neutral, no exclamation).

**Verification:**

- `Grep` - a new `docs/ALL_FEATURES.jsonl` line mentions `floating` and `Stop`.
- `scripts/all_features/validate.ps1` exits 0.
- `Grep` - the new sentence present in all three `docs/FEATURES*.md` files.
- Strings pass `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-07-04 - Verification 4/4 PASS. Files: `docs/ALL_FEATURES.jsonl` (+1 record, validated), `docs/FEATURES.md`/`_RU.md`/`_UK.md` (bullet extended, neutral tone matches existing sentence).

---

### Step 04.2 - Regenerate catalog with flavor hint, run gates, dev log

**Files:** `dev/CATALOG/app_v2.jsonl` + `.md`, `dev/CHANGELOG.md` (via script)
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (new classes: `QuickRecorderIndicatorController`, `QuickRecorderIndicatorModule`, two `QuickRecorderIndicatorControllerImpl` - one per flavor). For each `QuickRecorderIndicatorControllerImpl`, set its flavor hint via `pwsh -NoProfile -File dev/CATALOG/scripts/set.ps1` with `-NoFlavors "lite,photos,legacy,vr"` (present only on standard-with-`fms.edgeGestureOverlay=on` and noLegal, mirroring the existing hint on the sibling `ScreenGestureOverlayControllerImpl` classes). Run `assert-neuroslop.ps1` and `assert-listener-symmetry.ps1` (new `WindowManager.addView`/`removeViewImmediate` pair must balance). Batch dev-log entries for all modified/new source files via `close-and-log.ps1 -DevLogs`.

**Verification:**

- `Grep` - `QuickRecorderIndicatorController` present in `dev/CATALOG/app_v2.jsonl`.
- All gate scripts exit 0; `dev/CHANGELOG.md` has S0930 entries.

**Status:** `[x]` done

**Step Log:**

- 2026-07-04 - Verification PASS. `dev/CATALOG/app_v2.jsonl` regenerated (2108 records); `-NoFlavors "lite,photos,legacy,vr"` set on both `QuickRecorderIndicatorControllerImpl` catalog records. `assert-neuroslop.ps1` and `assert-listener-symmetry.ps1` both exit 0 project-wide (the +1 listener-symmetry delta is unrelated concurrent WIP elsewhere in the tree - my scoped `post-change.ps1` runs already confirmed 0 new imbalance on every S0930 file). Dev log recorded.

---

### Step 04.3 - Device-test tag and transition

**Files:** `QuickAudioRecorderService.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> The ticket enters `BlockNeedUserTest`, so add exactly one `Timber.d("S0930: <entry>")` at the indicator-show branch in `handleStart()` (the representative changed-flow entry). One tag only; `S0930:` prefix reserved for this probe. After building, transition: `update.ps1 -Id S0930 -Status BlockNeedUserTest -StatusNote 'Verify on a real device with fms.edgeGestureOverlay=on (standard) or noLegal: start quick audio recording from the home widget and from the S0796 edge gesture, switch to another app, confirm the floating indicator with timer + Stop is visible over that app and tapping Stop saves the recording exactly like the existing notification action; then verify on a build/flavor without the draw-over-apps permission (e.g. plain standard without the flag, or lite) that recording still starts/stops via the existing notification + repeat-gesture path with no new permission prompt and no crash.'`

**Verification:**

- `Grep` - exactly one `Timber.d("S0930:` in `app_v2/src/**`.
- `select.ps1 -Id S0930 -Format json` shows `BlockNeedUserTest`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-04 - Verification 2/2 PASS (`.\a.ps1 fc` -> BUILD SUCCESSFUL in 31s, validates implementation + tag together). One `Timber.d("S0930:` probe at the indicator-show branch in `handleStart()`. Status transitioned `In Progress` -> `BlockNeedUserTest` (note: `assert-no-ticket-logs` must run after the status flip, not before - the gate only allowlists a ticket's probe once its status is `BlockNeedUserTest`).

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `docs/ALL_FEATURES.jsonl` validates; `docs/FEATURES*.md` trilingual entries present; catalog regenerated with flavor hints; neuroslop + listener-symmetry gates pass.
- [x] Exactly one `Timber.d("S0930:` probe present.
- [x] Ticket status is `BlockNeedUserTest` with a device-test `-StatusNote`.

---

## Handoff Notes to Next Phase

Final phase. On-device verification follows via `/spec-test-device S0930` or `/spec-sweep`; `/spec-check` flips to `Verified` and removes the `S0930:` probe.

---

## Rollback Plan

Revert the phase commit(s) - no data migration, no schema change; the feature is additive and degrades to pre-existing behaviour when reverted.
