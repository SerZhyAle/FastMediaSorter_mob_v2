# Tactical Plan: S0337 - system-info-extended-fields

**Strategic spec:** [`../S0337_system-info-extended-fields.md`](../S0337_system-info-extended-fields.md)
**Feature:** Расширенные данные и бенчмарки в «Сведениях о системе»
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-06-03

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundation-sections | - | ✅ Done | 3/3 | [PHASE_01__foundation-sections.md](PHASE_01__foundation-sections.md) |
| 02 | hardware-system-personal | 01 | ✅ Done | 3/3 | [PHASE_02__hardware-system-personal.md](PHASE_02__hardware-system-personal.md) |
| 03 | battery-display | 01 | ✅ Done | 3/3 | [PHASE_03__battery-display.md](PHASE_03__battery-display.md) |
| 04 | network | 01 | ✅ Done | 2/2 | [PHASE_04__network.md](PHASE_04__network.md) |
| 05 | benchmarks | 01 | ✅ Done | 3/3 | [PHASE_05__benchmarks.md](PHASE_05__benchmarks.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6.1–§6.6 are resolved (owner decisions 2026-06-03). §6.7 (benchmark budget/methodology) stays Open but is explicitly non-blocking: implement with conservative defaults (small buffer/file, hard time cap, temp file deleted), tune thresholds on device. This does not gate phasing.

Resolved owner decisions baked into the plan:

- Benchmarks run automatically on dialog open (off main thread).
- Section and field labels are localized EN/RU/UK.
- Personal-but-permission-free fields (device name, user name, install source, IME id) are included, also in copied/shared text.
- Local IP shown for active interface(s), included in shared text.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates a copy refresh).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public types).
- [ ] `/spec-check S0337` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status accordingly.
5. All done: flip `Status:` to `Done`, run `/spec-check S0337`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-03 - Initial tactical plan authored by `/spec-tech`.
