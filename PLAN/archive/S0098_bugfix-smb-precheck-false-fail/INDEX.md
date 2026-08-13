# Tactical Plan: S0098 — bugfix-smb-precheck-false-fail

**Strategic spec:** [`../S0098_bugfix-smb-precheck-false-fail.md`](../S0098_bugfix-smb-precheck-false-fail.md)
**Feature:** SMB TCP precheck false-fail fix
**Tier:** 2
**Priority:** 65
**Status:** Done
**Phases:** 2 / 2 done
**Last updated:** 2026-05-06

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | fix-precheck-logic | — | ✅ Done | 3/3 | [PHASE_01__fix-precheck-logic.md](PHASE_01__fix-precheck-logic.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 3/3 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `dev/CHANGELOG.md` has entries for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0098` returns `Verified`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.

---

## Blockers Log

*(none)*

---

## Change Log

- 2026-05-06 — Initial tactical plan authored by `/spec-tech`.
