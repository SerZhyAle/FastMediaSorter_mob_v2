# Tactical Plan: S0376 - file-manager-predefined-resource

**Strategic spec:** [../S0376_file-manager-predefined-resource.md](../S0376_file-manager-predefined-resource.md)
**Feature:** Predefined All Files resource
**Tier:** 4 - Strategic, ad-hoc
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-06-07

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | resource-foundation | - | ✅ Done | 3/3 | [PHASE_01__resource-foundation.md](PHASE_01__resource-foundation.md) |
| 02 | settings-profile-flows | 01 | ✅ Done | 3/3 | [PHASE_02__settings-profile-flows.md](PHASE_02__settings-profile-flows.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Decision:** Button anchor is the existing General settings `All Files` row and hides once the predefined resource exists.
- [x] **Decision:** The resource contract is shared by profile auto-create and manual CTA; name is `All Files`.
- [x] **Decision:** First creation inserts at the top of ordinary resources only once; later triggers keep the user's manual order.
- [x] **Decision:** Settings profile re-apply requires confirmation before auto-creating the missing resource.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0376` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0376`.

---

## Blockers Log

- 2026-06-07 - Initial tactical plan authored.
- 2026-06-07 - Implementation completed; awaiting `/spec-check S0376`.

---

## Change Log

- 2026-06-07 - Initial tactical plan authored for S0376.
- 2026-06-07 - All tactical phases implemented and build-validated.
