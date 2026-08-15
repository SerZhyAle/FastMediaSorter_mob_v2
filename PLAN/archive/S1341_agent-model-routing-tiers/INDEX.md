# Tactical Plan: S1341 - agent-model-routing-tiers

**Strategic spec:** [`../S1341_agent-model-routing-tiers.md`](../S1341_agent-model-routing-tiers.md)
**Research inputs:** none - facts gathered inline during tactical planning (2026-08-01): 14 command files carry dead `model: sonnet`; `android-kotlin-developer.md`/`android-rd-specialist.md` are `model: inherit`; `android-solution-researcher.md`/`friendly-android-doc-writer.md` already `model: sonnet`; one hardcoded adb path at `.claude/commands/spec-prerelease.md:109`; `scripts/devtest/adb.ps1`'s `Find-Adb` function (lines 147-161) is the existing auto-discovery to reuse.
**Feature:** Two-tier model routing for agents
**Tier:** 2
**Priority:** 68
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-08-01

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | fix-dead-routing-mechanism | - | ✅ Done | 2/2 | [PHASE_01__fix-dead-routing-mechanism.md](PHASE_01__fix-dead-routing-mechanism.md) |
| 02 | split-agent-roster | - | ✅ Done | 4/4 | [PHASE_02__split-agent-roster.md](PHASE_02__split-agent-roster.md) |
| 03 | bound-spawns-and-device-safety | 02 | ✅ Done | 3/3 | [PHASE_03__bound-spawns-and-device-safety.md](PHASE_03__bound-spawns-and-device-safety.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic spec has no §6 research-items section (this ticket's own numbering has no open-questions section - `research_open_count: 0` per preflight); all facts needed were gathered during tactical planning.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip. Strategic §8 not present as a standalone section, but this ticket delivers no user-visible capability (agent/tooling routing only).
- [ ] `dev/CHANGELOG.md` has entry for every modified file (batched per logical change, per CLAUDE.md journaling-granularity rule).
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed - skip, no Kotlin/app source touched.
- [ ] `/spec-check S1341` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1341`.

---

## Blockers Log

None yet.

---

## Change Log

- 2026-08-01 - Initial tactical plan authored by `/spec-tech`.
