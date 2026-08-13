# Tactical Plan: S0183 — nolegal-apk-install

**Strategic spec:** [`../S0183_nolegal-apk-install.md`](../S0183_nolegal-apk-install.md)
**Feature:** APK Install from Browse (noLegal only)
**Tier:** 3
**Priority:** 50
**Status:** Implemented
**Phases:** 5 / 5 done
**Last updated:** 2026-05-14

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec and research doc (`PLAN/S0156_nolegal-capability-surface-audit/apk-install.md`).

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundation-di | — | ✅ Done | 4/4 | [PHASE_01__foundation-di.md](PHASE_01__foundation-di.md) |
| 02 | permission | 01 | ✅ Done | 1/1 | [PHASE_02__permission.md](PHASE_02__permission.md) |
| 03 | strings | 01 | ✅ Done | 1/1 | [PHASE_03__strings.md](PHASE_03__strings.md) |
| 04 | ui-integration | 01, 02, 03 | ✅ Done | 3/3 | [PHASE_04__ui-integration.md](PHASE_04__ui-integration.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All §6 research items resolved. Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES_noLegal.md` + `_RU.md` + `_UK.md` updated (Phase 05).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated after `.kt` changes.
- [ ] `/spec-check S0183` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0183`.

---

## Blockers Log

*(none)*

---

## Change Log

- **2026-05-13** — Initial tactical plan authored by `/spec-tech`.
