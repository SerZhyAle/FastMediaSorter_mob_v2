# Tactical Plan: S0406 - unified-settings-backup

**Strategic spec:** [`../S0406_unified-settings-backup.md`](../S0406_unified-settings-backup.md)
**Research inputs:** [`research/01__cloud-oauth-token-portability.md`](research/01__cloud-oauth-token-portability.md), [`research/02__web-auth-cookie-validity.md`](research/02__web-auth-cookie-validity.md), [`research/03__merge-strategy.md`](research/03__merge-strategy.md), [`research/04__local-file-format-and-name.md`](research/04__local-file-format-and-name.md)
**Feature:** Единый механизм резервного копирования настроек
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Implemented (BlockNeedUserTest)
**Phases:** 7 / 7 done
**Last updated:** 2026-06-11

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | payload-schema | - | ✅ Done | 3/3 | [PHASE_01__payload-schema.md](PHASE_01__payload-schema.md) |
| 02 | mapper-and-session-export | 01 | ✅ Done | 3/3 | [PHASE_02__mapper-and-session-export.md](PHASE_02__mapper-and-session-export.md) |
| 03 | unified-build-apply | 02 | ✅ Done | 2/2 | [PHASE_03__unified-build-apply.md](PHASE_03__unified-build-apply.md) |
| 04 | local-file-rewire | 03 | ✅ Done | 2/2 | [PHASE_04__local-file-rewire.md](PHASE_04__local-file-rewire.md) |
| 05 | drive-rewire | 03 | ✅ Done | 2/2 | [PHASE_05__drive-rewire.md](PHASE_05__drive-rewire.md) |
| 06 | round-trip-tests | 04, 05 | ✅ Done | 1/1 | [PHASE_06__round-trip-tests.md](PHASE_06__round-trip-tests.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - all strategic §6 research items are Resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates a FEATURES sentence).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API changed).
- [ ] `/spec-check S0406` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0406`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-11 - Initial tactical plan authored by `/spec-tech`.
