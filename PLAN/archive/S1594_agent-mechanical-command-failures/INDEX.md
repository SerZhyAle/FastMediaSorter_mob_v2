# Tactical Plan: S1594 - agent-mechanical-command-failures

**Strategic spec:** [`../S1594_agent-mechanical-command-failures.md`](../S1594_agent-mechanical-command-failures.md)
**Research inputs:** [`research/01__hook-output-surface.md`](research/01__hook-output-surface.md)
**Feature:** Mechanical command-failure removal (agent infrastructure)
**Tier:** 1 - Quick Win (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-08-12

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | python3-path-shim | - | ✅ Done | 1/1 | [PHASE_01__python3-path-shim.md](PHASE_01__python3-path-shim.md) |
| 02 | bash-unavailable-command-guard | - | ✅ Done | 3/3 | [PHASE_02__bash-unavailable-command-guard.md](PHASE_02__bash-unavailable-command-guard.md) |
| 03 | read-guard-rewrite | - | ✅ Done | 3/3 | [PHASE_03__read-guard-rewrite.md](PHASE_03__read-guard-rewrite.md) |
| 04 | docs-catalog-cleanup | 02, 03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Phases 01, 02 and 03 are mutually independent - each closes its own failure class and consumes nothing the others produce. Phase 04 is last because its rule text and policy prose name the guard filenames that 02 and 03 create.

---

## Pre-Implementation Blockers

No open research items. Strategic §6 item 1 is Resolved by [`research/01__hook-output-surface.md`](research/01__hook-output-surface.md), verified by experiment against the running Claude Code on 2026-08-12.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 states "Без изменений в docs/FEATURES".
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/<module>.jsonl` - not applicable: no Kotlin public API touched.
- [x] `/spec-check S1594` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1594`.

---

## Blockers Log

- No blockers recorded.

---

## Change Log

- 2026-08-12 - Initial tactical plan authored by `/spec-tech`.
