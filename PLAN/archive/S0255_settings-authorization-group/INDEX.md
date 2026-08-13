# Tactical Plan: S0255 — settings-authorization-group

**Strategic spec:** [`../S0255_settings-authorization-group.md`](../S0255_settings-authorization-group.md)
**Feature:** Authorization collapsible group in General Settings
**Tier:** 2 — Easy (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 5 / 5 done
**Last updated:** 2026-05-19 23:33

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | strings | - | ✅ Done | 2/2 | [PHASE_01__strings.md](PHASE_01__strings.md) |
| 02 | empty-group-with-toggle | 01 | ✅ Done | 4/4 | [PHASE_02__empty-group-with-toggle.md](PHASE_02__empty-group-with-toggle.md) |
| 03 | move-gsm-and-google-card | 02 | ✅ Done | 4/4 | [PHASE_03__move-gsm-and-google-card.md](PHASE_03__move-gsm-and-google-card.md) |
| 04 | move-saved-auth-row | 03 | ✅ Done | 6/6 | [PHASE_04__move-saved-auth-row.md](PHASE_04__move-saved-auth-row.md) |
| 05 | docs-catalog-cleanup | 04 | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All strategic §6 research items resolved by `/ui-clarify` on 2026-05-19.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — strategic §8 = "Без изменений", so skip.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated (helper class signature changed).
- [ ] `/spec-check S0255` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, set the journal status accordingly.
5. All done: flip `Status:` to `Done`, run `/spec-check S0255`.

---

## Blockers Log

- (empty)

---

## Change Log

- 2026-05-19 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-19 — Static implementation audit: Phases 02..05 updated, build/manual gates intentionally left open (`no build`).
- 2026-05-19 23:33 — Real implementation pass: the prior audit had marked steps as done but the codebase did not yet contain the Authorization group (recent refactor `85b122bf` had also removed the saved-authorizations row entirely). Implementation reinstated the row and built it into the new collapsible group. `assembleStandardDebug` PASS. Spec now waits for on-device verification under `BlockNeedUserTest`.
