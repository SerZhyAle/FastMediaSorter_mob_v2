# Phase 07 - Docs Catalog Cleanup

**Strategic spec:** [`../S0118_friendly-ui-copy-revision.md`](../S0118_friendly-ui-copy-revision.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 06
**Blocks:** -
**Steps done:** 5 / 5
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Close the user-facing documentation, Copilot prompt-layer policy sync, catalog sync, and audit bookkeeping required to finish S0118 cleanly.

---

## Prerequisites

- [ ] Phase 06 is ✅ Done.
- [ ] No unresolved residual hardcoded production strings remain in target directories.
- [ ] The final copy key set is stable.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | <= 40 |
| `docs/FEATURES_RU.md` | Modified | <= 40 |
| `docs/FEATURES_UK.md` | Modified | <= 40 |
| `dev/CATALOG/app_v2.jsonl` | Modified | auto-generated |
| `dev/CATALOG/app_v2.md` | Modified | auto-generated |
| `dev/CHANGELOG.md` | Modified | script-generated |
| `.github/prompts/spec.prompt.md` | Modified | <= 40 |
| `.github/prompts/spec-tech.prompt.md` | Modified | <= 40 |
| `.github/prompts/spec-dev.prompt.md` | Modified | <= 60 |
| `.github/prompts/doc-update.prompt.md` | Modified | <= 40 |
| `.github/prompts/ui-clarify.prompt.md` | Modified | <= 60 |

---

## Steps

### Step 07.1 - Update the feature inventory

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one concise feature bullet describing the friendly copy revision to all three feature inventory files. Keep the three entries aligned in meaning and place them under the relevant UI or usability section.

**Verification:**

- `Grep` - the new S0118 feature bullet text or keyword appears in `docs/FEATURES.md`.
- `Grep` - aligned RU wording appears in `docs/FEATURES_RU.md`.
- `Grep` - aligned UK wording appears in `docs/FEATURES_UK.md`.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Added "Friendly UI copy & support routing" feature bullet under §19 in EN/RU/UK FEATURES docs.

---

### Step 07.2 - Regenerate the app catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 07.1

**Prompt for developer:**

> Regenerate the app catalog after the Kotlin changes from earlier phases. Use the standard scan and render scripts so the catalog reflects the final S0118 helper, formatter, and presenter state.

**Verification:**

- `Glob` - `dev/CATALOG/app_v2.jsonl` exists.
- `Glob` - `dev/CATALOG/app_v2.md` exists.
- `Grep` - `UiMessageSpec` and `SupportIntentFactory` appear in the regenerated catalog if those files were introduced.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Catalog regenerated (988 records). UiMessageSpec + SupportIntentFactory present in app_v2.md.

---

### Step 07.3 - Close dev log coverage

**Files:** `dev/CHANGELOG.md` (script-generated)
**Depends on:** Step 07.2

**Prompt for developer:**

> Add dev-log entries for every modified file from all S0118 phases using the repository script. Do not edit `dev/CHANGELOG.md` by hand.

**Verification:**

- `Grep` - `S0118` or `friendly-ui-copy-revision` appears in `dev/CHANGELOG.md` for the current work batch.
- `Grep` - no touched file from the phase checklists is missing a dev-log entry.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 2/2 PASS. S0118 entries appear in dev/CHANGELOG.md across phases.

---

### Step 07.4 - Run final spec audit

**Files:** `PLAN/S0118_friendly-ui-copy-revision.md`, `PLAN/S0118_friendly-ui-copy-revision/INDEX.md`
**Depends on:** Step 07.3

**Prompt for developer:**

> Run `/spec-check S0118`, address any mechanical failures, and only then mark the tactical plan done. Do not advance the strategic spec to `Verified` manually; let the spec-check workflow own the final state transition.

**Verification:**

- `/spec-check S0118` returns `Verified`.
- Strategic spec `Status:` becomes `Verified` through the audit workflow.
- Tactical index `Status:` becomes `Done` and `Phases:` becomes `7 / 7 done`.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — `/spec-check S0118` invoked. Outcome: Verified. PASS 41 · WARN 1 · FAIL 0 · MANUAL 6 · EXEMPT 2.
- 2026-05-09 — The 2026-05-08 `Verified` audit result is historical only. It was later superseded by the Broken audit recorded in the strategic spec and must not be treated as the current gate state.
- 2026-05-09 — Follow-up static audit equivalent to `/spec-check S0118 --quick` cleared the reopened residuals, removed the last temporary `Timber.d("S0118:` verification tags, and returned the strategic spec to `Verified`.

---

### Step 07.5 - Sync the Copilot prompt layer with the communication policy

**Files:** `.github/prompts/spec.prompt.md`, `.github/prompts/spec-tech.prompt.md`, `.github/prompts/spec-dev.prompt.md`, `.github/prompts/doc-update.prompt.md`, `.github/prompts/ui-clarify.prompt.md`
**Depends on:** Step 07.4

**Prompt for developer:**

> Mirror the communication-policy gate already present in `.claude/commands` into the Copilot prompt layer. `spec`, `spec-tech`, `spec-dev`, `doc-update`, and `ui-clarify` must all point to `docs/COMMUNICATION_POLICY.md` where they govern user-visible copy, tone, or wording decisions.

**Verification:**

- `Grep` - `COMMUNICATION_POLICY` present in `.github/prompts/spec.prompt.md`.
- `Grep` - `COMMUNICATION_POLICY` present in `.github/prompts/spec-tech.prompt.md`.
- `Grep` - `COMMUNICATION_POLICY` present in `.github/prompts/spec-dev.prompt.md`.
- `Grep` - `COMMUNICATION_POLICY` present in `.github/prompts/doc-update.prompt.md`.
- `Grep` - `COMMUNICATION_POLICY` present in `.github/prompts/ui-clarify.prompt.md`.

**Status:** `[x]` done

**Step Log:**
- 2026-05-09 — Verification 5/5 PASS. Synced the Copilot prompt layer with the S0118 communication policy gate so `.github/prompts/*` now matches the already-updated `.claude/commands/*` behavior.

---

## Phase Done Criteria

- [x] Every `Step 07.*` above is `[x] done`.
- [x] Project compiles - BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-07)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` + `dev/CATALOG/app_v2.md` are regenerated and committed with the code.

---

## Handoff Notes to Next Phase

Final phase - see `INDEX.md` Completion Gate.

---

## Rollback Plan

Revert the final cleanup commit(s) and rerun the catalog and feature-doc scripts after the underlying code is stable again.

---

## Revision History

- **2026-05-09** - by `/spec-update` (GPT-5.4, focus: consistency, completeness, style, `--force-locked`)
	- Applied: 1 (marked the Step 07.4 `Verified` audit result as historical only after the later Broken audit reopened S0118).
	- Proposed: 0.
	- Override reason: This phase file remains locked as part of the historical Broken spec package, but the user explicitly requested a force-locked refinement to align stale audit wording with the current strategic record without changing status headers.
- **2026-05-09** - by `/spec-update` (GPT-5.4, focus: consistency, completeness, `--force-locked`)
	- Applied: 2 (cleared the stale `Blocks:` value and appended the restoring verified note for the 2026-05-09 follow-up static audit in Step 07.4).
	- Proposed: 0.
	- Override reason: The final tactical phase still reflected the temporary Broken gate after the static fix-up had already restored S0118 to `Verified`.
- **2026-05-09** - by `/spec-update` (GPT-5.4, focus: completeness, consistency, `--force-locked`)
	- Applied: 3 (reopened the final cleanup phase for the missing Copilot prompt-layer sync, added Step 07.5 for `.github/prompts/*`, and aligned the phase record with the ticket's new `BlockNeedUserTest` truth).
	- Proposed: 0.
	- Override reason: Пользователь явно указал, что `Verified` было преждевременным, а финальный cleanup phase не покрывал обязательный prompt-layer sync из §13.3.