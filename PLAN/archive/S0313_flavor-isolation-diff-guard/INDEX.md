# Tactical Plan: S0313 - flavor-isolation-diff-guard

**Strategic spec:** [`../S0313_flavor-isolation-diff-guard.md`](../S0313_flavor-isolation-diff-guard.md)
**Feature:** Diff-aware static guard that blocks only new/touched main-source flavor checks; reports legacy debt non-blocking
**Tier:** 3 - Moderate, ad-hoc
**Priority:** 75
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-05-31

> **Scope:** tactical, English, developer handoff. Every step has a static verification predicate (file-exists / token / value equality). Rationale lives in the strategic spec. This tool only reads Kotlin to scan it; it adds no `.kt`.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations | - | ✅ Done | 4/4 | [PHASE_01__foundations.md](PHASE_01__foundations.md) |
| 02 | diff-classify-gate | 01 | ✅ Done | 5/5 | [PHASE_02__diff-classify-gate.md](PHASE_02__diff-classify-gate.md) |
| 03 | self-test-fixtures | 02 | ✅ Done | 4/4 | [PHASE_03__self-test-fixtures.md](PHASE_03__self-test-fixtures.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Phase 01 must not start while any box below is unchecked. Each maps to a strategic §6 research item with `Status: Open`.

- [x] **Research (strategic §6.1 - Flavor guard baseline):** Confirm the baseline strategy. Default chosen by this plan: **diff-only blocking** - the blocking scan reads only added lines in the changed `src/main` Kotlin set (git diff) or an explicit `-Path` list; legacy debt is surfaced by a separate opt-in non-blocking full-scan mode (`-LegacyAudit`). No generated baseline file and no allowlist-with-expiry are introduced, so there is no untracked permanent source of truth to rot (mitigates S0311 §6.2 risk). Owner must accept this default or name the alternative (generated baseline file / explicit allowlist with expiry) before Phase 01 begins.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not updated: strategic §8 records "Без изменений" (internal tooling).
- [ ] `dev/CHANGELOG.md` has an entry for every file written by this plan.
- [ ] `dev/CATALOG/<module>.jsonl` - not regenerated: no `.kt` added or changed by this plan.
- [ ] `/spec-check S0313` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/4 done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0313`.

---

## Blockers Log

- 2026-05-31 - Pre-impl: strategic §6.1 baseline strategy is `Status: Open`. Plan defaults to diff-only blocking with an opt-in `-LegacyAudit` full scan; owner confirmation required before Phase 01.
- 2026-05-31 - RESOLVED: owner accepted the default (diff-only blocking + opt-in `-LegacyAudit` full scan) under strategic §0 autonomy rule. Phase 01 unblocked.

---

## Change Log

- 2026-05-31 - Initial tactical plan authored by `/spec-tech`.
