# Tactical Plan: S0204 — bugfix-toolbar-hidden-behind-statusbar

**Strategic spec:** [`../S0204_bugfix-toolbar-hidden-behind-statusbar.md`](../S0204_bugfix-toolbar-hidden-behind-statusbar.md)
**Feature:** Status bar height safety fallback for OEM Android 8.x devices
**Tier:** 2 — Easy
**Priority:** 90
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-05-14

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | statusbar-height-utility | — | ✅ Done | 1/1 | [PHASE_01__statusbar-height-utility.md](PHASE_01__statusbar-height-utility.md) |
| 02 | settings-activity-insets | 01 | ✅ Done | 2/2 | [PHASE_02__settings-activity-insets.md](PHASE_02__settings-activity-insets.md) |
| 03 | secondary-activities-insets | 01 | ✅ Done | 3/3 | [PHASE_03__secondary-activities-insets.md](PHASE_03__secondary-activities-insets.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Neither open research item (strategic §6) blocks implementation — the fix covers all scenarios (insets=0 AND insets>0, both portrait and landscape). Both items are **verification items** to confirm during `BlockNeedUserTest`, not design-time blockers.

- [ ] **Verify (BlockNeedUserTest):** orientation the car head unit reports — determines which code path Timber tags should probe in Phase 02. See strategic §6.1.
- [ ] **Verify (BlockNeedUserTest):** actual value of `statusBarHeightSafe` on the device — confirms which fallback tier fires. See strategic §6.2.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` — no update needed (strategic §8: "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (`scan.ps1`).
- [ ] `/spec-check S0204` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status via `update.ps1 -Status Block...`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0204`.

---

## Blockers Log

*(none)*

---

## Change Log

- 2026-05-14 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-14 — Implementation completed; spec advanced to `BlockNeedUserTest` for on-device verification.
