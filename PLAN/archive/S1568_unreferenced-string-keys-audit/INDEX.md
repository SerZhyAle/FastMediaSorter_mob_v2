# Tactical Plan: S1568 - unreferenced-string-keys-audit

**Strategic spec:** [`../S1568_unreferenced-string-keys-audit.md`](../S1568_unreferenced-string-keys-audit.md)
**Research inputs:** [`research/01__deadness-method-and-risk-subsets.md`](research/01__deadness-method-and-risk-subsets.md)
**Feature:** Unreferenced string key audit and cleanup
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-08-12

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | liveness-measurement | - | ✅ Done | 3/3 | [PHASE_01__liveness-measurement.md](PHASE_01__liveness-measurement.md) |
| 02 | removal-tool-parity | 01 | ✅ Done | 3/3 | [PHASE_02__removal-tool-parity.md](PHASE_02__removal-tool-parity.md) |
| 03 | dead-key-cleanup | 01, 02 | ✅ Done | 2/2 | [PHASE_03__dead-key-cleanup.md](PHASE_03__dead-key-cleanup.md) |
| 04 | unreferenced-strings-gate | 01, 03 | ✅ Done | 3/3 | [PHASE_04__unreferenced-strings-gate.md](PHASE_04__unreferenced-strings-gate.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Measured baseline (2026-08-11, re-run during planning)

Numbers the plan is sized against. Phase 01 replaces this hand-run with a committed command.

- `app_v2/src/main/res/values/strings.xml` declares 3234 names: 3227 `<string>`, 6 `<plurals>`, 1 `<string-array>`.
- 3892 source files under `app_v2/src` carry 4245 distinct resource names across `R.string.` / `R.plurals.` / `R.array.` / `@string/` / `@plurals/` / `@array/`.
- 397 declared names have zero references: 396 `<string>` plus the `<plurals>` name `sync_interval_hours`.
- The dead `<plurals>` is why Phase 02 exists as its own phase: `-Action remove` matches only `<string>` blocks, so today it cannot delete `sync_interval_hours` at all.

## Cross-ticket coordination

- **S1420** edits the same locale files concurrently (strategic §6.2). Every step that writes a strings file takes `CODE.LOCK` immediately before the write and releases it immediately after, per CLAUDE.md Rule 23. No step holds the lock across a build.
- **S1550** runs after this ticket (strategic §6.3). 75 of the 397 dead names belong to the layout-attribute-literal families S1550 covers; deleting them here shrinks that ticket rather than conflicting with it.
- **S1571** is `Verified`. `Get-KeyReferences` in `scripts/utils/set-android-string.ps1` already scans `<module>/src` across every source set and matches all three resource kinds, and `-Action remove` already refuses with exit 3 on a referenced key. Phase 02 reuses that behaviour instead of rebuilding it.

---

## Pre-Implementation Blockers

All three strategic §6 items are Resolved (owner sign-off 2026-08-11 via `/spec-quiz`). No blockers.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 states "Без изменений".
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed - not expected, this ticket touches no Kotlin.
- [ ] `/spec-check S1568` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1568`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-11 - Initial tactical plan authored by `/spec-tech`.
