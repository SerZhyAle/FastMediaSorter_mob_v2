# Tactical Plan: S0201 — developer-workflow-governance-hardening

**Strategic spec:** [`../S0201_developer-workflow-governance-hardening.md`](../S0201_developer-workflow-governance-hardening.md)
**Feature:** Internal — developer / agent workflow process hardening
**Tier:** 4
**Priority:** 75
**Status:** Tactical
**Phases:** 4 / 4 done
**Last updated:** 2026-05-14 (all phases done — Implemented)

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | resolve-open-questions | — | ✅ Done | 4 | [PHASE_01__resolve-open-questions.md](PHASE_01__resolve-open-questions.md) |
| 02 | validation-ladder-shell-convention | 01 | ✅ Done | 3 | [PHASE_02__validation-ladder-shell-convention.md](PHASE_02__validation-ladder-shell-convention.md) |
| 03 | fail-closed-post-change-gates | 02 | ✅ Done | 3 | [PHASE_03__fail-closed-post-change-gates.md](PHASE_03__fail-closed-post-change-gates.md) |
| 04 | progress-journal-schema | 02 | ✅ Done | 4 | [PHASE_04__progress-journal-schema.md](PHASE_04__progress-journal-schema.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

§6 open questions in the strategic spec were resolved inline before this tactical plan was written:

- [x] §6.1 Session trace primary source — **Resolved**: text-first; per-session rotation of `dev_progress.log`; raw command artifacts to `temp/sessions/`; structured JSONL transcript deferred (future scope).
- [x] §6.2 Trivial edit boundary — **Resolved**: doc-only (`.md` only, no executable artifacts) = grep-check closure; any `.kt`/`.kts`/`.py`/`.ps1`/`.xml` change = build-gate or test-gate closure.
- [x] §6.3 Automation level — **Resolved**: documented validation matrix first; `build-from-path.ps1` helper deferred to P2 roadmap item.

No external blockers.

---

## Completion Gate

- [ ] All 4 phases show ✅ Done.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] S0201 strategic spec `**Status:**` reads `Implemented`.
- [ ] `/spec-check S0201` returns `Verified`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0201`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-05-14 — Initial tactical plan authored by `/spec-all` for S0201 (4 phases). §6 open questions resolved inline.
