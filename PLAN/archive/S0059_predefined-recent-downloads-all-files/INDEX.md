# Tactical Plan: S0059 — predefined-recent-downloads-all-files

**Strategic spec:** [`../S0059_predefined-recent-downloads-all-files.md`](../S0059_predefined-recent-downloads-all-files.md)
**Feature:** All-files default for predefined "Recent" and "Downloads"
**Tier:** 2 — Easy (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 0 / 4 done
**Last updated:** 2026-05-03

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | predefined-classifier | — | ⬜ Not started | 0/3 | [PHASE_01__predefined-classifier.md](PHASE_01__predefined-classifier.md) |
| 02 | creation-defaults | 01 | ⬜ Not started | 0/4 | [PHASE_02__creation-defaults.md](PHASE_02__creation-defaults.md) |
| 03 | one-time-migration | 01 | ⬜ Not started | 0/4 | [PHASE_03__one-time-migration.md](PHASE_03__one-time-migration.md) |
| 04 | docs-catalog-cleanup | 02, 03 | ⬜ Not started | 0/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 holds four open research items. Resolve each (mark `Resolved` in the strategic file, optionally amend the relevant phase) before flipping Phase 01 to In Progress.

- [ ] **Research:** §6.1 — Recreate "Recent" if user deleted it? Default assumption baked into Phase 03: **only update existing rows; do not recreate.** Confirm or amend.
- [ ] **Research:** §6.2 — Renamed "Downloads" — does migration still apply? Default assumption baked into Phase 03: **opt (a), match strictly by canonical Downloads path regardless of name.** Confirm or amend.
- [ ] **Research:** §6.3 — Multiple LOCAL rows pointing at the canonical Downloads path. Default assumption baked into Phase 03: **opt (a), update all matching rows.** Confirm or amend.
- [ ] **Research:** §6.4 — Visible user notification on migration. Default assumption baked into Phase 03: **opt (a), silent migration with one Timber `i`-line per touched row.** Confirm or amend.

If any answer changes the default, edit the corresponding step body in `PHASE_03__one-time-migration.md` before starting.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (one bullet per locale per strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (Phase 01 introduces a new utility class).
- [ ] `/spec-check S0059` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0059`.

---

## Blockers Log

- 2026-05-03 — Strategic §6 has four open research items (see Pre-Implementation Blockers above). Phase 01 may proceed once defaults are confirmed; Phase 03 step bodies must be reconciled with any non-default answer.

---

## Change Log

- 2026-05-03 — Initial tactical plan authored by `/spec-tech`.
