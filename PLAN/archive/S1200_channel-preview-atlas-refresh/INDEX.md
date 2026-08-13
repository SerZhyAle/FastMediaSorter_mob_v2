# Tactical Plan: S1200 - channel-preview-atlas-refresh

**Strategic spec:** [`../S1200_channel-preview-atlas-refresh.md`](../S1200_channel-preview-atlas-refresh.md)
**Research inputs:** none (strategic §6 closed inline from code contracts)
**Feature:** Stale-payload detection for downloadable sets, surfaced as "update available"
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 60
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-07-26

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | payload-stamp | - | ✅ Done | 3/3 | [PHASE_01__payload-stamp.md](PHASE_01__payload-stamp.md) |
| 02 | stamp-lifecycle | 01 | ✅ Done | 2/2 | [PHASE_02__stamp-lifecycle.md](PHASE_02__stamp-lifecycle.md) |
| 03 | update-available-status | 02 | ✅ Done | 5/5 | [PHASE_03__update-available-status.md](PHASE_03__update-available-status.md) |
| 04 | offer-on-stale | 03 | ✅ Done | 2/2 | [PHASE_04__offer-on-stale.md](PHASE_04__offer-on-stale.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 carries no Open research item.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES*.md` - skip; showcase is `/skill-release`-owned.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if the public API changed.
- [ ] `/spec-check S1200` returns `Verified`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[x] done` when Verification passes. Never flip on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1200`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-07-26 - Initial tactical plan authored by `/spec-tech`.
