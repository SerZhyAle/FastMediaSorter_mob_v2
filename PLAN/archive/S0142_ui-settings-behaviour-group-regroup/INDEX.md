# Tactical Plan: S0142 — ui-settings-behaviour-group-regroup

**Strategic spec:** [`../S0142_ui-settings-behaviour-group-regroup.md`](../S0142_ui-settings-behaviour-group-regroup.md)
**Feature:** Перегруппировка пунктов в группе «Behaviour» настроек воспроизведения
**Tier:** 1 — Quick Win (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-05-10

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | strings | — | ✅ Done | 1/1 | [PHASE_01__strings.md](PHASE_01__strings.md) |
| 02 | layout-regroup | 01 | ✅ Done | 4/4 | [PHASE_02__layout-regroup.md](PHASE_02__layout-regroup.md) |
| 03 | saved-auth-help-handler | 02 | ✅ Done | 1/1 | [PHASE_03__saved-auth-help-handler.md](PHASE_03__saved-auth-help-handler.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No open research items in strategic §6 — no blockers. Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (minor UI note — see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (PlaybackSettingsFragment touched).
- [ ] `/spec-check S0142` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0142`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-05-10 — Initial tactical plan authored by `/spec-tech`.
