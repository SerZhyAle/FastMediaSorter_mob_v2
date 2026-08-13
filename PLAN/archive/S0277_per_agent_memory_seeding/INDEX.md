# Tactical Plan: S0277 - Per-Agent Memory Seeding

**Strategic spec:** [../S0277_per_agent_memory_seeding.md](../S0277_per_agent_memory_seeding.md)
**Feature:** One-shot seeding of three named target agent profiles with adapted starter memory derived from the donor profile.
**Tier:** 3 - Moderate (ad-hoc infrastructure)
**Priority:** 40
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-05-21

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | inventory-and-mapping | - | ✅ Done | 2/2 | [PHASE_01__inventory-and-mapping.md](PHASE_01__inventory-and-mapping.md) |
| 02 | seed-kotlin-developer | 01 | ✅ Done | 3/3 | [PHASE_02__seed-kotlin-developer.md](PHASE_02__seed-kotlin-developer.md) |
| 03 | seed-solution-researcher | 01 | ✅ Done | 3/3 | [PHASE_03__seed-solution-researcher.md](PHASE_03__seed-solution-researcher.md) |
| 04 | seed-doc-writer | 01 | ✅ Done | 3/3 | [PHASE_04__seed-doc-writer.md](PHASE_04__seed-doc-writer.md) |
| 05 | verify-and-close | 02, 03, 04 | ✅ Done | 3/3 | [PHASE_05__verify-and-close.md](PHASE_05__verify-and-close.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. All §0 owner-gate fields and §6 research items resolved on 2026-05-21.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] Each target profile dir under `.claude/agent-memory/<profile>/` contains a non-empty `MEMORY.md` and one file per migrated invariant with valid frontmatter (`name`, `description`, `metadata.type`).
- [ ] Every seeded record body contains an explicit `**How to apply:**` block (or, for `user`-type ritual records, an equivalent role-targeted note).
- [ ] `git diff --stat .claude/agent-memory/android-rd-specialist/` produces empty output (donor unchanged).
- [ ] `git diff --stat .claude/agents/ .claude/settings.json .claude/settings.local.json` produces empty output (harness config unchanged).
- [ ] `dev/CHANGELOG.md` carries one entry per new memory file (recorded via `scripts/add_to_dev_log.ps1`).
- [ ] `/spec-check S0277` returns `Verified`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0277`.

---

## Blockers Log

- (empty)

---

## Change Log

- 2026-05-21 - Initial tactical plan authored by `/spec-all` (F2 stage).
- 2026-05-21 - All 5 phases executed by `/spec-all` (F3 stage); 62 memory files seeded across 3 target profiles. All Completion Gate predicates verified.
