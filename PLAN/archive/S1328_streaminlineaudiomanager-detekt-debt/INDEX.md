# Tactical Plan: S1328 - streaminlineaudiomanager-detekt-debt

**Strategic spec:** [`../S1328_streaminlineaudiomanager-detekt-debt.md`](../S1328_streaminlineaudiomanager-detekt-debt.md)
**Research inputs:** none
**Feature:** Clear the live `LongParameterList` finding on `StreamInlineAudioManager` by grouping its constructor dependencies into holder data classes
**Tier:** 2 - Small (ad-hoc)
**Priority:** 35
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-08-03

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Measured baseline - SUPERSEDED (2026-07-31)

> Every number in this section was measured against a detekt report from 2026-07-31 and a baseline
> file that no longer exists. `config/detekt/baseline-app_v2.xml` was rewritten on 2026-08-02
> 15:03:18. Read the next section instead; this one is kept only so the change is legible.

- `StreamInlineAudioManager.kt` - **1** live finding: `LongParameterList - 10/10` at line 43:31.
  The strategic spec's number holds.
- `config/detekt/detekt.yml` - `constructorThreshold: 10`, `functionThreshold: 8`. The report is
  baseline-filtered, so a listed finding is live debt.
- `config/detekt/baseline-app_v2.xml` - **3** entries for this file (all
  `SpacingBetweenDeclarationsWithComments`), not 4 as the strategic spec states. `LongParameterList`
  is absent, confirming live debt.
- `StreamsActivity.kt` - **6** live findings (`TooManyFunctions 42/40`, `Wrapping` x4,
  `ImportOrdering`). The detekt gate is **file-granular**, not line-granular
  (`scripts/quality/assert-detekt.ps1` intersects changed files with files carrying findings), so
  touching this file drags all six into this ticket's gate.
- `MainStreamsInlineAudioManager.kt` - **0** live findings, **0** baseline entries.

---

## Measured baseline (2026-08-02) - authoritative

Re-measured directly against the working tree and against two gated detekt runs, not against a
report file on disk. Neither source file has changed since 2026-07-31; the baseline has.

- `config/detekt/baseline-app_v2.xml` - **12286** lines, mtime 2026-08-02 15:03:18. Was 12656.
- `assert-detekt.ps1 -Module app_v2 -Gate -ChangedFiles StreamsActivity.kt` exits **0**:
  `PASS (no new findings; baselines hold)`. The same gate over `StreamsActivity.kt` plus
  `StreamInlineAudioManager.kt` also exits **0**.
- `StreamInlineAudioManager.kt` - **0** live findings, **4** baseline entries: the three
  `SpacingBetweenDeclarationsWithComments` plus `LongParameterList` at baseline line 3484, whose
  signature spells out the current ten-parameter list. 412 lines.
- `StreamsActivity.kt` - **0** live findings, **12** baseline entries, including
  `TooManyFunctions:StreamsActivity.kt$StreamsActivity : BaseActivity` (line 11620), `ImportOrdering`
  (line 2997) and two `Wrapping` entries (lines 12244-12245). 1205 lines, 61 imports.
- `MainStreamsInlineAudioManager.kt` - **0** live findings, **0** baseline entries. Unchanged.

Two things follow, and they redirect the plan:

- **Phase 01 is no longer gate clearance - it is re-key prevention.** Nothing on
  `StreamsActivity.kt` is live today. But the `ImportOrdering` baseline id embeds the whole import
  block verbatim, and Phase 02 adds two imports to that block, which re-keys the entry and resurfaces
  the finding. Step 01.1 must still run, and must run before Phase 02. The `Wrapping` entries key on
  tokens rather than the import list, so Step 01.2 is optional - keep it only as opportunistic
  cleanup.
- **Phase 02 must now prune, not preserve.** After the constructor changes to four parameters the
  `LongParameterList` entry at line 3484 is dead - its signature names parameters that no longer
  exist. Delete it, the way S1350 and S1351 deleted theirs. The old "baseline byte-identical" gate
  criterion is inverted.

Both call sites must change, because named arguments bind to constructor parameter names - there is
no constructor-shape change that leaves the call sites untouched.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | streamsactivity-gate-clearance | - | ✅ Done | 1/2 (+1 skipped) | [PHASE_01__streamsactivity-gate-clearance.md](PHASE_01__streamsactivity-gate-clearance.md) |
| 02 | inline-audio-parameter-objects | 01 | ✅ Done | 3/3 | [PHASE_02__inline-audio-parameter-objects.md](PHASE_02__inline-audio-parameter-objects.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Both cleared 2026-08-02. Neither needed the answer it was written to ask for.

- [x] **Cross-ticket:** the premise is void. `TooManyFunctions - 42/40` on `StreamsActivity.kt` was
      baselined by the 2026-08-02 baseline rewrite (line 11620), and a gated detekt run scoped to
      that file exits 0. None of routes (a) land S1198 first / (b) absorb a 3-function extraction /
      (c) won't-fix is required, and the release order needs no change. What survives is not
      cross-ticket at all: Phase 02 adds imports, which re-keys the `ImportOrdering` baseline entry,
      so Step 01.1 runs first. See "Measured baseline (2026-08-02)" above.
- [x] **Shape:** confirmed - both holders, four constructor parameters. Views-only would leave eight
      against a threshold of ten, i.e. the same one-dependency-from-failure position the ticket
      exists to escape. Decided by the ticket's own goal, not put to the owner.
- [x] **Fate of the ticket** (new, asked 2026-08-02): the finding is frozen rather than live, so
      doing nothing was a real option. Owner chose to fix it anyway and prune the dead baseline entry
      with it - a `LongParameterList` signature is the parameter list itself, so the freeze breaks the
      moment anyone adds a dependency.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: pure refactor, no shippable capability.
- [x] `dev/CHANGELOG.md` has an entry for the change - one row per logical change, per CLAUDE.md, not
      one per file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated, and both holder records carry `role` + `status=new`.
- [x] `config/detekt/baseline-app_v2.xml` differs from its pre-S1328 state by **exactly one deleted
      line** - 12286 -> 12285, `StreamInlineAudioManager.kt$` entries 4 -> 3. Deleted by hand, no
      regeneration.
- [x] `scripts/quality/audit-detekt-baseline-drift.ps1` reports no dead entry for
      `StreamInlineAudioManager.kt` (exit 0, zero `StreamInlineAudio` lines in its output).
- [x] No `@Suppress` annotation exists in any file this ticket touched.
- [ ] `/spec-check S1328` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1328`.

---

## Blockers Log

- 2026-07-31 - Whole plan blocked before Phase 01: `TooManyFunctions 42/40` on `StreamsActivity.kt`
  belongs to S1198. Next: owner picks route (a) / (b) / (c) in Pre-Implementation Blockers.
- 2026-08-02 - Cleared, but not by any of those routes. The baseline was rewritten on 2026-08-02
  15:03:18 and swallowed both this ticket's finding and S1198's, so the gate now passes on both files
  and the entanglement no longer exists. Owner confirmed the refactor goes ahead regardless. Plan
  redirected: Phase 01 becomes re-key prevention, Phase 02 prunes the dead entry instead of
  preserving the baseline. Status restored to `Tactical`. The unlogged regeneration is parked as
  S1356.

---

## Change Log

- 2026-07-31 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-02 - `/spec-quiz`: measurements re-taken against the current tree, superseded section
  marked, Pre-Implementation Blockers cleared, Completion Gate baseline criterion inverted.
