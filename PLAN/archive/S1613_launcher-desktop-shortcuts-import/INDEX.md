# Tactical Plan: S1613 - launcher-desktop-shortcuts-import

**Strategic spec:** [`../S1613_launcher-desktop-shortcuts-import.md`](../S1613_launcher-desktop-shortcuts-import.md)
**Research inputs:** none
**Feature:** Restore pinned desktop shortcuts when the launcher desktop is seeded
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 4 / 4 done
**Last updated:** 2026-08-13

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | import-source | - | ✅ Done | 2/2 | [PHASE_01__import-source.md](PHASE_01__import-source.md) |
| 02 | seed-merge | 01 | ✅ Done | 3/3 | [PHASE_02__seed-merge.md](PHASE_02__seed-merge.md) |
| 03 | packer-tests | 02 | ✅ Done | 2/2 | [PHASE_03__packer-tests.md](PHASE_03__packer-tests.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Every strategic §6 item is Resolved; §6.1 was reclassified as a device observation folded into the §3.3 validation checklist, because the design is identical under either answer.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: owned by `/skill-release`, never edited per-spec.
- [ ] `dev/CHANGELOG.md` has entry for the change.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - `AppShortcutDataSource` gains a public method.
- [ ] `/spec-check S1613` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1613`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-13 - Initial tactical plan authored by `/spec-tech`.
