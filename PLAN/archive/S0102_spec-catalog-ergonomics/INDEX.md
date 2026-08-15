# Tactical Plan: S0102 — spec-catalog-ergonomics

**Strategic spec:** [`../S0102_spec-catalog-ergonomics.md`](../S0102_spec-catalog-ergonomics.md)
**Feature:** Spec Catalog Ergonomics — operator facade + schema extension
**Tier:** 2 — Easy
**Priority:** 40
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-05-06

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | schema-foundations | — | ✅ Done | 3/3 | [PHASE_01__schema-foundations.md](PHASE_01__schema-foundations.md) |
| 02 | read-commands | 01 | ✅ Done | 3/3 | [PHASE_02__read-commands.md](PHASE_02__read-commands.md) |
| 03 | close-command | 01 | ✅ Done | 1/1 | [PHASE_03__close-command.md](PHASE_03__close-command.md) |
| 04 | bulk-update | 01 | ✅ Done | 1/1 | [PHASE_04__bulk-update.md](PHASE_04__bulk-update.md) |
| 05 | skill-integration | 02, 03, 04 | ✅ Done | 3/3 | [PHASE_05__skill-integration.md](PHASE_05__skill-integration.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All §6 research items resolved at design time — Phase 01 may start immediately.

- [x] **§6.1 Block→final transition** — `close.ps1` requires non-Block status; operator unblocks first via `update.ps1 -Status <previous>`. Resolved.
- [x] **§6.2 DryRun for bulk-update** — not in v1; all-or-nothing validation before any write is sufficient. Resolved.
- [x] **§6.3 MCP layer** — deferred post-CLI stabilisation per decision 2026-05-06. Resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` — no update needed (internal tooling, per strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` — no regen needed (no Kotlin changes).
- [ ] `pwsh -File scripts/spec_catalog/validate.ps1` exits 0.
- [ ] `/spec-check S0102` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/6 done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0102`.

---

## Blockers Log

_(none)_

---

## Change Log

- **2026-05-06** — Initial tactical plan authored by `/spec-tech`.
