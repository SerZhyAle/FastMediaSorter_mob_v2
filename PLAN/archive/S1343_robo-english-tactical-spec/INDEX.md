# Tactical Plan: S1343 - robo-english-tactical-spec

**Strategic spec:** [`../S1343_robo-english-tactical-spec.md`](../S1343_robo-english-tactical-spec.md)
**Research inputs:** none - strategic §6.1-§6.4 were resolved by owner ruling (`/spec-quiz` 2026-08-02); no research artifact was produced.
**Feature:** robo-english form for the tactical part of a spec
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** In Progress
**Phases:** 4 / 4 done
**Last updated:** 2026-08-02

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | form-and-harness | - | ✅ Done | 2/2 | [PHASE_01__form-and-harness.md](PHASE_01__form-and-harness.md) |
| 02 | pilot-run | 01 | ✅ Done | 4/4 | [PHASE_02__pilot-run.md](PHASE_02__pilot-run.md) |
| 03 | decision-and-rollout | 02 | ✅ Done | 2/2 | [PHASE_03__decision-and-rollout.md](PHASE_03__decision-and-rollout.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ✅ Done | 4/4 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Ordering invariant

The live template `.claude/templates/phase-file.md` and the commands that consume it are NOT touched before Phase 03. Strategic §11.4 makes the template edit conditional on the pilot verdict; editing it in Phase 01 would decide the question the pilot exists to answer. Phase 01 therefore produces a **pilot-only variant** at a separate path, and rollout is Phase 03's conditional step.

This plan file set is deliberately written in the **current** form, not the robo form, for the same reason.

---

## Pre-Implementation Blockers

None. Phase 01 may start immediately.

**Deferred by design - not a blocker.** Strategic §6.5 ("нужен ли механический гейт") carries `Status: Open`, and the strategic spec forbids pre-empting it: "предрешать этот вопрос до пилота нельзя - он зависит от исхода пункта 3 (если эффект в пределах шума, гейт не нужен вовсе)". Treating it as an unchecked blocker would deadlock the ticket against its own spec. It is answered by Phase 03 from the pilot verdict and written back in Phase 04.

---

## Recorded measurement deviation

Strategic §11.2 asks for each pilot ticket to be run "в обеих формах" while counting "ходы `/spec-dev`". A tactical plan cannot be executed twice against one ticket: the first execution changes the code, so the second run finds no work and yields no comparable turn count. Phase 02 therefore measures a **paired read-through proxy** - two independent read-only agents each receive exactly one form of the same plan and must produce an execution intent without writing code; `ходы /spec-dev` is recorded as that agent's tool-call count.

Everything else in §6.3 is honoured exactly: same Tier, comparable phase/step counts, and the 2-of-3 significance rule. The substitution itself is surfaced for owner review in Phase 04 rather than silently adopted - if the owner rejects the proxy, only Phase 02 is redone.

The owner ratified this proxy on 2026-08-02 (`/spec-quiz`, strategic §6.3 "Выбор владельца"), choosing it over the literal design because the literal one costs 4-6 full implementations to measure an effect strategic §7 already rates as probably within noise. The Phase 04 review of the substitution still stands - ratifying the method is not ratifying its verdict.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 reads "Без изменений в docs/FEATURES".
- [x] `dev/CHANGELOG.md` has an entry for every modified file - three `post-change.ps1` closures, one per phase that touched files, `S1343` appears 11 times.
- [x] `dev/CATALOG/<module>.jsonl` regeneration - skipped: this ticket changes no Kotlin public API.
- [x] `/spec-check S1343` returns `Verified` - PASS/WARN/FAIL 37/0/0 after one `Partial` round fixed two `Files Touched` tables.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1343`.

---

## Blockers Log

- 2026-08-02 - RESOLVED (`/spec-quiz`): the owner picked option B, the proxy read-through. Phase 02 is unblocked as written; no step needed rewriting. Ticket restored to `In Progress`.
- 2026-08-02 - Phase 02 blocked: the pilot's measurement method is undecided. §11.2 asks for each ticket "в обеих формах" with `/spec-dev` turn counts, which no single execution can produce - a plan runs once, and the second run finds no work. Strategic §6.3 now holds the two candidate designs. Next: owner picks A (literal - execute both forms in isolated worktrees, 4-6 full implementations) or B (proxy - blind paired read-through, ~6 short runs, weaker conclusion). Phase 01 is unaffected and stays ✅ Done.

---

## Change Log

- 2026-08-02 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-02 - Measurement method settled by `/spec-quiz` (option B); Phase 02 unblocked.
- 2026-08-02 - Phases 02-04 executed. Pilot verdict `adopt` (2 of 3 improved, threshold 2); the `Why:` field, the compression rule and the `assert-tactical-step-form` ratchet are live in the template, in `/spec-tech`, `/spec-dev`, `/spec-all` and in both `.github/prompts/` siblings; the pilot variant is deleted. Three plan defects were corrected in place rather than worked around: the two measurement arms carried different information (Phase 02), the §6.5 gate could not be diff-scoped against `HEAD` because `PLAN/` is gitignored (Phase 03), and Phase 04's registry product area did not exist. Parked out-of-scope: `S1368`.
