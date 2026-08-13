# Phase 04 - ViewStub targets and rules

**Strategic spec:** [`../S0365_lazy-initialization-audit.md`](../S0365_lazy-initialization-audit.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Produce the concrete overlay-migration shortlist and align shared lazy-optimization rules across docs, prompts, and agent profiles.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Phase 02 is ✅ Done.
- [ ] Phase 03 is ✅ Done.
- [ ] Strategic §6 research items are Resolved.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/S0365_lazy_init_audit_report.md` | Modified | ≤ 500 |
| `docs/ARCHITECTURE.md` | Modified | ≤ 320 |
| `CLAUDE.md` | Modified | ≤ 260 |
| `.github/copilot-instructions.md` | Modified | ≤ 220 |
| `.github/prompts/spec-tech.prompt.md` | Modified | ≤ 220 |
| `.github/prompts/spec-dev.prompt.md` | Modified | ≤ 220 |
| `.github/agents/android-kotlin-developer.agent.md` | Modified | ≤ 200 |

---

## Steps

### Step 04.1 - Expand the audit report with concrete player overlay candidates

**Files:** `temp/S0365_lazy_init_audit_report.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a dedicated section that enumerates the eager overlays in both `activity_player_unified.xml` variants and classifies each candidate as `Convert to ViewStub`, `Keep eager`, or `Needs binding-safe host first`. Include both portrait and landscape parity notes for every candidate group so a later XML phase can move safely without binding drift.

**Verification:**

- `Glob` - `temp/S0365_lazy_init_audit_report.md` exists.
- `Grep` - `## Player overlay candidates` present in the report.
- `Grep` - `Needs binding-safe host first` present in the report.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 3/3 PASS. Files: `temp/S0365_lazy_init_audit_report.md`. Dev log recorded where applicable.

---

### Step 04.2 - Align shared rules with the approved lazy contract

**Files:** `docs/ARCHITECTURE.md`, `CLAUDE.md`, `.github/copilot-instructions.md`, `.github/prompts/spec-tech.prompt.md`, `.github/prompts/spec-dev.prompt.md`, `.github/agents/android-kotlin-developer.agent.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Ensure the architecture doc, repo rules, copilot instructions, spec prompts, and android-kotlin-developer agent profile all express the same lazy-optimization contract: heavy DI via `dagger.Lazy<T>`, optional overlays via `ViewStub`, and immediate media release when inactive. Keep the wording concise and consistent across the touched files.

**Verification:**

- `Grep` - `Lazy optimization` present in `CLAUDE.md`.
- `Grep` - `LAZY_OPTIMIZATION` present in `.github/copilot-instructions.md`.
- `Grep` - `Lazy optimization` present in `.github/agents/android-kotlin-developer.agent.md`.
- `Grep` - `Lazy optimization` present in both `.github/prompts/spec-tech.prompt.md` and `.github/prompts/spec-dev.prompt.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 4/4 PASS. Files: docs/rule/prompts/agent set. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every non-temp file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] No user-facing feature inventory update required - infrastructure-only change.

---

## Handoff Notes to Next Phase

The overlay shortlist and shared-rule contract are frozen; the final phase can close validation, catalog sync, and status-handoff without reopening the architectural decision.

---

## Rollback Plan

Revert doc/rule edits in one batch if the wording proves inconsistent; the temp audit report can be regenerated.
