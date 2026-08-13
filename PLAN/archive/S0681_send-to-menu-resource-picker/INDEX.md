# Tactical Plan: S0681 - send-to-menu-resource-picker

**Strategic spec:** [`../S0681_send-to-menu-resource-picker.md`](../S0681_send-to-menu-resource-picker.md)
**Research inputs:** none
**Feature:** Unified «Send to..» menu + pinned «Select resource..» (copy-to) entry
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 4 / 4 done
**Last updated:** 2026-06-25

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | unified-menu-entry | - | ✅ Done | 4/4 | [PHASE_01__unified-menu-entry.md](PHASE_01__unified-menu-entry.md) |
| 02 | main-player-wiring | 01 | ✅ Done | 1/1 | [PHASE_02__main-player-wiring.md](PHASE_02__main-player-wiring.md) |
| 03 | standalone-wiring | 01 | ✅ Done | 4/4 | [PHASE_03__standalone-wiring.md](PHASE_03__standalone-wiring.md) |
| 04 | docs-catalog-cleanup | 01,02,03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 items 2 (exclude current resource) and 3 (post-copy navigation) are tactical design choices resolved inside the plan, not external research:

- Item 2: main player passes its current `resourceId` (current resource excluded from the recipient list); standalone hosts have no resource context and pass `-1` (full recipient list shown) - the acceptable fallback named in strategic §6.2. Resolved in Phase 02 / Phase 03.
- Item 3: copy reuses each host's existing copy behavior - main player honors the existing `goToNextAfterCopy` setting; standalone copy keeps the viewer open. Resolved in Phase 02 / Phase 03.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/ALL_FEATURES.jsonl` has a record for the new capability (via `scripts/all_features/add.ps1`).
- [ ] `docs/FEATURES*.md` - NOT edited here; release showcase is `/skill-release`-owned (CLAUDE.md §11). Strategic §8 sentence is input for the future release diff only.
- [ ] `dev/CHANGELOG.md` has an entry for the change (via `add_to_dev_log.ps1`).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API of `SendToMenuManager` / `StandaloneFileOperationsHandler` changed).
- [ ] `/spec-check S0681` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to the matching `Block*`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0681`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-25 - Initial tactical plan authored by `/spec-tech`.
