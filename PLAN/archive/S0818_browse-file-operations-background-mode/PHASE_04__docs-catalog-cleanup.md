# Phase 04 - Docs Catalog Cleanup

**Strategic spec:** [`../S0818_browse-file-operations-background-mode.md`](../S0818_browse-file-operations-background-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-29
**Completed:** 2026-06-29

---

## Objective

Record the delivered capability, sync catalogs/logs, and leave the ticket ready for `/spec-check` and device verification.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] Browse background transfer behavior is compile-clean and functionally wired.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | ≤ 50 |
| `docs/FEATURES.md` | Modified | ≤ 50 |
| `docs/FEATURES_RU.md` | Modified | ≤ 50 |
| `docs/FEATURES_UK.md` | Modified | ≤ 50 |
| `PLAN/S0818_browse-file-operations-background-mode.md` | Modified | ≤ 80 |
| `PLAN/S0818_browse-file-operations-background-mode/INDEX.md` | Modified | ≤ 120 |

---

## Steps

### Step 04.1 - Add the shipped capability to the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one inventory entry describing that browse copy/move can continue in the background with progress notification and return-to-browse. Use the existing `File Operations` area vocabulary and keep the description user-facing.

**Verification:**

- `Grep` - `browse copy and move operations can be sent to the background` present in `docs/ALL_FEATURES.jsonl`.

**Status:** `[x] done`

---

### Step 04.2 - Sync public feature docs in EN/RU/UK

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add one concise bullet to the public features docs describing the new background browse transfer behavior. Keep the wording aligned across EN/RU/UK and avoid duplicating existing transfer bullets.

**Verification:**

- `Grep` - `background` and `browse` present on the new line in `docs/FEATURES.md`.
- `Grep` - `фон` present on the new line in `docs/FEATURES_RU.md`.
- `Grep` - `тло` or `фонов` present on the new line in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

---

### Step 04.3 - Close tactical bookkeeping for spec-check

**Files:** `PLAN/S0818_browse-file-operations-background-mode.md`, `PLAN/S0818_browse-file-operations-background-mode/INDEX.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Update the tactical counters, change log, and strategic header so the ticket is ready for `/spec-check`. If on-device verification is still pending, move the ticket to `BlockNeedUserTest`, add the required `S0818:` debug probe, and record the manual device-test note in the status header.

**Verification:**

- `Grep` - `Phases: 4 / 4 done` present in `PLAN/S0818_browse-file-operations-background-mode/INDEX.md`.
- `Grep` - `Status:` present and aligned with `BlockNeedUserTest` in `PLAN/S0818_browse-file-operations-background-mode.md`.
- `Grep` - `Timber.d("S0818:` present in the changed-flow entry point before the final build.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` PASS.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render).

---

## Handoff Notes to Next Phase

Final phase complete. Next step: on-device verification (`/spec-test-device S0818` or `/spec-sweep` with a connected device), then `/spec-check S0818`.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing data compatibility changes.
