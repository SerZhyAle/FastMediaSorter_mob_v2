# Tactical Plan: S0667 - player-rotation-fullscreen-sync

**Strategic spec:** [`../S0667_player-rotation-fullscreen-sync.md`](../S0667_player-rotation-fullscreen-sync.md)
**Research inputs:** [`research/03__image-zoom-pan-preserved.md`](research/03__image-zoom-pan-preserved.md)
**Feature:** Sync fullscreen/command-panel mode with device rotation
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-06-24

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | orientation-mode-resolver | - | ✅ Done | 2/2 | [PHASE_01__orientation-mode-resolver.md](PHASE_01__orientation-mode-resolver.md) |
| 02 | stream-host-wiring | 01 | ✅ Done | 1/1 | [PHASE_02__stream-host-wiring.md](PHASE_02__stream-host-wiring.md) |
| 03 | standalone-host-wiring | 01 | ✅ Done | 1/1 | [PHASE_03__standalone-host-wiring.md](PHASE_03__standalone-host-wiring.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - all strategic §6 items are Resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (owned by `/skill-release`; capability recorded in `docs/ALL_FEATURES.jsonl` in Phase 04).
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new class added in Phase 01).
- [ ] `/spec-check S0667` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to a `Block*` value.
5. All done: flip `Status:` to `Done`, run `/spec-check S0667`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-24 - Initial tactical plan authored by `/spec-tech`.
