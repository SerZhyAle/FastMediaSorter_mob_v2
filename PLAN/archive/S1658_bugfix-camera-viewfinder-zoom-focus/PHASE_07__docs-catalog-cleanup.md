# Phase 07 - Docs, catalog, cleanup

**Strategic spec:** [`../S1658_bugfix-camera-viewfinder-zoom-focus.md`](../S1658_bugfix-camera-viewfinder-zoom-focus.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04, Phase 06
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** -
**Completed:** 2026-08-15

---

## Objective

Regenerate every artifact the two mechanisms invalidated - settings manifest and reference, class catalog, capability inventory - and record the shipped capability.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree carries no unrelated in-flight edits to the files below.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/settings/settings-annotations.json` | Modified | ≤ 20 |
| `docs/settings/settings-manifest.json` | Regenerated | - |
| `docs/SETTINGS_REFERENCE*.md` | Regenerated | - |
| `docs/ALL_FEATURES.jsonl` | Modified | ≤ 4 |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 07.1 - Update the aspect row annotation

**Files:** `docs/settings/settings-annotations.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Rewrite the `rowCameraAspect` annotation so it names three values rather than two, states that the selection now drives the live preview stream and the saved file rather than an overlay frame, and records 16:9 as the default. Keep the entry's existing shape and neighbouring keys untouched.

**Why:**

Rule 22 makes the annotation the hand-written half of the settings documentation, and this row's behaviour, value set and default all changed in Phases 02 to 04.

**Verification:**

- `Grep` - `rowCameraAspect` present in `settings-annotations.json`.
- `Grep` - the annotation text mentions three values and the 16:9 default.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - rowCameraAspect annotation now names all three values, records 16:9 as the default and states the selection drives the live viewfinder and the saved file rather than an overlay frame; en/ru/uk kept in parity, neighbouring keys untouched.

---

### Step 07.2 - Regenerate the settings manifest and reference

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`
**Depends on:** Step 07.1

**Prompt for developer:**

> Regenerate both artifacts with the project's own generator - never by hand - and then run `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1 -Gate` and read its exit code.

**Why:**

Rule 22 requires the manifest and reference to be regenerated for any change to a setting's presence, behaviour, position or naming, including one hosted in a dialog.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1 -Gate` exits 0.
- `Grep` - the regenerated `SETTINGS_REFERENCE.md` carries the camera aspect row.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - reindex-settings regenerated the stale manifest mirror (exit 2 = drift refreshed), render-settings-reference rebuilt all four locales, assert-settings-doc-sync -Gate exits 0 reporting manifest fresh and reference up to date. SETTINGS_REFERENCE.md line 365 carries the camera aspect row with the new three-value text.

---

### Step 07.3 - Record the shipped capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 07.2

**Prompt for developer:**

> Add records via `pwsh -NoProfile -File scripts/all_features/add.ps1` - one for the viewfinder aspect selection driving the live stream with a full-screen option, one for per-lens capture-set memory surviving a restart. English only, `spec` field `S1658`. Do not hand-edit the file, and do not touch `docs/FEATURES*.md` - that showcase is `/skill-release`-owned.

**Why:**

CLAUDE.md section 11 makes `ALL_FEATURES.jsonl` the developer inventory every spec's delivered capability is recorded in, and both mechanisms of this ticket are user-visible capabilities rather than internal repairs.

**Verification:**

- `Grep` - `S1658` present in `docs/ALL_FEATURES.jsonl` at least twice.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Two ALL_FEATURES records added via add.ps1 (never hand-edited): camera-capture.viewfinder-aspect-selection and camera-capture.per-lens-capture-set-memory, both spec S1658, flavors standard/legacy/vr/noLegal taken from the existing in-app-camera records rather than assumed. validate.ps1 exit 0, 718 records.

---

### Step 07.4 - Sync the class catalog and close mechanically

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 07.3

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once for the whole ticket, then fill `role` and `status` for `CameraAspectSelection` and `CameraLensSettingsMemory` via `set.ps1`. Close the whole change set through `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<the whole changed set>" -ScopeToFile -Target "S1658" -Description "Viewfinder aspect drives the stream; per-lens capture-set memory" -ChangeType Mixed` and read its verdict line rather than assuming it passed.

**Why:**

Two classes shipped and one was deleted, so the catalog no longer matches the tree, and CLAUDE.md section 12 routes mechanical closure through the facade rather than through hand-rolled steps.

**Verification:**

- `Grep` - `CameraAspectSelection` and `CameraLensSettingsMemory` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `ResultFrameOverlayView` returns zero hits in `dev/CATALOG/app_v2.jsonl`.
- `post-change.ps1` prints `post-change: PASS` (or `PASS WITH ADVISORIES` with each advisory read and addressed) and exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - catalog_sync reported the index already current; role and status set for CameraAspectSelection and CameraLensSettingsMemory. Catalog verified by query: both new classes present, ResultFrameOverlayView returns no records. post-change PASS after acknowledging the feature-inventory registry entry (schema needed no change - add.ps1 wrote both records into the existing shape and validate.ps1 exits 0); dev-log deduped to a single row.

---

## Phase Done Criteria

- [x] Every `Step 07.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] Dev log entry added for the ticket via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. The device verification of strategic §4 is a `BlockNeedUserTest` gate on `SM-G996U1`, not a step here: three logical back lenses are what the claim was observed on, and no emulator enumerates them.

---

## Rollback Plan

Revert phase commit(s). Every artifact in this phase is generated from a source of truth, so a revert of the source phases plus a regeneration restores them exactly.
