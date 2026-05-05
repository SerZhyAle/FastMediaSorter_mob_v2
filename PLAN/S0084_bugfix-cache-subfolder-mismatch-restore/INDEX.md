# Tactical Plan: S0084 — bugfix-cache-subfolder-mismatch-restore

**Strategic spec:** [`../S0084_bugfix-cache-subfolder-mismatch-restore.md`](../S0084_bugfix-cache-subfolder-mismatch-restore.md)
**Feature:** Fix misleading "subfolder mismatch" log on cold-start resume + ensure correct reload scope
**Tier:** 2 — Easy
**Priority:** 90
**Status:** Done
**Phases:** 2 / 2 done
**Last updated:** 2026-05-05

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | fix-cache-miss-detection | — | ✅ Done | 3/3 | [PHASE_01__fix-cache-miss-detection.md](PHASE_01__fix-cache-miss-detection.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 4/4 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 items are **Resolved** by code inspection before writing this plan:

- **§6.1 Subfolder depth** — `lastIndexOf('/')` handles any depth; no constraint. Resolved.
- **§6.2 Recursive scanning** — when resource has recursive scan enabled the root cache already contains all files (including subfolders), so `cacheMatchesInitialFile` is true and the warning never fires in that configuration. Bug manifests only when recursive scan is off and the in-memory cache was built for a different subfolder or is empty (cold start). Resolved.

No blockers — Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API not changed, but file was modified).
- [ ] `/spec-check S0084` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0084`.

---

## Blockers Log

*(none)*

---

## Change Log

- 2026-05-05 — Initial tactical plan authored by `/spec-tech`.
