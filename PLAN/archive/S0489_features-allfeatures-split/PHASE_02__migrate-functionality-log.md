# Phase 02 - Migrate FUNCTIONALITY.log

**Strategic spec:** [`../S0489_features-allfeatures-split.md`](../S0489_features-allfeatures-split.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, 05
**Steps done:** 3 / 3
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Convert the chronological `dev/FUNCTIONALITY.log` into current-state ALL_FEATURES records and retire the log: collapse ADD/CHANGE/FIX history per feature into one active record, then stop writing the log.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`add.ps1` + schema available).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (data) | n/a |
| `scripts/all_features/migrate_from_log.ps1` | New | ≤ 220 |
| `dev/FUNCTIONALITY.log` | Modified (retire header) | ≤ 10 |
| `scripts/add_to_functionality_log.ps1` | Modified (deprecation guard) | ≤ 30 |

---

## Steps

### Step 02.1 - One-shot migration converter

**Files:** `scripts/all_features/migrate_from_log.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Author a one-shot converter reading `dev/FUNCTIONALITY.log`. Group entries by the feature they describe (collapse repeated FIX/CHANGE/verify lines for the same capability into a single current-state record; drop entries whose capability was later DELETEd). For each surviving feature emit a record via `scripts/all_features/add.ps1`: derive `id`/`area`/`name`/`description` from the log text, set `spec` to the referenced `Sxxxx` when present, set `flavors` conservatively to `standard` unless the text names a flavor (vr/noLegal/lite/photos/legacy). Print a summary count. Idempotent: re-running upserts, never duplicates.

**Verification:**

- `Glob` - `scripts/all_features/migrate_from_log.ps1` exists.
- Run it; `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0 afterward.
- `Grep -c` of `"spec":"S` in `docs/ALL_FEATURES.jsonl` is > 0 (spec provenance carried over).

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS. 172 records migrated, validate exit 0, spec provenance 153. Files: scripts/all_features/migrate_from_log.ps1 (New). Fixed: array-splat re-parsed dash-leading values -> switched to hashtable splat; Unicode punctuation folded via code points. Dev log recorded.

---

### Step 02.2 - Retire the log file

**Files:** `dev/FUNCTIONALITY.log`
**Depends on:** Step 02.1

**Prompt for developer:**

> Replace the log header with a retirement banner: state the log is retired as of 2026-06-17, that the developer feature inventory now lives in `docs/ALL_FEATURES.jsonl`, and that chronology comes from git history + release diffs. Keep the historical body lines in place (do not delete them) so prior `Sxxxx` references remain greppable; only the header changes to mark retirement.

**Verification:**

- `Grep` - `retired` (case-insensitive) present in `dev/FUNCTIONALITY.log` header.
- `Grep` - `docs/ALL_FEATURES.jsonl` referenced in the log header.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS (retired x2, ALL_FEATURES.jsonl referenced). Files: dev/FUNCTIONALITY.log (header). Body kept read-only. Dev log recorded.

---

### Step 02.3 - Guard the old writer

**Files:** `scripts/add_to_functionality_log.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Make `add_to_functionality_log.ps1` fail fast with a deprecation message pointing callers to `scripts/all_features/add.ps1`, exiting non-zero without writing. Do not delete the script (other tooling/skills reference it until Phase 05 rewires them); the guard prevents new chronological entries from being appended.

**Verification:**

- Run: `pwsh -NoProfile -File scripts/add_to_functionality_log.ps1 -Op ADD -Description "x"` exits non-zero.
- `Grep` - `scripts/all_features/add.ps1` referenced in the deprecation message.
- `Grep` - no new line appended to `dev/FUNCTIONALITY.log` after the failed call (line count unchanged).

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS (exit 1, add.ps1 referenced, line count 316 unchanged). Files: scripts/add_to_functionality_log.ps1 (deprecation guard). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

ALL_FEATURES now holds a migrated baseline. Phase 03 must dedup new scan records against existing `id`s (upsert) and only add genuinely missing capabilities.

---

## Rollback Plan

Revert the migration commit (data file + scripts). The historical log body is untouched; restore its original header to un-retire.
