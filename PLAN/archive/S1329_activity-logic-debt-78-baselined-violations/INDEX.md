# Tactical Plan: S1329 - activity-logic-debt-78-baselined-violations

**Strategic spec:** [`../S1329_activity-logic-debt-78-baselined-violations.md`](../S1329_activity-logic-debt-78-baselined-violations.md)
**Research inputs:** none
**Feature:** Rule 3 debt sweep - remove domain-layer field injection from Activities
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 45
**Status:** Done
**Phases:** 8 / 8 done
**Last updated:** 2026-08-14

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Scope of this ticket - 46 of 78

Re-counted from `app_v2/lint-baseline.xml` (regenerated 2026-07-31 10:10) and re-derived from current
source with the detector's own rule (`lint-rules/src/main/java/com/sza/fastmediasorter/lint/ActivityLogicDetector.kt`:
`@Inject` field whose `type.canonicalText` contains `Repository`, `UseCase`, `DataSource`, `Dao` or `Database`,
case-sensitive, in a class named `*Activity`). Total is still **78**, distribution unchanged from strategic §0.

This ticket takes **46 violations across 13 files**. Two files are explicitly **deferred to a follow-up ticket**:

| Deferred file | Violations | Reason |
|---|---:|---|
| `PlayerActivity.kt` | 20 | 1406 LOC, the app's most regression-prone host; 9 of its 20 are the image/GIF edit cluster shared with the file below |
| `PhotoVideoStandaloneActivity.kt` | 12 | carries the same image-edit cluster; splitting it from `PlayerActivity` would build the shared facade twice |

Nothing else is dropped. 46 + 32 = 78.

In-scope distribution. LOC re-measured 2026-08-13 during the Phases 02-05 re-plan - the figures authored on
2026-07-31 had drifted by up to 115 lines:

| File | Violations | LOC | Phase |
|---|---:|---:|---|
| `AudioStandaloneActivity.kt` | 7 | 640 | 03 |
| `TextStandaloneActivity.kt` | 7 | 572 | 03 |
| `DocumentStandaloneActivity.kt` | 6 | 867 | 02 |
| `StandalonePlayerActivity.kt` | 6 | 1142 | 02 |
| `BrowseActivity.kt` | 5 | 852 | 05 |
| `MainActivity.kt` | 5 | **1478** | 05 |
| `ReceiveShareActivity.kt` | 4 | 841 | 04 |
| `BaseActivity.kt` | 1 | 621 | 01 |
| `CalculatorActivity.kt` | 1 | 127 | 01 |
| `CameraLaunchActivity.kt` | 1 | 66 | 01 |
| `CameraOcrTranslateActivity.kt` | 1 | 469 | 01 |
| `CameraQuickCaptureActivity.kt` | 1 | 70 | 01 |
| `ScreenCaptureConsentActivity.kt` | 1 | 101 | 01 |

No in-scope Activity exceeds the 1500 LOC ceiling, so no pre-split is required - but `MainActivity.kt` is now
**22 lines** from it, not the 100 the first revision of this plan recorded. Every step touching it must reduce
its count. The factory shape does reduce it: a five-argument manual constructor call becomes a two-argument
`create(..)`.

---

## Fix-shape buckets

**Re-derived 2026-08-13.** The first revision bucketed by *host* - "has a ViewModel" versus "has none" - and
prescribed a ViewModel surface for most of them. Measuring the live tree showed that grouping predicts the
wrong fix: what decides the shape is not whether the host owns a ViewModel but **what the host does with the
dependency**. Roughly 42 of the ~72 in-scope call sites never use the object at all - they hand it to a
manually constructed manager. Full rationale in strategic §9 ADR-1; the four shapes are defined in §5.1.

Shape counts across the 46 in-scope violations:

- **F - factory** (~35 sites, every phase): the host forwards the object into a manager constructor. An
  `@Inject constructor` factory owns the dependency and builds the manager, leaving the manager's own
  signature untouched. This is what keeps the two deferred files out of the blast radius, and it is why the
  shape was chosen over the alternative of widening five manager APIs.
- **V - ViewModel** (~10 sites): the host genuinely calls behaviour. `ReceiveShareActivity`'s auth-session
  cluster (8 sites, Phase 04), `AudioStandaloneActivity.searchLyricsUseCase` (Phase 03), and
  `BrowseActivity`'s single `getDestinationsExcluding` read (Phase 05).
- **S - inherited settings stream** (~6 sites): the host reads settings and extends `BaseActivity`, so it
  consumes `appSettings` from step 01.1. Phases 02, 05.
- **D - dead field** (1): `TextStandaloneActivity.playbackPositionRepository` is injected and never read.
  Deleted, not relocated. Phase 03.

Phases remain the units of work, grouped by subsystem; files are the steps inside them.

- Phase 01 - six thin hosts. ✅ Done, shapes F and S.
- Phases 02-03 - the standalone player family, four hosts sharing `StandaloneHostFactory`. F, plus one V and
  the one D.
