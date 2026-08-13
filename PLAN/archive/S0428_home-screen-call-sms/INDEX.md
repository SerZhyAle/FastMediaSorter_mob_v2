# Tactical Plan: S0428 - home-screen-call-sms

**Strategic spec:** [`../S0428_home-screen-call-sms.md`](../S0428_home-screen-call-sms.md)
**Research inputs:** none - strategic §8 carries the research results inline; the S0404 research entry it cites lives outside this folder.
**Feature:** Calling and SMS from the launcher desktop
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 40
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-08-06

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | sms-target | - | ✅ Done | 6/6 | [PHASE_01__sms-target.md](PHASE_01__sms-target.md) |
| 02 | picker-categories | 01 | ✅ Done | 5/5 | [PHASE_02__picker-categories.md](PHASE_02__picker-categories.md) |
| 03 | manual-number-entry | 02 | ✅ Done | 4/4 | [PHASE_03__manual-number-entry.md](PHASE_03__manual-number-entry.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §4 items 1-4 are all Resolved; the last two owner decisions were recorded by ui-clarify 2026-08-06 (four flat editor categories; manual number entry as a source row).

---

## Scope boundaries carried from the strategic spec

- Level 2 (`CALL_PHONE` / `SEND_SMS` / call-log / SMS reading, `ROLE_DIALER` / `ROLE_SMS`) is out of scope. No phase declares a restricted permission and no phase writes into `src/noLegal/`.
- `READ_CONTACTS` is already declared by S1335. No phase touches `AndroidManifest.xml` for it.
- No stored-cell migration: the editor category is not persisted, only `LauncherContactAction` is, and every existing action keeps its name.
- Cell presentation (monogram disc, caption, long press) belongs to S1176 and is not touched.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not updated here; the strategic spec has no FEATURES sentence and `/skill-release` owns that file.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - Phase 02 and Phase 03 add classes.
- [ ] `/spec-check S0428` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0428`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-06 - Initial tactical plan authored by `/spec-tech`.
