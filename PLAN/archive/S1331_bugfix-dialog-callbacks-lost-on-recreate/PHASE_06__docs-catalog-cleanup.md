# Phase 06 - Docs, catalog and capability record

**Strategic spec:** [`../S1331_bugfix-dialog-callbacks-lost-on-recreate.md`](../S1331_bugfix-dialog-callbacks-lost-on-recreate.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phases 01, 02, 03, 04, 05
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

The class catalog reflects the five changed `newInstance` signatures and the removed callback interface, the
architecture reference records the dialog-result convention so the next dialog is written correctly the first
time, and the dev log carries one entry for the ticket.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Working tree contains no uncommitted work from another ticket that would widen the catalog diff.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ARCHITECTURE.md` | Modified | ≤ 40 added |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CHANGELOG.md` | Appended via script | n/a |

`dev/CATALOG/app_v2.jsonl` and its `.md` are gitignored local indexes - regenerate, never hand-edit and never
commit.

---

## Steps

### Step 06.1 - Record the dialog-result convention in the architecture reference

**Files:** `docs/ARCHITECTURE.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a short subsection near the existing "Button Taxonomy" standard, named for dialog result delivery. State
> the rule: a `DialogFragment` never holds its result callback in a field, because `FragmentManager` rebuilds a
> restored dialog with the no-arg constructor; results go through `setFragmentResult` under a request key read
> from `arguments` in `onCreate`, and hosts register `setFragmentResultListener` in their own
> `onCreate`/`onViewCreated` rather than at the moment the dialog is opened. Name
> `SearchableLanguagePickerDialog` as the reference implementation. Record the one accepted limitation: when
> the opening host is a plain `AlertDialog` instead of a `DialogFragment`, the result is delivered the next
> time that picker is opened, because the plain dialog does not survive recreation.
>
> Prose only - no new table. Keep it to the rule plus the limitation; the per-dialog detail lives in the specs.

**Verification:**

- `Grep` - `setFragmentResultListener` matches in `docs/ARCHITECTURE.md`.
- `Grep` - `SearchableLanguagePickerDialog` matches in `docs/ARCHITECTURE.md`.

**Status:** `[x]` done

---

### Step 06.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 06.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once for the whole ticket. Five
> `newInstance` signatures changed and the `PermissionRationaleCallback` interface was removed, so the index is
> stale until this runs.

**Verification:**

- `Grep` - `PermissionRationaleCallback` returns zero hits in `dev/CATALOG/app_v2.jsonl`.
- Command exit code is 0.

**Status:** `[x]` done

---

### Step 06.3 - Journal the ticket

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> Add one dev-log entry for the ticket through `./scripts/add_to_dev_log.ps1` - one logical change, not one per
> touched file. Never hand-edit `dev/CHANGELOG.md`.
>
> Do not add a `docs/ALL_FEATURES.jsonl` record: this ticket ships no new capability, it restores behaviour the
> five dialogs were always meant to have. For the same reason `docs/FEATURES*.md` stays untouched - that file
> is `/skill-release`-owned and populated from the ALL_FEATURES diff.
>
> No settings were added, moved, renamed or changed in behaviour, so the Rule 22 settings-manifest regeneration
> does not apply. No user-visible strings changed, so no strings audit is required.

**Verification:**

- `Grep` - `S1331` matches in `dev/CHANGELOG.md`.
- Command exit code is 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` - across `app_v2/src`, zero hits for `onHostSelected`, `onApplyListener`, `onColorSelected`
      and `PermissionRationaleCallback`.
- [x] `Grep` - `onPicked` returns zero hits in phase 03's four touched files. Not project-wide: `onPicked` is
      also the in-dialog row-click parameter of `SearchableOptionPickerController.attach`, a different concept
      this ticket does not convert, live in eleven files outside this plan.
- [x] `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0 after the `docs/ARCHITECTURE.md`
      edit, and `generate.ps1 -Check` reports no drift.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. `/spec-dev` inserts the `Timber.d("S1331: ..")` probes and flips the
ticket to `BlockNeedUserTest` after this phase; the device check is the strategic §4 manual pass over all five
dialogs.

---

## Rollback Plan

Revert the `docs/ARCHITECTURE.md` edit. The catalog is a regenerated local index and the dev log is append-only;
neither needs a rollback.
