# Tactical Plan: S0663 - app-launch-panel-internal-routes

**Strategic spec:** [`../S0663_app-launch-panel-internal-routes.md`](../S0663_app-launch-panel-internal-routes.md)
**Research inputs:** none
**Feature:** Internal routes & OS shortcuts in the app-launch panel
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest (all 5 phases done; awaiting on-device verification)
**Phases:** 5 / 5 done
**Last updated:** 2026-06-24

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Storage decision (no schema change)

All three new tile kinds reuse the already-modelled `AppLaunchPanelTileType.INTERNAL_ROUTE` and the existing `targetId` TEXT column via a namespace scheme:

- `fn:<routeKey>` - our own feature (e.g. `fn:calculator`, `fn:game`, `fn:ocr`, `fn:streams`, `fn:favorites`).
- `resource:<resourceId>` - a specific resource (multiple such tiles allowed).
- `os:<targetKey>` - a curated OS system target (e.g. `os:settings`, `os:wifi`).

`RESERVED` stays the empty-slot UI sentinel only. No `@Database` version bump, no migration. The "three paths" is an editor UI grouping, not a storage concept.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | route-catalogs | - | ✅ Done | 5/5 | [PHASE_01__route-catalogs.md](PHASE_01__route-catalogs.md) |
| 02 | launch-dispatch | 01 | ✅ Done | 3/3 | [PHASE_02__launch-dispatch.md](PHASE_02__launch-dispatch.md) |
| 03 | editor-three-paths | 01, 02 | ✅ Done | 6/6 | [PHASE_03__editor-three-paths.md](PHASE_03__editor-three-paths.md) |
| 04 | default-seed | 01 | ✅ Done | 2/2 | [PHASE_04__default-seed.md](PHASE_04__default-seed.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

These strategic §6 items are `Open` and require an owner/design decision before the affected phase starts. Recommended defaults are noted; resolve via `/spec-quiz S0663` or inline confirmation.

- [x] **Research §6.1:** behaviour of a tile for an available-but-disabled feature - RESOLVED (quiz 2026-06-24): tile is shown; tapping routes to the relevant Settings toggle (highlighted), like the game widget. No dead launch.
- [x] **Research §6.3:** curated OS-target list - RESOLVED (quiz 2026-06-24): full set of 9 - Settings, Wi-Fi, Bluetooth, Display, Sound, Battery, Storage, App info, Date/time - each shown only if its intent resolves on the device.
- [x] **Research §6.4:** three-path chooser pattern - RESOLVED (quiz 2026-06-24): a category pre-step dialog (External app / OS part / Our feature or resource) before the matching picker.

Resolved during planning (no blocker):

- §6.2 limited availability in default seed: skip unavailable features and shift up, preserving "ours first"; never leave a hole between seeded tiles.
- §6.5 labels/icons: reuse the per-feature launch intents and existing string/icon resources already used by the widgets; no new icon set.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates a FEATURES sentence).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API changed).
- [ ] `/spec-check S0663` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log; if the whole spec is blocked, set journal status to the matching `Block*` state.
5. All done: flip `Status:` to `Done`, run `/spec-check S0663`.

---

## Blockers Log

- 2026-06-24 - Phases 02/03 gated on owner decisions §6.1 / §6.3 / §6.4 (see Pre-Implementation Blockers).

---

## Change Log

- 2026-06-24 - Initial tactical plan authored by `/spec-tech`.
