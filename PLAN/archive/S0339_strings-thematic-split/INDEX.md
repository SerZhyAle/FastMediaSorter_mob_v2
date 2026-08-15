# Tactical Plan: S0339 - strings-thematic-split

**Strategic spec:** [`../S0339_strings-thematic-split.md`](../S0339_strings-thematic-split.md)
**Feature:** Thematic split of strings.xml by feature area
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-06-03

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | move-tooling | - | ✅ Done | 3/3 | [PHASE_01__move-tooling.md](PHASE_01__move-tooling.md) |
| 02 | taxonomy-baseline | 01 | ✅ Done | 3/3 | [PHASE_02__taxonomy-baseline.md](PHASE_02__taxonomy-baseline.md) |
| 03 | execute-migration | 02 | ✅ Done | 2/2 | [PHASE_03__execute-migration.md](PHASE_03__execute-migration.md) |
| 04 | validation | 03 | ✅ Done | 3/3 | [PHASE_04__validation.md](PHASE_04__validation.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Both strategic §6 research items are `Resolved` (2026-06-03, research-backed taxonomy + attribution rule).

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped (strategic §8: "Без изменений").
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] Per-locale union of `<string name>` keys across all `strings*.xml` is identical before and after migration (EN 3473 / RU 3456 / UK 3434; diff 0/0/0; 0 duplicates).
- [ ] `/spec-check S0339` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

### Result summary

- 15 new thematic files created (+ `strings_vr.xml` extended), residual `strings.xml` shrank 3405 → 1980 (≈42%).
- 8 pre-existing parity-drift keys (dropbox/import/file/translation groups) correctly left in residual - not made worse.
- `standardDebug` build SUCCESSFUL (aapt2 merge clean, all `R.string.*` resolve).
- New tooling: `set-android-string.ps1` `move` (single + `-Prefix` bulk, atomic) and `audit` (union oracle).

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0339`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-03 - Initial tactical plan authored by `/spec-tech`.
