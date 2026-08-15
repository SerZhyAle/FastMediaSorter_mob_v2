# Phase 05 - Docs and catalog cleanup

**Strategic spec:** [`../S1010_write-resource-picker-local-folder.md`](../S1010_write-resource-picker-local-folder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, 03, 04
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-02
**Completed:** 2026-08-02

---

## Objective

Regenerate the `app_v2` catalog for the one new class and the three modified public signatures, and confirm no
stray `TODO(phase-*)` markers remain across the whole ticket before handing off to `/spec-check`.

---

## Prerequisites

- [ ] Phases 02, 03 and 04 are all ✅ Done - every one of the 7 target settings exposes the option.

---

## Files Touched

No source files touched by this phase - catalog index regeneration only (`dev/CATALOG/app_v2.jsonl`/`.md` are
local, gitignored indexes per CLAUDE.md "Catalog & Navigation" - regenerated, never hand-edited or committed).

---

## Steps

### Step 05.1 - Regenerate the app_v2 catalog

**Files:** none (catalog index only)
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` so `LocalFolderDestinationPickerManager`
> gets a catalog entry and the modified `SettingsViewModel` / `DestinationPickerDialog` signatures are current.
> Then fill `role`/`status` for the new class via `set.ps1` if the sync leaves them at their default (matches
> existing helpers like `OperationsScheduledManager` - `role: ui`, no flavor restriction, since this class is
> `src/main` and used by all flavors uniformly).

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "LocalFolderDestinationPickerManager"`
  returns exactly one record.
- Exit code of `catalog_sync.ps1` is 0.

**Status:** `[x]` done

---

### Step 05.2 - Confirm zero stray markers and dev-log completeness

**Files:** none (verification only)
**Depends on:** Step 05.1

**Prompt for developer:**

> `Grep` the whole ticket's touched-file set (`SettingsViewModel.kt`, `LocalFolderDestinationPickerManager.kt`,
> `OperationsSettingsFragment.kt`, `EdgeGestureConfigDialogFragment.kt`, `DestinationPickerDialog.kt`,
> `VideoSettingsFragment.kt`) for `TODO(phase-` - expect zero hits across all five. Confirm every one of those
> six files has a dev-log entry from its own phase's closure (Phases 01-04 each already log their own files at
> their Phase Done Criteria step) - this step adds no new dev-log line of its own, it only verifies none were
> skipped.

**Verification:**

- `Grep` for `TODO\(phase-` across the six files listed above returns zero hits.
- `dev/CHANGELOG.md` (via `add_to_dev_log.ps1`'s own append, never hand-edited) has one entry per file in the
  list above.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (Step 05.1).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] No public API changed by this phase itself - catalog regen and verification only.

---

## Step Log

- 2026-08-02 - Step 05.1: Verification 2/2 PASS. `catalog_sync.ps1 -Module app_v2` exit 0 (reported already up to date -
  the per-step `post-change.ps1` closures had kept the index current); `query.ps1 -ClassMatches
  "LocalFolderDestinationPickerManager"` returns exactly 1 record. Manual fields filled via `set.ps1`
  (`status=new`, plus a `role` description). Note: `set.ps1 -Role` takes free text, not the category `ui` the prompt's
  shorthand suggests - `layer: ui` is an auto-field already set correctly by the scan, and the named sibling
  `OperationsScheduledManager` in fact carries `role: ""`/`status: unknown` itself, so no sibling value was copied.
- 2026-08-02 - Step 05.2: Verification 2/2 PASS. Zero `TODO(phase-` hits across `app_v2/src/main`. All six touched
  files carry an S1010 dev-log row (`dev/CHANGELOG.md` lines 24064-24071, eight rows - `OperationsSettingsFragment.kt`
  and `EdgeGestureConfigDialogFragment.kt` each logged once per step).
- 2026-08-02 - No phase-boundary audit: `Files Touched` is empty for this phase (catalog/verification only), which the
  protocol excludes.
- 2026-08-02 - Rule 22 (settings-doc sync) assessment: no regeneration required, and none was demanded. The
  `settings-doc-sync-gate` reported `SKIP - not applicable` on every one of the six closures; its trigger is
  path-based (`res/layout/fragment_settings_*.xml`, `ui/settings/search/`, `SettingsSearchAvailabilityModule.kt`,
  `docs/settings/`, `docs/SETTINGS_REFERENCE`) and this ticket touched none of them. Substantively that is correct:
  no setting's presence, position, title or hosting surface changed - all seven keep their existing rows. What
  changed is the option list rendered inside their resource-picker dialog, which the settings manifest does not
  describe.

---

## Handoff Notes to Next Phase

Final phase. Per INDEX.md Completion Gate: `docs/FEATURES*.md` stays untouched (owned by `/skill-release`);
`docs/ALL_FEATURES.jsonl` gets its record automatically at the `/spec-dev` `Implemented`/`BlockNeedUserTest`
transition (strategic §8 already names the capability - not "Без изменений"), not as a step here. Given the
strategic acceptance criteria (§11) are directly UI-observable (option visible, folder browser opens, write-check
gates acceptance, hidden from the general list), `/spec-dev` is expected to end this ticket at `BlockNeedUserTest`
with `Timber.d("S1010: ...")` tags at each of the three insertion points, not straight to `Implemented` - handled
by `/spec-dev`'s own generic on-device-verification logic, not encoded as a step here.

---

## Rollback Plan

No source changed by this phase - nothing to roll back beyond re-running catalog sync.
