# Tactical Plan: S0659 - streams-settings-expansion

**Strategic spec:** [`../S0659_streams-settings-expansion.md`](../S0659_streams-settings-expansion.md)
**Research inputs:** [`research/01__streams-settings-architecture.md`](research/01__streams-settings-architecture.md)
**Feature:** Расширение группы настроек «Трансляции»
**Tier:** 3 - Moderate
**Priority:** 50
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-06-24

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-persistence | - | ✅ Done | 5/5 | [PHASE_01__settings-persistence.md](PHASE_01__settings-persistence.md) |
| 02 | session-remember-last | 01 | ✅ Done | 4/4 | [PHASE_02__session-remember-last.md](PHASE_02__session-remember-last.md) |
| 03 | clear-statuses-reset | 01 | ✅ Done | 4/4 | [PHASE_03__clear-statuses-reset.md](PHASE_03__clear-statuses-reset.md) |
| 04 | catalog-refresh-policy | 01, 02 | ✅ Done | 3/3 | [PHASE_04__catalog-refresh-policy.md](PHASE_04__catalog-refresh-policy.md) |
| 05 | settings-ui-rows | 01, 03 | ✅ Done | 4/4 | [PHASE_05__settings-ui-rows.md](PHASE_05__settings-ui-rows.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All strategic §6 research items are Resolved (owner quiz 2026-06-24): see strategic §6 items 1-5 and `research/01`.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - **NOT** updated per-spec. Strategic §8's "one sentence into FEATURES" is superseded by CLAUDE.md §11: FEATURES is `/skill-release`-owned; per-spec capability is recorded in `docs/ALL_FEATURES.jsonl` (Phase 06 / `/spec-dev` close).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file (via `add_to_dev_log.ps1`).
- [ ] Settings docs regenerated (Rule 22): `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`, `docs/settings/settings-annotations.json`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public classes added).
- [ ] `/spec-check S0659` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/6 done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log. If the whole spec is blocked, also set the journal status to the matching `Block*`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0659`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-24 - Initial tactical plan authored by `/spec-tech` (within `/spec-all`).
