# Tactical Plan: S0490 - post-crash-report-prompt

**Strategic spec:** [`../S0490_post-crash-report-prompt.md`](../S0490_post-crash-report-prompt.md)
**Research inputs:** [`research/01__reuse-and-touchpoints.md`](research/01__reuse-and-touchpoints.md)
**Feature:** Предложение отправить отчёт после реального падения (продолжение S0483)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 55
**Status:** Not started
**Phases:** 5 / 5 done
**Last updated:** 2026-06-17

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | strings | - | ✅ Done | 1/1 | [PHASE_01__strings.md](PHASE_01__strings.md) |
| 02 | crash-file-accessor | - | ✅ Done | 1/1 | [PHASE_02__crash-file-accessor.md](PHASE_02__crash-file-accessor.md) |
| 03 | prompt-manager | 01, 02 | ✅ Done | 1/1 | [PHASE_03__prompt-manager.md](PHASE_03__prompt-manager.md) |
| 04 | mainactivity-wiring | 03 | ✅ Done | 1/1 | [PHASE_04__mainactivity-wiring.md](PHASE_04__mainactivity-wiring.md) |
| 05 | docs-catalog-cleanup | 01, 02, 03, 04 | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 item 2 is Resolved; item 1 is a non-blocking tactical choice (resolved direction: dedicated SharedPreferences watermark). S0483's send infrastructure (`buildCrashReportEmail`, `buildLogsZipUri`) is already merged in the working tree.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates a FEATURES sentence).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new manager + LoggingHelper API).
- [ ] `/spec-check S0490` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log; set journal status to the matching `Block*` state if the whole spec is blocked.
5. All done: flip `Status:` to `Done`, run `/spec-check S0490`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-17 - Initial tactical plan authored by `/spec-tech`.
