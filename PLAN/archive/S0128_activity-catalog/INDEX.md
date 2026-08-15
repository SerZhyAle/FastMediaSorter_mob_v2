# Tactical Plan: S0128 — activity-catalog

**Strategic spec:** [`../S0128_activity-catalog.md`](../S0128_activity-catalog.md)
**Feature:** Activity Catalog — dev tooling catalog of all Android Activity entry points
**Tier:** 2 — Easy
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-05-09

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | schema-foundation | — | ✅ Done | 2/2 | [PHASE_01__schema-foundation.md](PHASE_01__schema-foundation.md) |
| 02 | scan-script | 01 | ✅ Done | 3/3 | [PHASE_02__scan-script.md](PHASE_02__scan-script.md) |
| 03 | query-render-set | 02 | ✅ Done | 3/3 | [PHASE_03__query-render-set.md](PHASE_03__query-render-set.md) |
| 04 | initial-population | 03 | ✅ Done | 3/3 | [PHASE_04__initial-population.md](PHASE_04__initial-population.md) |
| 05 | docs-catalog-cleanup | 04 | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No blockers — all §6 research items resolved.

---

## Schema note (carries into all phases)

JSONL record fields for each Activity:

**Auto-populated by `scan.ps1`:**
- `class` — simple class name (e.g. `PlayerActivity`)
- `package` — fully-qualified class name
- `module` — `app_v2` or `wear`
- `path` — relative path to `.kt` source (empty if source not found)
- `sourceSet` — `main`, `vr`, or `""` (flavor of source)
- `exported` — `true`/`false` from manifest
- `launcher` — `true` if has `MAIN + LAUNCHER` intent-filter
- `intentActions` — array of `action` strings from all intent-filters
- `intentCategories` — array of `category` strings
- `noFlavors` — array of flavors where the Activity is absent (derived from flavor manifests)
- `loc` — source file line count (`0` if source not found)
- `lastTouched` — `yyyy-MM-dd` from `git log` (empty if no source)

**Manual (preserved on rescan, merge key = `module + class`):**
- `role` — English one-line description
- `roleRu` — Russian one-line description (used for RU-language search)
- `tags` — array of keyword strings (e.g. `["player","portrait","fullscreen","pip"]`)
- `status` — `new` / `tested` / `todo` / `unknown`
- `notes` — free-text notes

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` unchanged — dev tooling only (see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `/spec-check S0128` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0128`.

---

## Blockers Log

*(none)*

---

## Change Log

- 2026-05-09 — Initial tactical plan authored by `/spec-tech`.
