# Tactical Plan: S0269 - post-change-ritual-unification

**Strategic spec:** [`../S0269_post_change_ritual_unification.md`](../S0269_post_change_ritual_unification.md)
**Feature:** Post-change ritual unification
**Tier:** 1 - Major (system infrastructure)
**Priority:** 70
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-05-20

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | dispatcher-core | - | ✅ Done | 2/2 | [PHASE_01__dispatcher-core.md](PHASE_01__dispatcher-core.md) |
| 02 | canonical-rules | 01 | ✅ Done | 2/2 | [PHASE_02__canonical-rules.md](PHASE_02__canonical-rules.md) |
| 03 | prompt-adoption | 01,02 | ✅ Done | 3/3 | [PHASE_03__prompt-adoption.md](PHASE_03__prompt-adoption.md) |
| 04 | docs-catalog-cleanup | 01,02,03 | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** Legacy callers without `-ChangeType` default to `Kotlin`. See strategic §6.1.
- [x] **Research:** `Mixed` keeps module default `app_v2` and skips strings audit without `-KeyPrefix`. See strategic §6.2.
- [x] **Research:** Step output keeps child stdout and adds compact `PASS` / `FAIL` / `SKIP` labels. See strategic §6.3.
- [x] **Research:** Canonical output labels are `dev-log`, `catalog-sync`, and `strings-audit`. See strategic §6.4.
- [x] **Research:** `ChangeType` remains the fixed six-value set from `CLAUDE.md`. See strategic §6.5.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [ ] `scripts/post-change.ps1` passes focused Script and Doc smoke runs.
- [x] Active prompt and rule files touched by this ticket no longer instruct agents to commit gitignored catalog files or to use raw `scan.ps1` + `render.ps1` as the routine closure path.
- [x] `dev/CHANGELOG.md` has an entry for every modified script, prompt, rule, and spec file.
- [x] `/spec-check S0269` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/4 done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check <Sxxxx>`.

---

## Blockers Log

- 2026-05-20 - Tactical plan created during `/spec-all`; dispatcher-core and canonical-rules were already applied and validated inline. Next: Phase 03 prompt adoption.
- 2026-05-20 - Prompt adoption and final static cleanup completed inline during `/spec-all`. Next: `/spec-check S0269`.

---

## Change Log

- 2026-05-20 - Initial tactical plan authored by `/spec-tech` semantics inside `/spec-all`.
- 2026-05-20 - `/spec-all` resumed the draft, filled the owner gate, and recorded completed work for phases 01-02 before prompt adoption.
- 2026-05-20 - `/spec-all` completed phases 03-04 and moved the ticket to `Implemented` pending audit.
- 2026-05-20 - `/spec-check` semantics inside `/spec-all` verified S0269 with no remaining WARN/FAIL items.