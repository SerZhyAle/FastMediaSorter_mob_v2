# Tactical Plan: S0149 — enh-sftp-permission-denied-message

**Strategic spec:** [../S0149_enh-sftp-permission-denied-message.md](../S0149_enh-sftp-permission-denied-message.md)
**Feature:** User-visible SFTP permission denied messages
**Tier:** 1 — Quick Win (ad-hoc)
**Priority:** 25
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-05-11

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | status-classifier | — | ✅ Done | 3/3 | [PHASE_01__status-classifier.md](PHASE_01__status-classifier.md) |
| 02 | message-contract | 01 | ✅ Done | 2/2 | [PHASE_02__message-contract.md](PHASE_02__message-contract.md) |
| 03 | result-surfaces | 01, 02 | ✅ Done | 3/3 | [PHASE_03__result-surfaces.md](PHASE_03__result-surfaces.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [ ] **Research:** Confirm the SFTP access-denied mapping scope — required before Phase 01. Field evidence already shows `3: Permission denied` in `logs/fastmediasorter_20260511_005409.log`; decide whether only `SSH_FX_PERMISSION_DENIED (3)` maps to access denied and keep adjacent server write failures generic. See strategic §6.1.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated only if scope expands beyond the no-inventory-change assumption in strategic §8.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` regenerated after the Kotlin changes.
- [ ] `/spec-check S0149` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0149`.

---

## Blockers Log

- 2026-05-11 — Pre-implementation blocker open: confirm whether access denied is limited to `SSH_FX_PERMISSION_DENIED (3)` for S0149 or whether additional write-side status codes should share the same user-facing copy.

---

## Change Log

- 2026-05-11 — Initial tactical plan authored by `/spec-tech`.