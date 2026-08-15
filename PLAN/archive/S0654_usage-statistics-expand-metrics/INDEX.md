# Tactical Plan: S0654 - usage-statistics-expand-metrics

**Strategic spec:** [`../S0654_usage-statistics-expand-metrics.md`](../S0654_usage-statistics-expand-metrics.md)
**Research inputs:** [`research/01__untracked-behaviors-gap-analysis.md`](research/01__untracked-behaviors-gap-analysis.md)
**Feature:** Expand usage-statistics metric coverage
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Implemented - device test pending (BlockNeedUserTest)
**Phases:** 7 / 7 done (Phase 05 `TEXT_TRANSLATIONS` key deferred to a 2nd wave)
**Last updated:** 2026-06-24

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations | - | ✅ Done | 4/4 | [PHASE_01__foundations.md](PHASE_01__foundations.md) |
| 02 | operations-emission | 01 | ✅ Done | 2/2 | [PHASE_02__operations-emission.md](PHASE_02__operations-emission.md) |
| 03 | viewing-emission | 01 | ✅ Done | 2/2 | [PHASE_03__viewing-emission.md](PHASE_03__viewing-emission.md) |
| 04 | automation-streams-emission | 01 | ✅ Done | 4/4 | [PHASE_04__automation-streams-emission.md](PHASE_04__automation-streams-emission.md) |
| 05 | second-wave-emission | 01 | ✅ Done (translation deferred) | 2/3 | [PHASE_05__second-wave-emission.md](PHASE_05__second-wave-emission.md) |
| 06 | presentation | 01,02,03,04,05 | ✅ Done | 3/3 | [PHASE_06__presentation.md](PHASE_06__presentation.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Phase 01 must not start while any blocker is unchecked. These are owner-decision items from strategic §6 (resolve via `/spec-quiz S0654` or owner sign-off). The plan below bakes a default and marks where each decision lands.

- [x] **Owner decision (§6.1):** first-wave composition. Resolved with default by `/spec-all` (ADR-2 allows deferral): Phases 02-04 ship in full; Phase 05 shipped undo + OCR; `TEXT_TRANSLATIONS` deferred to a 2nd wave (3 scattered entry points, no cheap shared seam - `TextTranslationFacade.translate()` is per-fragment and would over-count). Owner may still trim/expand the shipped set.
- [x] **Owner decision (§6.2):** stream-play granularity. Resolved with default: two keys (`STREAMS_AUDIO_PLAYED`, `STREAMS_VIDEO_PLAYED`).
- [x] **Owner decision (§6.3):** new dashboard section vs reuse. Resolved with default: reuse existing categories (no new `StatsCategory`). Mapping: OPERATIONS (rename, favorites, scheduled, undo), VIEWING (slideshow, GIF frame, stream plays, OCR), SOURCES (streams added, playlists imported).

---

## Completion Gate

- [x] All phases show ✅ Done (Phase 05 partial: `TEXT_TRANSLATIONS` deferred to a 2nd wave).
- [x] `docs/FEATURES.md` showcase - NOT edited per-spec; `/skill-release` owns it (CLAUDE.md §11). Capability recorded in `docs/ALL_FEATURES.jsonl`.
- [x] `dev/CHANGELOG.md` has an entry for the change set.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] On-device verification done (status `BlockNeedUserTest`) - perform each scenario, confirm the counter grows on the dashboard and appears in the report.
- [ ] `/spec-check S0654` returns `Verified` (after device test).
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0654`.

---

## Blockers Log

- 2026-06-23 - Phases gated by three §6 owner-decision items above. Next: owner sign-off or `/spec-quiz S0654`.
- 2026-06-24 - `/spec-all` resolved all three §6 blockers with the baked defaults (ADR-2 backs the translation deferral) and ran the pipeline end to end.
- 2026-06-24 - `TEXT_TRANSLATIONS` (Phase 05.3) deferred to a 2nd wave: EPUB/PDF/image translation have 3 scattered completion points; the shared `TextTranslationFacade.translate()` seam operates per text fragment and would over-count page/chapter/image-level translations. Undo + OCR shipped.
- 2026-06-24 - Pre-existing unit-test source-set compile breakage (unrelated to S0654) parked as `S0657` via `/spec-draft`; blocks running the suite but not the standard debug assemble.

---

## Change Log

- 2026-06-23 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-24 - `/spec-all` implemented Phases 01-07; standard debug assemble PASS; status -> `BlockNeedUserTest` for on-device counter verification.
- 2026-06-24 - Device smoke on emulator-5554 (fresh debug APK): install OK, launch OK (no crash - Hilt singleton graph + `StatsSinkImpl` init), opening a browse folder constructs `BrowseViewModel` with the new `StatsSink` injection without a DI crash, no FATAL across launch+browse. Per-scenario counter growth NOT verified: the AVD has 0 MediaStore-indexed files ("No files found" on virtual resources), no configured streams, and OCR engines not installed - so rename/favorite/slideshow/GIF/stream/OCR scenarios are unreachable here. Full counter verification stays for a real device (kept `BlockNeedUserTest`).
