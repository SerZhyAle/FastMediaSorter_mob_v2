# Tactical Plan: S0662 - welcome-default-gestures

**Strategic spec:** [`../S0662_welcome-default-gestures.md`](../S0662_welcome-default-gestures.md)
**Research inputs:** [`research/01__default-binding-seeding.md`](research/01__default-binding-seeding.md)
**Feature:** Welcome gesture toggle + default left-edge gesture bindings on install
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 4 / 4 done
**Last updated:** 2026-06-24

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | default-binding-seeding | - | ✅ Done | 2/2 | [PHASE_01__default-binding-seeding.md](PHASE_01__default-binding-seeding.md) |
| 02 | functionality-row | - | ✅ Done | 3/3 | [PHASE_02__functionality-row.md](PHASE_02__functionality-row.md) |
| 03 | gesture-toggle-wiring | 02 | ✅ Done | 4/4 | [PHASE_03__gesture-toggle-wiring.md](PHASE_03__gesture-toggle-wiring.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - all strategic §6 research items are Resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/ALL_FEATURES.jsonl` has the new capability record (via `scripts/all_features/add.ps1`). `docs/FEATURES*.md` is NOT edited here - it is `/skill-release`-owned (CLAUDE.md Rule 11).
- [ ] `dev/CHANGELOG.md` has an entry for the change (via `add_to_dev_log.ps1`).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new use case + manager + changed controller signature).
- [ ] Standard debug build passes (`.\a.ps1 d`).
- [ ] `/spec-check S0662` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log. If the whole spec is blocked, set the journal status to the matching `Block*` state via `update.ps1 -StatusNote`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0662`.

---

## Debug Verification Tags

This spec ends in `BlockNeedUserTest`. `/spec-dev` inserts `Timber.d("S0662: <desc>")` at each changed-flow entry on the final transition only - one per flow: the Welcome gesture-toggle handler and the first-run seeding invocation. Do NOT add S0662 tags in intermediate phases (the permanent-log ticket-id gate rejects them unless the spec is `BlockNeedUserTest`).

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-24 - Initial tactical plan authored by `/spec-tech`.
