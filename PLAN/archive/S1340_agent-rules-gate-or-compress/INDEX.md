# Tactical Plan: S1340 - agent-rules-gate-or-compress

**Strategic spec:** [`../S1340_agent-rules-gate-or-compress.md`](../S1340_agent-rules-gate-or-compress.md)
**Research inputs:** none - facts gathered inline during tactical planning (2026-08-01), findings folded into strategic §3.1/§3.3/§3.4 delivery-status notes
**Feature:** Gate the expensive rules, compress the rest
**Tier:** 2
**Priority:** 70
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-08-01

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

> **Scope note (drift from strategic spec).** Strategic §3.1 ("New gates") requires **zero** tactical work - all four gates already landed via S1338 package I before this ticket reached tactical planning (confirmed in strategic spec, delivery-status notes added 2026-08-01). Strategic §3.3 is mostly already correct for the same reason - only two of its six bullets are still open. This tactical plan covers exactly the remaining real work: §3.2 (compress), the two open §3.3 items, and §3.4 (parallel rule files). See strategic spec's inline delivery-status notes for the full mapping.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | compress-verbose-sections | - | ✅ Done | 4/4 | [PHASE_01__compress-verbose-sections.md](PHASE_01__compress-verbose-sections.md) |
| 02 | fix-contradictions | - | ✅ Done | 2/2 | [PHASE_02__fix-contradictions.md](PHASE_02__fix-contradictions.md) |
| 03 | sync-parallel-rule-files | 01, 02 | ✅ Done | 2/2 | [PHASE_03__sync-parallel-rule-files.md](PHASE_03__sync-parallel-rule-files.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. No open strategic §6 research items - this spec has no §6 research section; all facts needed were gathered and folded into the strategic spec's own delivery-status notes during tactical planning.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip. Strategic §6 (out of scope) confirms this ticket delivers no user-visible capability; nothing to record in `docs/ALL_FEATURES.jsonl` either.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated - skip. No Kotlin/app source touched.
- [ ] `/spec-check S1340` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1340`.

---

## Blockers Log

None yet.

---

## Change Log

- 2026-08-01 - Initial tactical plan authored by `/spec-tech`, after patching strategic spec §3.1/§3.3/§3.4 with delivery-status notes reflecting S1338 package I/E/J landing ahead of this ticket.
