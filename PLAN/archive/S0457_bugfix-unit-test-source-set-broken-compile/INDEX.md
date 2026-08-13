# Tactical Plan: S0457 - bugfix-unit-test-source-set-broken-compile

**Strategic spec:** [`../S0457_bugfix-unit-test-source-set-broken-compile.md`](../S0457_bugfix-unit-test-source-set-broken-compile.md)
**Research inputs:** none
**Feature:** Restore app_v2 unit-test source-set compilation; add a defaulted MediaCapabilities test factory
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 90
**Status:** Done
**Phases:** 2 / 2 done
**Last updated:** 2026-06-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | restore-test-compile | - | ✅ Done | 2/2 | [PHASE_01__restore-test-compile.md](PHASE_01__restore-test-compile.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 1/1 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 has no open items. Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` - no update (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0457` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0457`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-16 - Initial tactical plan authored by `/spec-tech`.
