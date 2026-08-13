# Phase 05 - Docs + catalog cleanup

**Strategic spec:** [`../S0490_post-crash-report-prompt.md`](../S0490_post-crash-report-prompt.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Document the new user-facing capability trilingually, record it in the functionality log, and regenerate the class catalog for the new manager + the LoggingHelper API.

---

## Prerequisites

- [ ] Phase 01-04 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | +1 sentence |
| `docs/FEATURES_RU.md` | Modified | +1 sentence |
| `docs/FEATURES_UK.md` | Modified | +1 sentence |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |

---

## Steps

### Step 05.1 - Document the feature trilingually

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one sentence to each FEATURES file (the same Settings & Navigation / diagnostics area as the S0483 crash-report bullet): after the app closes unexpectedly, the next launch offers to email the crash report with the app log to the author. Keep tone aligned with `docs/COMMUNICATION_POLICY.md`. Do not duplicate the S0483 bullet - this is the post-crash (next-launch) case.

**Verification:**

- `Grep` - a post-crash-prompt sentence present in `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification PASS (post-crash bullet present in FEATURES EN/RU/UK §16, distinct from the S0483 bullet; Cyrillic intact).

---

### Step 05.2 - Record in the functionality log

**Files:** (journal write - `dev/FUNCTIONALITY.log`)
**Depends on:** Step 05.1

**Prompt for developer:**

> Record via `add_to_functionality_log.ps1` with change type `ADD` describing the post-crash report prompt. Run it standalone/last; it is known to leave a non-zero `$LASTEXITCODE` on success, so re-verify the appended line rather than trusting the exit code.

**Verification:**

- `Grep` - the new `ADD` entry for the post-crash prompt is present in `dev/FUNCTIONALITY.log`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification PASS (ADD entry in dev/FUNCTIONALITY.log line 313 via close-and-log -FuncOp ADD).

---

### Step 05.3 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Regenerate the catalog for the new `CrashReportPromptManager` and `LoggingHelper.getLatestCrashFile`: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Gitignored local index - regenerate, no commit expected. Fill role/status for the new class via `set.ps1`.

**Verification:**

- Command exits 0.
- `Grep` - `CrashReportPromptManager` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification PASS (catalog regenerated; CrashReportPromptManager present; role/status=new set via set.ps1).

---

## Phase Done Criteria

- [ ] Every `Step 05.*` is `[x] done`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for the three FEATURES files.
- [ ] All five phases show ✅ Done in `INDEX.md`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. `/spec-dev` flips to `BlockNeedUserTest` (the S0490 probe tag is already in the manager); device test: force a crash, relaunch, confirm the prompt and the email with attachment.

---

## Rollback Plan

Revert the three FEATURES edits. Catalog and functionality-log regeneration are non-destructive local artifacts.
