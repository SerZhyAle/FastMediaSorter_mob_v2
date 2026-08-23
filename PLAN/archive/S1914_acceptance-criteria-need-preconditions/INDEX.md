# Tactical Plan: S1914 - acceptance-criteria-need-preconditions

**Strategic spec:** [`../S1914_acceptance-criteria-need-preconditions.md`](../S1914_acceptance-criteria-need-preconditions.md)
**Research inputs:** none - strategic §4 carries the three measurements this plan is built on.
**Feature:** Spec authoring and device-run vocabulary
**Tier:** 2 - Significant (ad-hoc)
**Priority:** 60
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-08-21

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | unobserved-outcome | - | ✅ Done | 2/2 | [PHASE_01__unobserved-outcome.md](PHASE_01__unobserved-outcome.md) |
| 02 | precondition-gate | 01 | ✅ Done | 3/3 | [PHASE_02__precondition-gate.md](PHASE_02__precondition-gate.md) |
| 03 | authoring-rule | 02 | ✅ Done | 2/2 | [PHASE_03__authoring-rule.md](PHASE_03__authoring-rule.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Design decisions this plan fixes

Recorded here so `/spec-dev` does not re-open them.

- **The outcome word is `UNOBSERVED`.** It answers "the check ran and its precondition was absent", which strategic §6.3 establishes no existing value answers. It is an addition; `PASS`/`FAIL`/`INCONCLUSIVE`/`SKIPPED` keep their meanings (§3.2).
- **`UNOBSERVED` never closes a criterion.** It joins `SKIPPED`/`INCONCLUSIVE` on the "leave the checklist line unchanged" branch of `/spec-test-device` (line ~216), which is what makes an unmet precondition survive the run instead of dissolving into PASS.
- **The gate is narrow by construction.** It fires only on §11 criteria about state accumulated OUTSIDE the test session - migration, import, upgrade, merge - per strategic §6.4. A restart/rotation criterion is procedurally safe and is not in scope.
- **The gate ships with a baseline.** Strategic §7 row 1: without it the check rejects the existing corpus on introduction.

---

## Pre-Implementation Blockers

None. All four items in strategic §6 are `Resolved`.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES*.md` - skipped: no user-visible capability; this is process tooling.
- [x] `dev/CHANGELOG.md` entry written by `post-change.ps1`.
- [x] The new gate runs over the whole `PLAN/**` corpus and exits 0 with its baseline.
- [x] Proven both ways: the real S1832 criterion is caught, a fixture naming its precondition passes, and all three documented exit codes (0/1/2) were each observed.
- [x] No app code touched, so `/spec-check` may take this to `Verified` - there is no device step to wait for.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`.
2. During phase: flip step to `[x] done` only when its Verification passes.
3. On completion: confirm Phase Done Criteria, flip row to `✅ Done`, bump the counter.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-21 - Initial tactical plan authored by `/spec-tech` under `/spec-code`.
