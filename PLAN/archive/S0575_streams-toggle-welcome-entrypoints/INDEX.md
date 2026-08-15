# Tactical Plan: S0575 - streams-toggle-welcome-entrypoints

**Strategic spec:** [`../S0575_streams-toggle-welcome-entrypoints.md`](../S0575_streams-toggle-welcome-entrypoints.md)
**Research inputs:** none (owner decisions captured in strategic §6 "Quiz decisions")
**Feature:** Streams master toggle, welcome onboarding entry, downloadable-extensions entry, main-menu gating
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Implemented - awaiting device test (BlockNeedUserTest)
**Phases:** 7 / 7 done
**Last updated:** 2026-06-21

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations | - | ✅ Done | 5/5 | [PHASE_01__foundations.md](PHASE_01__foundations.md) |
| 02 | profile-default | 01 | ✅ Done | 4/4 | [PHASE_02__profile-default.md](PHASE_02__profile-default.md) |
| 03 | extensions-catalog-item | 01 | ✅ Done | 5/5 | [PHASE_03__extensions-catalog-item.md](PHASE_03__extensions-catalog-item.md) |
| 04 | settings-streams-section | 01 | ✅ Done | 4/4 | [PHASE_04__settings-streams-section.md](PHASE_04__settings-streams-section.md) |
| 05 | welcome-entry | 01 | ✅ Done | 4/4 | [PHASE_05__welcome-entry.md](PHASE_05__welcome-entry.md) |
| 06 | main-menu-runtime-gate | 01 | ✅ Done | 1/1 | [PHASE_06__main-menu-runtime-gate.md](PHASE_06__main-menu-runtime-gate.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - every strategic §6 open question was resolved in the `/spec-quiz` pass (see strategic §6 "Quiz decisions"). Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - NOT updated here (strategic §8 defers the showcase sentence to `/skill-release`; only `docs/ALL_FEATURES.jsonl` is written, in Phase 07).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new classes: `StreamsSettingsStore`, `StreamsSettingsFragment`, `ExtensionItem.Catalog`).
- [ ] Settings docs regenerated and `scripts/quality/assert-settings-doc-sync.ps1` exits 0 (Rule 22; new `enable_streams` key).
- [ ] `/spec-check S0575` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0575`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-21 - Initial tactical plan authored by `/spec-tech`.