- Phase 04 - `ReceiveShareActivity`, the only host with no ViewModel and the only V-dominated one.
- Phase 05 - `BrowseActivity` and `MainActivity`, F-dominated with one V and three S.
- Deferred (32) - the image/GIF edit cluster in `PlayerActivity` + `PhotoVideoStandaloneActivity`.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | Clears | Count after | File |
|---|-------|-----------|--------|------:|---:|---:|------|
| 00 | ratchet-gate-seed | - | ✅ Done | 2/2 | 0 | 78 | [PHASE_00__ratchet-gate-seed.md](PHASE_00__ratchet-gate-seed.md) |
| 01 | thin-host-delegation | 00 | ✅ Done | 6/6 | 6 | 72 | [PHASE_01__thin-host-delegation.md](PHASE_01__thin-host-delegation.md) |
| 02 | standalone-shared-core | 01 | ✅ Done | 3/3 | 12 | 60 | [PHASE_02__standalone-shared-core.md](PHASE_02__standalone-shared-core.md) |
| 03 | standalone-audio-text | 02 | ✅ Done | 3/3 | 14 | 46 | [PHASE_03__standalone-audio-text.md](PHASE_03__standalone-audio-text.md) |
| 04 | receive-share-viewmodel | 00 | ✅ Done | 3/3 | 4 | 42 | [PHASE_04__receive-share-viewmodel.md](PHASE_04__receive-share-viewmodel.md) |
| 05 | browse-main-hosts | 01 | ✅ Done | 4/4 | 10 | 32 | [PHASE_05__browse-main-hosts.md](PHASE_05__browse-main-hosts.md) |
| 06 | ratchet-gate | 00, 02, 03, 04, 05 | ✅ Done | 2/2 | 32 | 0 | [PHASE_06__ratchet-gate.md](PHASE_06__ratchet-gate.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 4/4 | 0 | 32 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

`Count after` is the `ActivityLogicViolation` total the ratchet gate must report once the phase lands, and it
is what makes each phase's Done Criteria provable rather than asserted. The chain 78 -> 72 -> 60 -> 46 -> 42 ->
32 lands exactly on the strategic §11 target, with the final 32 confined to the two deferred files.

Phase 00 comes first because the gate is what stops the debt growing while the sweep runs - seeded at the
current 78, ratcheted down by each code phase. Beyond it, phase order is risk-ascending: an interrupted
sweep leaves a green tree and a coherent merged subset. Phase 05 depends only on 01 and Phase 04 only on
00, so either may be taken before 02-03 if the player family is blocked. Phase 06 needs all four code
phases, because it ratchets the baseline to its final 32.

Every code phase (01-05) ends by ratcheting the gate down with `-UpdateBaseline`, so its Done Criteria are
provable rather than asserted: a phase that cleared seven violations must leave the baseline seven lower.

---

## Invariants binding every phase

- **No `@Suppress`.** `@Suppress("ActivityLogicViolation")` must not appear anywhere in the repository.
- **No manager constructor signature changes (added 2026-08-13).** A factory adapts to the manager as it is.
  Nine of the managers touched by Phases 02-05 are also constructed by `PlayerActivity` or
  `PhotoVideoStandaloneActivity`, the two files this ticket defers, so widening a signature reaches straight
  into out-of-scope code and past its line budgets. Every code phase proves this the same way: `git diff --stat`
  must show zero changes to those two files. A phase that touches either took the wrong shape - stop and
  re-read strategic §9 ADR-1 rather than pressing on.
- **No lint-baseline hand-edit.** `app_v2/lint-baseline.xml` is regenerated by a build (Phase 06), never
  edited by hand. Baseline entries match on message text, so hand-pruning risks unhiding unrelated findings.
- **Probes: nothing left to preserve (corrected 2026-08-13).** The plan was written when four probe
  tickets were `BlockNeedUserTest`; all four are now `Archived` - `S1242`, `S1214`, `S1114`, `S0995` -
  and their `Timber.d("Sxxxx: ..")` lines were removed on that transition, as the CLAUDE.md invariant
  requires. Re-adding one to satisfy a stale verification predicate would break that invariant in the
  other direction and fail `assert-no-ticket-logs`, so the predicates naming them were removed instead.
  Ordinary `// S1214:` rationale comments in `CameraOcrTranslateActivity.kt` are not probes and stay.
- **No new probes.** S1329 is a refactor with no user-visible behavior change; it must not enter
  `BlockNeedUserTest`, so no `Timber.d("S1329: ..)` tags are added.
- **Behavior-preserving.** No user-visible string, layout, or flow changes. No `res/layout*` file is
  touched by any phase, so landscape parity does not apply.
- **Flavor placement.** `ScreenCaptureConsentActivity.kt` lives in `app_v2/src/screenCapture/`. Any new
  collaborator for it stays in that source set - never `src/main/`.

---

## Pre-Implementation Blockers

- [x] **Owner decision (strategic §6.1)** - resolved 2026-08-02 via `/spec-quiz`: split by subsystem, not by
      activity. 46 violations across 13 files stay in S1329; `PlayerActivity` + `PhotoVideoStandaloneActivity`
      (32 violations) are deferred to a follow-up ticket. Plan scope unchanged.
- [x] **Owner decision (strategic §6.2)** - resolved 2026-08-02 via `/spec-quiz`: the ratchet gate is added,
      and seeded **now at 78** rather than at the end at 32. Gate creation moved out of Phase 06 into the new
      Phase 00; Phase 06 keeps only the final ratchet to 32 and the lint-baseline regeneration.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip; strategic §8 says "Без изменений в docs/FEATURES".
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - public API changed (new managers, factories, ViewModel).
- [ ] `ActivityLogicViolation` count in `app_v2/lint-baseline.xml` is exactly 32, all in the two deferred files.
- [x] Follow-up ticket created for the deferred 32 violations.
- [ ] `/spec-check S1329` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status.
5. All done: flip `Status:` to `Done`, run `/spec-check S1329`.

---

## Blockers Log

- 2026-07-31 - Two owner decisions open (see Pre-Implementation Blockers). Phases 01-05 unblocked.
- 2026-08-02 - Both resolved via `/spec-quiz`. No open blockers.
- 2026-08-13 - Phases 00 and 01 landed (count 78 -> 72, gate live and proven to bite). Phase 02 blocked before
  any edit: its fix shape assumes the hosts *use* the six domain dependencies, while the code *forwards* them
  into manager constructors at 12 of 14 call sites, and the managers are shared with the two deferred files.
  Full evidence in `PHASE_02__standalone-shared-core.md`. Phases 02-05 need one re-plan around the factory
  template from Phase 01 - run `/spec-update --tactical` (or `/spec-tech`) before resuming `/spec-dev`.
- 2026-08-13 - **Resolved.** Phases 02-05 re-planned together around the factory template, per the blocker's
  own recommendation. The blocker's claim was re-verified against the live tree first, not taken on trust:
  `DocumentStandaloneActivity` is 12 forwards of 14 sites as recorded, and the same measurement across the
  other six hosts found the shape is not local to Phase 02 - `BrowseActivity` forwards 5 of 6 and
  `MainActivity` 6 of 9, so **Phase 05 carried the identical defect while still reading `⬜ Not started`**.
  No open blockers. Phase 02 is unblocked and is the next step.
- 2026-08-16 - Phase 06 baseline regeneration ran after S1636, but lint no longer reports the custom
  `ActivityLogicViolation` detector. The task removed all 32 deferred entries and churned unrelated records,
  so `app_v2/lint-baseline.xml` was restored to HEAD. Blocker: S1722.
- 2026-08-16 - **Resolved.** S1722 was a false-positive investigation. The custom-rule suite passed 30/30,
  baseline update passed, and both the regenerated baseline and source ratchet report zero
  `ActivityLogicViolation` findings. The 32 former deferred entries are stale because their source fields are
  already gone. Phase 06 is unblocked but requires a scope reconciliation before completion.

---

## Change Log

- 2026-07-31 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-02 - `/spec-quiz`: owner resolved both blockers. Added Phase 00 (gate seeded at 78), reduced
  Phase 06 to the final ratchet plus baseline regeneration, made Phases 01-05 ratchet down as they land.
  Source count re-verified against the live tree before asking: still 78.
- 2026-08-13 - Phases 02-05 re-planned around the `@Inject`-constructed factory template after `/spec-dev`
  blocked Phase 02. Fix-shape buckets re-derived from measurement (F/V/S/D) instead of from whether the host
  owns a ViewModel; strategic §5.1 rewritten and §9 ADR-1 added to record why the shared ViewModel surface was
  refuted. Phase 04 gained a factory step for three forwards the first revision missed (now 3 steps), Phase 05
  split into 4 steps and no longer touches `MainViewModel` at all. New invariant: no manager constructor
  signature may change, proved per phase by a zero-diff check on the two deferred files. LOC figures
  re-measured - `MainActivity` is 1478, not 1400, leaving 22 lines of headroom rather than 100. Phase 07's
  new-class list and its follow-up handoff corrected to match. Ticket scope, phase count and the 46/32 split
  are unchanged.
- 2026-08-13 - Step 02.1 corrected before its first edit with a **parameter budget**, found while writing the
  factory rather than after a failed build. `LongParameterList` is `functionThreshold: 8` /
  `constructorThreshold: 10` and detekt reports **at** the threshold - proved against the baseline, which
  carries an 8-parameter function as a finding. The managers here are far wider than the factory shape assumed
  (`StandaloneViewManager` 18 parameters, `NetworkFileManager` 14, `StandaloneFileOperationsHandler` 13), so a
  naive `create(..)` would have failed the detekt gate. Resolved with the house dependency-bundle idiom from
  `BrowseViewModelDependencies`: a second new file `StandaloneHostDependencies.kt` carrying two injected
  bundles plus a callbacks holder, keeping every `create*` at 7 parameters or fewer. The same budget applies to
  Phases 03-05 and their factories.
