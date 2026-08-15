# Tactical Plan: S0473 - statistics-collection-option-default-off

**Strategic spec:** [`../S0473_statistics-collection-option-default-off.md`](../S0473_statistics-collection-option-default-off.md)
**Research inputs:** none (architecture mapped inline by `/spec-tech`; see strategic §4-§5)
**Feature:** Local usage statistics (opt-in, off by default)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done (journal: BlockNeedUserTest - awaiting device test)
**Phases:** 6 / 6 done
**Last updated:** 2026-06-17

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundation-stores | - | ✅ Done | 6/6 | [PHASE_01__foundation-stores.md](PHASE_01__foundation-stores.md) |
| 02 | opt-in-gate | 01 | ✅ Done | 6/6 | [PHASE_02__opt-in-gate.md](PHASE_02__opt-in-gate.md) |
| 03 | stats-sink-wiring | 01, 02 | ✅ Done | 8/8 | [PHASE_03__stats-sink-wiring.md](PHASE_03__stats-sink-wiring.md) |
| 04 | dashboard | 01, 02 | ✅ Done | 8/8 | [PHASE_04__dashboard.md](PHASE_04__dashboard.md) |
| 05 | author-report | 04 | ✅ Done | 6/6 | [PHASE_05__author-report.md](PHASE_05__author-report.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 5/5 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 declares no open questions; all design decisions are fixed in §0 (refinements 3-4), §3.2, and §9 ADRs. Phase 01 may start immediately.

---

## Scope Decisions (v1 metric coverage)

The stats sink ships an extensible event contract (strategic §5.3). v1 wires the clean, domain-layer completion points across every metric group A-H. The following sub-metrics are deferred to a later iteration because no clean, non-intrusive completion point exists - they are NOT silently dropped, they are explicit extension points the sink API already supports:

- **Slideshow session timing** - `SlideshowController` is UI-layer, needs a callback hook.
- **OCR / translation counts** - `RecognitionBackend` / `TranslationManager` are `ui.player.helpers` and not Hilt-managed; instrumenting them violates layer discipline.
- **Bytes-transferred over network** - no central byte accumulator; would require an interceptor in every `FileTransferProvider`.
- **NAS scan count** - completion is implicit (`finally` block), no success object.
- **Most-used feature / resource ranking** - derived metric, needs a ranking pass over per-feature counters.
- **Top destination folders** - excluded on privacy grounds (folder names approach PII per strategic §2 non-goals).
- **Image-edit count (`EditKind.IMAGE_EDIT`)** - deferred at Phase 03 impl: the planned point `ResourceEditorUseCase.save()` is resource-config editing, not image editing; the real edit save is fragmented (rotate/filter/correction + `NetworkImageEditUseCase`, double-count risk) with no single clean point. Parked as **S0482**. Group D stays represented by drawings + notes.

Every metric GROUP (A-H) is still represented in v1 by its core counters. The deferred items are sub-metrics within already-covered groups.

**Parked during implementation** (CLAUDE.md §3.1, out-of-scope findings): **S0481** (stale `enableGoogleLens` row in `device_profile_presets.csv` fails its gate), **S0482** (image-edit stat instrumentation, see above).

`core/metrics/OperationMetricsRecorder` is a pre-existing in-memory diagnostic counter (resets on process death, logs to Timber, no persistence, no SDK). It is NOT persisted telemetry and does not contradict strategic §4's green-field premise; S0473 does not reuse or extend it.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - update required (strategic §8 mandates a FEATURES sentence).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API added: stores, repository, sink, use cases, Activity, ViewModel).
- [ ] `/spec-check S0473` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0473`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-17 - Initial tactical plan authored by `/spec-tech`.
