# Tactical Plan: S1560 - launcher-profile-defaults

**Strategic spec:** [`../S1560_launcher-profile-defaults.md`](../S1560_launcher-profile-defaults.md)
**Research inputs:** [`research/02__third-party-package-visibility.md`](research/02__third-party-package-visibility.md) · [`research/06__existing-cells-inventory.md`](research/06__existing-cells-inventory.md)
**Feature:** Profile-aware launcher desktop starter set
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-08-11

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | launcher-host-actions | - | ✅ Done | 5/5 | [PHASE_01__launcher-host-actions.md](PHASE_01__launcher-host-actions.md) |
| 02 | sensor-gadgets | - | ✅ Done | 6/6 | [PHASE_02__sensor-gadgets.md](PHASE_02__sensor-gadgets.md) |
| 03 | installed-app-probe | - | ✅ Done | 4/4 | [PHASE_03__installed-app-probe.md](PHASE_03__installed-app-probe.md) |
| 04 | profile-starter-table | 01, 02, 03 | ✅ Done | 6/6 | [PHASE_04__profile-starter-table.md](PHASE_04__profile-starter-table.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All six strategic §6 items are Resolved - four by the owner on 2026-08-10 (§12), two by the research
artifacts linked above.

---

## Scope boundary - items named in §0 and deliberately not implemented

Strategic §11 criterion 3 forbids a silent omission. Every §0 item lands in a phase except these, each already
satisfied by shipped behaviour:

- **Clock, date and weekday** - `ClockGadget` already seeds for every profile and its date line already renders
  the weekday (skeleton `EEEdMMM`).
- **Android settings button** - `os:settings` is already in the common tail of every profile's set.
- **Local music** - the `res:<all-audio>:BROWSE` shortcut is already in every profile's common resource block;
  owner confirmed in §6.3 that no separate cell is wanted.
- **Voice recorder** - `fn:quick_voice` is already in the common feature block of every profile's set.
- **Internal radio on a car head unit** - the `streams` gadget is already in the `CAR_HEAD_UNIT` branch; only the
  external FM shortcut is new (Phase 04).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - update only if strategic §8 contains FEATURES sentence (not "Без изменений"); skip otherwise.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1560` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1560`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-11 - Initial tactical plan authored by `/spec-tech`.
