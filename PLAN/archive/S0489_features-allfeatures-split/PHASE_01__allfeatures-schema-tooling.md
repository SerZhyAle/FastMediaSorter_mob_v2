# Phase 01 - ALL_FEATURES Schema & Tooling

**Strategic spec:** [`../S0489_features-allfeatures-split.md`](../S0489_features-allfeatures-split.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 05, 06
**Steps done:** 4 / 4
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Establish `docs/ALL_FEATURES.jsonl` as the EN-only feature-inventory database: define its JSON Schema, seed empty data files (incl. gitignored noLegal variant), and provide writer + validator scripts under `scripts/all_features/`. No records populated yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.schema.json` | New | ≤ 120 |
| `docs/ALL_FEATURES.jsonl` | New | 0 (empty data) |
| `docs/ALL_FEATURES_noLegal.jsonl` | New (gitignored) | 0 (empty data) |
| `scripts/all_features/add.ps1` | New | ≤ 200 |
| `scripts/all_features/validate.ps1` | New | ≤ 180 |
| `.gitignore` | Modified | ≤ 5 |

---

## Steps

### Step 01.1 - Define the JSONL record schema

**Files:** `docs/ALL_FEATURES.schema.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Author a JSON Schema (draft-07) describing one ALL_FEATURES record (one JSONL line). Required fields: `id` (stable kebab `<area>.<feature>`), `area` (string), `name` (short EN title), `description` (EN, what it does), `flavors` (array, subset of `standard,lite,photos,legacy,vr,noLegal`). Optional: `spec` (`^S\d{4}$` or null - provenance), `status` (enum `active`/`removed`, default `active`). All text EN-only. `additionalProperties: false`. Add a top-of-file `$comment` stating the file is the developer inventory, EN-only, replaces `dev/FUNCTIONALITY.log`.

**Verification:**

- `Glob` - `docs/ALL_FEATURES.schema.json` exists.
- `Grep` - `"additionalProperties": false` present.
- `Grep` - `"flavors"` and `"id"` present in schema.
- Run: `pwsh -NoProfile -Command "Get-Content docs/ALL_FEATURES.schema.json -Raw | ConvertFrom-Json | Out-Null"` exits 0 (valid JSON).

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 4/4 PASS. Files: docs/ALL_FEATURES.schema.json (New). Dev log recorded.

---

### Step 01.2 - Seed empty data files and gitignore noLegal

**Files:** `docs/ALL_FEATURES.jsonl`, `docs/ALL_FEATURES_noLegal.jsonl`, `.gitignore`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create both data files empty (zero records). Add `docs/ALL_FEATURES_noLegal.jsonl` to `.gitignore` next to the existing `FEATURES_noLegal*` ignore rule (noLegal additions never tracked, mirror the FEATURES_noLegal policy). The public `docs/ALL_FEATURES.jsonl` stays tracked.

**Verification:**

- `Glob` - both `docs/ALL_FEATURES.jsonl` and `docs/ALL_FEATURES_noLegal.jsonl` exist.
- `Grep` - `ALL_FEATURES_noLegal` present in `.gitignore`.
- Run: `git check-ignore docs/ALL_FEATURES_noLegal.jsonl` prints the path (ignored).

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS. Files: docs/ALL_FEATURES.jsonl (New), docs/ALL_FEATURES_noLegal.jsonl (New, gitignored), .gitignore (Modified). Dev log recorded.

---

### Step 01.3 - Writer script (add/update record)

**Files:** `scripts/all_features/add.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Author `add.ps1` accepting `-Id -Area -Name -Description -Flavors -Spec` (and `-NoLegal` switch routing to the gitignored file). Upsert semantics: if a record with the same `id` exists, replace it; else append. Write UTF-8 no-BOM, one compact JSON object per line, stable key order. Validate the resulting line against `docs/ALL_FEATURES.schema.json` before write; reject on schema failure with a non-zero exit. Emulate the exit-code contract used by other repo scripts (`trap { exit 1 }` + explicit `exit 0`).

**Verification:**

- `Glob` - `scripts/all_features/add.ps1` exists.
- Run: `pwsh -NoProfile -File scripts/all_features/add.ps1 -Id "test.sample" -Area "Test" -Name "Sample" -Description "tmp" -Flavors "standard"` exits 0 and appends one line to `docs/ALL_FEATURES.jsonl`.
- Run again with same `-Id` and a changed `-Name`; `Grep -c '"id":"test.sample"'` returns `1` (upsert, not duplicate).
- Cleanup: remove the `test.sample` line; `Grep` for `test.sample` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 4/4 PASS (add, upsert count=1, cleanup zero). Files: scripts/all_features/add.ps1 (New). Fixed an EN-only regex that wrote control bytes; replaced with char-code scan. Dev log recorded.

---

### Step 01.4 - Validator script

**Files:** `scripts/all_features/validate.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Author `validate.ps1` that reads `docs/ALL_FEATURES.jsonl` (and `-NoLegal` for the gitignored file), parses each line as JSON, validates each against the schema (required fields, `flavors` enum membership, `id` uniqueness across the file, EN-only ASCII heuristic for `name`/`description`). Print a per-error report; exit non-zero on any violation, exit 0 when clean. Support a `-Gate` switch (quiet, exit-code-only) for later post-change wiring.

**Verification:**

- `Glob` - `scripts/all_features/validate.ps1` exists.
- Run: `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0 on the empty/clean file.
- `Grep` - `param(` and `-Gate` present in the script.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS (exit 0 on empty, param/-Gate present). Files: scripts/all_features/validate.ps1 (New). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`add.ps1` (upsert) and `validate.ps1` are the only sanctioned write/verify paths into ALL_FEATURES. Phases 02-03 populate exclusively through `add.ps1`; never hand-edit the JSONL.

---

## Rollback Plan

Delete `docs/ALL_FEATURES*.jsonl`, `docs/ALL_FEATURES.schema.json`, `scripts/all_features/`, revert the `.gitignore` line. No data migration, no user-facing surface.
