# Phase 03 — Migration Map

**Strategic spec:** [`../S0119_settings-information-architecture-revision.md`](../S0119_settings-information-architecture-revision.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Produce a per-element migration map that assigns each inventory item a canonical placement under the IA model, identifies confirmed misplacements with rationale, and defines the phased migration strategy for future implementation specs.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] `docs/settings-inventory.md` exists with all required sections.
- [ ] `docs/ia-model.md` exists with all required sections including Surface Hierarchy and Placement Checklist.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0119_settings-information-architecture-revision/docs/migration-map.md` | New | ≤ 500 |

> No Kotlin files touched in this phase. Output is a design document only.

---

## Steps

### Step 3.1 — Map every inventory element to its canonical IA placement

**Files:** `PLAN/S0119_settings-information-architecture-revision/docs/migration-map.md`

**Depends on:** — start of phase (Phases 01–02 completed)

**Prompt for developer:**

> Create `PLAN/S0119_settings-information-architecture-revision/docs/migration-map.md`. Add a `## Canonical Placement Map` section. For every element from the Phase 01 inventory, produce a row with: element key (from `SettingsSearchRegistry` or inventory id), current tab / section, canonical tab / section under the IA model, entity type, migration verdict (`stays` / `relocate` / `promote-to-management-surface` / `demote-to-contextual` / `retire`), and blocking prerequisite for migration. Use the Phase 02 Placement Checklist to determine each verdict.

**Verification:**

- `Glob` — `PLAN/S0119_settings-information-architecture-revision/docs/migration-map.md` exists.
- `Grep` — `## Canonical Placement Map` matches in that file.
- `Grep` — `general.language` mentioned (spot-check for a known key).
- `Grep` — `switchAllowDelete` or `allow_delete` mentioned (the known misplacement from Phase 01).

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Files: docs/migration-map.md (new, Canonical Placement Map section ~80 rows). Dev log recorded.

---

### Step 3.2 — Document confirmed misplacements with decision rationale

**Files:** `PLAN/S0119_settings-information-architecture-revision/docs/migration-map.md`

**Depends on:** Step 3.1

**Prompt for developer:**

> Add `## Confirmed Misplacements` section. For each element with migration verdict `relocate`, `promote-to-management-surface`, or `demote-to-contextual`, write a dedicated sub-entry with: element key, current placement, canonical placement, entity type mismatch rationale (why current placement violates the IA model), user-facing impact (discoverability, mental model, non-touch navigation), and implementation risk (high / medium / low based on: whether the element has a search deep-link, whether it opens a management surface, whether it uses inter-fragment state). Minimum expected entries based on Phase 01 anomalies: `switchAllowDelete` (service-action in Playback), streaming cache cluster (service-actions mixed with preferences in General), auth sessions (management surface reachable only through Playback).

**Verification:**

- `Grep` — `## Confirmed Misplacements` matches in `docs/migration-map.md`.
- `Grep` — `switchAllowDelete` mentioned in that section.
- `Grep` — `implementation risk` mentioned in that section (case-insensitive).

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Confirmed Misplacements M1–M5 with switchAllowDelete, implementation risk ratings. Dev log recorded.

---

### Step 3.3 — Define search index misalignment fixes

**Files:** `PLAN/S0119_settings-information-architecture-revision/docs/migration-map.md`

**Depends on:** Step 3.2

**Prompt for developer:**

> Add `## Search Index Fixes Required` section. For every `SettingsSearchRegistry` entry whose `destination` tab does not match the canonical placement decided in Step 3.1, list: the entry key, current `destination` enum value, required `destination` enum value after migration, and the implementation spec that will perform the fix. If no migration spec exists yet, mark as `pending-future-spec`. This section drives the search-registry update that happens alongside each future migration phase.

**Verification:**

- `Grep` — `## Search Index Fixes Required` matches in `docs/migration-map.md`.
- `Grep` — `pending-future-spec` or at least one entry key mentioned in that section.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 2/2 PASS. Search Index Fixes section with pending-future-spec entries present. Dev log recorded.

---

### Step 3.4 — Define phased migration strategy

**Files:** `PLAN/S0119_settings-information-architecture-revision/docs/migration-map.md`

**Depends on:** Step 3.3

**Prompt for developer:**

> Add `## Migration Strategy` section. Group all `relocate` / `promote` / `demote` / `retire` items into migration waves by risk and dependency. Wave 1: zero-risk relocations (no deep-link targets, no management surfaces, no inter-fragment state). Wave 2: medium-risk relocations (deep-link targets or items with associated search entries). Wave 3: high-risk or management-surface promotions (items that open sub-screens or carry behavioral load). For each wave, list the items, the implementation approach (new Fragment, updated `SettingsPagerAdapter`, updated `SettingsSearchRegistry` destination), and the non-regression check required (which search deep-links and keyboard navigation paths must still work after migration). Note that each wave is a separate future implementation spec; S0119 defines the map, not the execution.

**Verification:**

- `Grep` — `## Migration Strategy` matches in `docs/migration-map.md`.
- `Grep` — `Wave 1` mentioned in that section.
- `Grep` — `Wave 2` mentioned in that section.
- `Grep` — `non-regression` mentioned in that section.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS. Migration Strategy with Wave 1/Wave 2/Wave 3 and non-regression requirements. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 3.*` above is `[x] done`.
- [x] `PLAN/S0119_settings-information-architecture-revision/docs/migration-map.md` exists with all four sections.
- [x] §6 blockers §6.4, §6.6, §6.12 marked `[x]` in INDEX.md.
- [x] `Grep` for `TODO(phase-03)` returns zero hits in all files touched.
- [x] Dev log entry added for `docs/migration-map.md` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- `docs/migration-map.md` Wave definitions feed the backlog of future migration specs.
- The `Search Index Fixes Required` section drives `SettingsSearchRegistry` `destination` updates that each future migration wave must include.
- Phase 04 (multilingual-search) is independent of the migration waves — it adds locale aliases without changing existing `destination` values.

---

## Rollback Plan

Revert phase commit(s) — no code changes, no data migration. Only `docs/migration-map.md` is produced.
