# Tactical Plan: S0299 - office-document-viewing-legal-routing

**Strategic spec:** [`../S0299_office-document-viewing-legal-routing.md`](../S0299_office-document-viewing-legal-routing.md)
**Feature:** Office document external handoff
**Tier:** 3 - Strategic, compliance-sensitive feature
**Priority:** 55
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-05-28

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|------------|--------|------:|------|
| 01 | classification | - | ✅ Done | 4/4 | [PHASE_01__classification.md](PHASE_01__classification.md) |
| 02 | external-handoff | 01 | ✅ Done | 4/4 | [PHASE_02__external-handoff.md](PHASE_02__external-handoff.md) |
| 03 | docs-catalog-cleanup | 02 | ✅ Done | 4/4 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** Android external open route - resolved in strategic §6.1.
- [x] **Research:** Native Android renderer scope - resolved in strategic §6.2.
- [x] **Research:** DOCX format legality - resolved in strategic §6.3.
- [x] **Research:** Owner scope - resolved in strategic §6.9.
- [x] **UI:** direct external handoff and missing-viewer fallback - delegated by owner in strategic §0 and resolved in §6.10.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated after Kotlin changes.
- [x] `/spec-check S0299` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add a Blockers Log entry.
5. All done: flip `Status:` to `Done`, run `/spec-check S0299`.

---

## Blockers Log

- None.

---

## Change Log

- 2026-05-28 - Initial tactical plan authored by `/spec-all`.
- 2026-05-28 - Phase 03 completed and `/spec-check S0299` verified by `/spec-all`.
