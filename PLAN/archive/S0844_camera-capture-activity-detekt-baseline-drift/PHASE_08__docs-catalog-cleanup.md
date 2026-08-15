# Phase 08 - Docs, Catalog & Baseline Cleanup

**Strategic spec:** [`../S0844_camera-capture-activity-detekt-baseline-drift.md`](../S0844_camera-capture-activity-detekt-baseline-drift.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03, Phase 04, Phase 05, Phase 06, Phase 07
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-07-02
**Completed:** 2026-07-02

---

## Objective

Confirm `CameraCaptureActivity.kt` is fully detekt-clean (including `LargeClass`/`TooManyFunctions`), remove the now-permanently-stale baseline entries keyed to the pre-refactor class signature, and close out catalog/dev-log bookkeeping.

---

## Prerequisites

- [ ] Phases 02-07 are all ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `config/detekt/baseline-app_v2.xml` | Modified (deletions only) | n/a (deletion) |
| `dev/CATALOG/app_v2.jsonl` | Regenerated (gitignored, not committed) | n/a |

---

## Steps

### Step 08.1 - Full detekt verification

**Files:** none (verification-only step)
**Depends on:** - start of phase

**Prompt for developer:**

> Run `./gradlew.bat :app_v2:detekt --rerun-tasks` and inspect `app_v2/build/reports/detekt/detekt.xml` for the `CameraCaptureActivity.kt` `<file>` node. It must report zero `<error>` entries - specifically no `LargeClass`, `TooManyFunctions`, `Wrapping`, `ImportOrdering`, `ReturnCount`, or `MagicNumber` for this file. If `LargeClass`/`TooManyFunctions` still fire, the extractions in Phases 02-07 did not remove enough functions/lines - do not proceed to Step 08.2 until this is zero; add a follow-up extraction (fold `renderGridOverlay`/`updateMicrophoneIcon` or another small UI-only function into an existing helper from Phases 02-07) rather than touching the excluded shutter/recording cluster.

**Verification:**

- `Grep` on `app_v2/build/reports/detekt/detekt.xml` for `CameraCaptureActivity.kt` - the `<file name=".../CameraCaptureActivity.kt">` node contains zero `<error` lines before its closing `</file>`.

**Status:** `[x]` done

---

### Step 08.2 - Remove stale baseline entries for the old class signature

**Files:** `config/detekt/baseline-app_v2.xml`
**Depends on:** Step 08.1

**Prompt for developer:**

> Delete exactly the `<ID>` lines in `config/detekt/baseline-app_v2.xml` whose signature text is keyed to `CameraCaptureActivity.kt`'s pre-refactor supertype list - the `TooManyFunctions:CameraCaptureActivity.kt$CameraCaptureActivity : BaseActivityHostCallbacks` line, the `Wrapping:CameraCaptureActivity.kt$CameraCaptureActivity$BaseActivity&lt;ActivityCameraCaptureBinding&gt;(), CameraCaptureFlowManager.Host, CameraCaptureGestureManager.Callbacks` line, and the `ReturnCount:CameraCaptureActivity.kt$CameraCaptureActivity$private fun openLastCapture()` line (confirm this exact line still exists and its function still returns 3+ times post-refactor before deleting it - if `openLastCapture()` was untouched by Phases 02-07 and still has 3 returns, KEEP this entry, do not delete it; only delete entries whose signature text can structurally never match the new code, i.e. `TooManyFunctions`/`Wrapping` keyed to the removed supertypes). Do not touch any other file's baseline entries, and do not run `.\gradlew.bat :app_v2:detektBaseline` (project-wide re-freeze - forbidden, captures other tickets' in-flight WIP per strategic §2 non-goals). Preserve the file's existing ASCII-sorted `<ID>` ordering.

**Verification:**

- `Grep` - `TooManyFunctions:CameraCaptureActivity.kt$CameraCaptureActivity : BaseActivityHostCallbacks` returns zero hits in `config/detekt/baseline-app_v2.xml`.
- `Grep` - `Wrapping:CameraCaptureActivity.kt$CameraCaptureActivity$BaseActivity` returns zero hits in `config/detekt/baseline-app_v2.xml`.
- `Grep` on the same file for `CameraCaptureActivity` - any surviving entries are ones this step's prompt explicitly justified keeping (e.g. `openLastCapture` `ReturnCount`, if still present).

**Status:** `[x]` done

---

### Step 08.3 - Catalog and dev log closure

**Files:** none (bookkeeping-only step)
**Depends on:** Step 08.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` followed by `pwsh -NoProfile -File dev/CATALOG/scripts/render.ps1 -Module app_v2` (or `scripts/catalog_sync.ps1 -Module app_v2` if it wraps both) to pick up the 6 new helper classes. Set `role`/`status` for each new class via `set.ps1` if the scan leaves them `unknown`. Run `.\scripts\add_to_dev_log.ps1` once for the tactical INDEX.md/phase files batch (via `close-and-log.ps1 -DevLogs` per CLAUDE.md journaling-granularity rule) and once summarizing the `CameraCaptureActivity.kt` decomposition + the 6 new files + the baseline cleanup as a single logical entry.

**Verification:**

- `Grep` - each of the 6 new class names (`CameraOverlayRotationManager`, `CameraZoomControlsManager`, `CameraCaptureResultManager`, `CameraCaptureSaveDestinationLabelManager`, `CameraCaptureGestureCallbackHandler`, `CameraSettingsCallbackHandler`) appears at least once in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `dev/CHANGELOG.md` has an entry mentioning `S0844` for this closing batch.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 08.*` above is `[x] done`.
- [x] `standard debug` build passes (`a.ps1 dq`, full assembleStandardDebug, BUILD SUCCESSFUL).
- [x] Project-wide `:app_v2:detekt` still reports unrelated new findings in 58 other in-flight files (not this ticket's) - `assert-detekt.ps1 -ChangedFiles` scoped to this ticket's 8 touched files reports PASS (none of them have any finding).
- [x] `Grep` for `TODO(phase-08)` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next action after this phase is `/spec-check S0844`.

---

## Rollback Plan

Low-risk: baseline-entry deletion is reversible via re-adding the deleted lines; no data migration or user-facing surface changed.
