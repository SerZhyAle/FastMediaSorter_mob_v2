# Phase 04 - Docs + catalog cleanup

**Strategic spec:** [`../S0483_crash-report-email-button.md`](../S0483_crash-report-email-button.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Document the new user-facing capability trilingually, record it in the functionality log, and regenerate the class catalog for the changed public APIs.

---

## Prerequisites

- [ ] Phase 01, Phase 02, Phase 03 are ✅ Done.

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

### Step 04.1 - Document the feature trilingually

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one sentence to each of the three FEATURES files describing the new capability: when a real error (crash) is shown in the error dialog, a button lets the user email the crash report - with the app log attached - to the author. Place it in the error-handling / diagnostics section consistent with the existing structure. Keep wording aligned with `docs/COMMUNICATION_POLICY.md` tone. Do not duplicate an existing entry.

**Verification:**

- `Grep` - a crash-report sentence present in `docs/FEATURES.md`.
- `Grep` - the matching sentence present in `docs/FEATURES_RU.md`.
- `Grep` - the matching sentence present in `docs/FEATURES_UK.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification PASS (crash-report bullet present in FEATURES EN/RU/UK §16; Cyrillic intact). Files: FEATURES.md, FEATURES_RU.md, FEATURES_UK.md.

---

### Step 04.2 - Record in the functionality log

**Files:** (journal write - `dev/FUNCTIONALITY.log`)
**Depends on:** Step 04.1

**Prompt for developer:**

> Record the user-visible capability via `pwsh -NoProfile -File scripts/add_to_functionality_log.ps1` with change type `ADD` describing the crash-report email button in the error dialog. Run this command standalone/last - the script is known to leave a non-zero `$LASTEXITCODE` even on success; re-verify the journal line was appended rather than trusting the exit code.

**Verification:**

- `Grep` - the new `ADD` entry referencing the crash-report button is present in `dev/FUNCTIONALITY.log`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification PASS (ADD entry in dev/FUNCTIONALITY.log line 312, via close-and-log -FuncOp ADD).

---

### Step 04.3 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Regenerate the local class catalog for the changed public APIs (`ScrollableTextDialog.show` signature, `SupportIntentFactory.buildCrashReportEmail`, `LogExportHelper.buildLogsZipUri`): `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. These indexes are gitignored - regenerate, do not expect a commit.

**Verification:**

- Command exits 0.
- `Grep` - `buildCrashReportEmail` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification PASS (catalog regenerated via close-and-log; buildCrashReportEmail present in app_v2.jsonl).

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for the three FEATURES files via `.\scripts\add_to_dev_log.ps1`.
- [ ] All four phases show ✅ Done in `INDEX.md`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-dev` advances the spec to `BlockNeedUserTest` (inserting the `Timber.d("S0483: ..")` device-test probe at the report-button flow entry), then on-device verification confirms the mail client opens with the attachment.

---

## Rollback Plan

Revert the three FEATURES edits. Catalog and functionality-log regeneration are non-destructive local artifacts.
