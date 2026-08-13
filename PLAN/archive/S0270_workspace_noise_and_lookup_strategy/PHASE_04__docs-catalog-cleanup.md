# Phase 04 - Docs Catalog Cleanup

**Strategic spec:** [`../S0270_workspace_noise_and_lookup_strategy.md`](../S0270_workspace_noise_and_lookup_strategy.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Run the final consistency sweep across the three touched files and close the spec with clean evidence and logs.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Working tree is clean or on a feature branch.
- [x] No unresolved blocker remains in `INDEX.md`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.vscode/settings.json` | Modified | ≤ 220 |
| `CLAUDE.md` | Modified | ≤ 1400 |
| `dev/CATALOG/README.md` | Modified | ≤ 260 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 04.1 - Verify cross-file consistency of the exclude surface

**Files:** `.vscode/settings.json`, `CLAUDE.md`, `dev/CATALOG/README.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Compare the final heavy-directory exclude list and lookup split across all three touched files. Remove any drift so the editor config, repo rule, and catalogue guidance describe the same default search surface and the same exact-match exception.

**Verification:**

- `Grep` - `temp`, `DOWNLOADS`, `.venv`, `logs`, `.kotlin`, and `node_modules` all appear in the final rule surface across the touched files where applicable.
- `Grep` - `exact-match` appears in both `CLAUDE.md` and `dev/CATALOG/README.md`.
- `Grep` - `query.ps1` remains the semantic-query path in both `CLAUDE.md` and `dev/CATALOG/README.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 3/3 PASS. Files: .vscode/settings.json, CLAUDE.md, dev/CATALOG/README.md. Dev log recorded.

---

### Step 04.2 - Record closure evidence and spec hygiene

**Files:** `.vscode/settings.json`, `CLAUDE.md`, `dev/CATALOG/README.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run the required structural checks for this docs/config-only ticket, make sure every touched file has a dev-log entry, and leave the spec ready for `/spec-check S0270`. Do not add feature docs or functionality-log entries because the strategic spec marks the change as non-user-facing.

**Verification:**

- `Grep` - no new `TODO` or `TBD` markers were introduced in the touched files.
- `Grep` - `docs/FEATURES` was not modified by this ticket.
- `Grep` - `PLAN/S0270_workspace_noise_and_lookup_strategy.md` still states that no user-facing feature docs update is required.
- `Grep` - `dev/CHANGELOG.md` contains entries for `.vscode/settings.json`, `CLAUDE.md`, and `dev/CATALOG/README.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 4/4 PASS. Files: .vscode/settings.json, CLAUDE.md, dev/CATALOG/README.md. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `Grep` for `TODO|TBD` in the three touched files returns zero new hits attributable to this ticket.
- [x] `dev/CHANGELOG.md` has an entry for every touched file.
- [x] Final phase - see `INDEX.md` Completion Gate.

---

## Handoff Notes to Next Phase

Final phase - see `INDEX.md` Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
