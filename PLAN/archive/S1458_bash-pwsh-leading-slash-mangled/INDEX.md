# Tactical Plan: S1458 - bash-pwsh-leading-slash-mangled

**Strategic spec:** [`../S1458_bash-pwsh-leading-slash-mangled.md`](../S1458_bash-pwsh-leading-slash-mangled.md)
**Research inputs:** none - strategic §0 carries the measurements, and the one open item is discharged by step 01.1
**Feature:** Project guard refusing a slash-leading argument at the Bash to pwsh boundary
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** In Progress
**Phases:** 4 / 4 done
**Last updated:** 2026-08-09

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | guard-with-measured-perimeter | - | ✅ Done | 4/4 | [PHASE_01__guard-with-measured-perimeter.md](PHASE_01__guard-with-measured-perimeter.md) |
| 02 | both-sides-harness | 01 | ✅ Done | 3/3 | [PHASE_02__both-sides-harness.md](PHASE_02__both-sides-harness.md) |
| 03 | registration | 02 | ✅ Done | 1/1 | [PHASE_03__registration.md](PHASE_03__registration.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None blocking phase 01. Strategic §6.3 - the refusal perimeter - is still `Open`, and it is discharged by step 01.1, whose whole product is the measured list of forms. No step after 01.1 may run while 01.1 is unticked: every later step consumes that list.

Strategic §6.1 and §6.2 are Resolved and re-measured on 2026-08-09; their answers are in §0.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 says the change is agent infrastructure with no FEATURES effect.
- [x] `dev/CHANGELOG.md` has entry for every modified file - one row naming the whole set of five, per the repository's per-change granularity rule.
- [x] `dev/CATALOG/<module>.jsonl` regenerated if public API changed - not expected, no Kotlin is touched.
- [x] `/spec-check S1458` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1458`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-09 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-09 - Phase 04 executed by `/spec-do`: Rule 27 written into `CLAUDE.md` and `AGENTS.md`, cheatsheet regenerated (hook is outside the generator's scan roots by construction), closed through the facade at `post-change: PASS`.
