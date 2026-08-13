# Tactical Plan: S0196 — activity-render-priority-research

**Strategic spec:** [`../S0196_activity-render-priority-research.md`](../S0196_activity-render-priority-research.md)
**Feature:** Activity render-priority research
**Tier:** 3 — Moderate
**Priority:** 50
**Status:** BlockExternal
**Phases:** 3 / 6 done
**Last updated:** 2026-05-16

> **Scope:** tactical, English, developer handoff. This is a pure research spec: phases produce static audits, temp evidence, measurement logs, and recommendation updates. Production code changes are out of scope unless Phase 05 explicitly spawns child specs.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | surface-map | — | ✅ Done | 3/3 | [PHASE_01__surface-map.md](PHASE_01__surface-map.md) |
| 02 | player-host-audit | 01 | ✅ Done | 4/4 | [PHASE_02__player-host-audit.md](PHASE_02__player-host-audit.md) |
| 03 | browse-settings-pickers | 01 | ✅ Done | 4/4 | [PHASE_03__browse-settings-pickers.md](PHASE_03__browse-settings-pickers.md) |
| 04 | perf-measurements | 02, 03 | ⛔ Blocked | 0/4 | [PHASE_04__perf-measurements.md](PHASE_04__perf-measurements.md) |
| 05 | recommendation | 04 | ⬜ Not started | 0/4 | [PHASE_05__recommendation.md](PHASE_05__recommendation.md) |
| 06 | docs-catalog-cleanup | 05 | ⬜ Not started | 0/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Open strategic §6 items are the mandatory research backlog for this spec. Phases 01–04 exist to resolve them, but Phase 05 must not begin while any item below is unchecked.

- [ ] **§6.1** — Standalone image open order is documented with evidence.
- [ ] **§6.2** — Standalone audio open order is documented with evidence.
- [ ] **§6.3** — Standalone video open order is documented with evidence.
- [ ] **§6.4** — Prefetch vs first-frame conflict is classified for player-family flows.
- [ ] **§6.5** — Settings first-page blockers are documented with evidence.
- [ ] **§6.6** — Resource-selection and folder-picker first content order is documented with evidence.
- [ ] **§6.7** — Existing `ViewStub` / `reportFullyDrawn` usage is inventoried.
- [ ] **§6.8** — Deferred-inflate safety for toolbar and overlays is evaluated.
- [ ] **§6.9** — VR boundary verdict is written.
- [ ] **§6.10** — User-visible vs synthetic gain is classified.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` remain unchanged (strategic §8 says no user-facing feature change).
- [ ] `dev/CHANGELOG.md` has an entry for every spec or research artifact written.
- [ ] Strategic spec §6 items are all updated to `Resolved` or `Resolved (Skipped)` with evidence references.
- [ ] Phase 05 recommendation records one verdict per target surface and lists child spec ids if follow-up implementation is warranted.
- [ ] `/spec-check S0196` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0196`.

---

## Blockers Log

- 2026-05-14 — Initial tactical plan created. Open research backlog in strategic §6 remains unresolved; implementation is intentionally blocked at recommendation phase until those items are closed by Phases 01–04.
- 2026-05-15 — Phases 01-03 closed by `/spec-dev` autonomously (static audit + measurement protocol). Phase 04 set to `BlockExternal`: requires manual measurements on a physical reference device (`adb shell am start -W`, Perfetto / systrace traces, screen recordings, frame-by-frame review). Operator must:
  1. Pin a reference phone model + `standardDebug` build SHA into `temp/S0196/04_measurement_journal.md` row 0.
  2. Reuse Meta Quest 3 for the VR boundary 2D pre-VR loading run only.
  3. ~~Add the missing Timber primary-content tags identified in `temp/S0196/01_render_hooks.md` and `temp/S0196/02_player_hosts.md` before running cold-start measurements.~~ **Resolved 2026-05-16:** primary-content tags are now in place for every audited host — Browse, Settings, the three cloud folder pickers, and the StandalonePlayer docs branch (PDF/EPUB). Tag inventory and exact code sites are documented in `temp/S0196/01_render_hooks.md` ("Implications for Phase 04 measurements" section) and `temp/S0196/02_player_hosts.md` ("Open items handed off"). Build verification: `standardDebug` assembles successfully (2026-05-16, 46s).
  4. Execute Phase 04 step set (04.1 → 04.4) per the protocol in `temp/S0196/01_protocol.md`.
  5. Resume `/spec-dev S0196` after measurements land — Phases 05 and 06 are unblocked once `temp/S0196/04_measurement_journal.md` is populated.
- 2026-05-16 — Current workspace preflight confirmed the blocker is still external: `adb devices` returned no attached devices, so Phase 04 cannot capture cold/warm evidence in this session. Measurement scaffolds were added under `temp/S0196/04_*` to remove manual setup on the next resume, but the spec remains `BlockExternal` until the reference phone and Meta Quest 3 runs are recorded.

---

## Change Log

- 2026-05-14 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-15 — Phases 01-03 completed by `/spec-dev`. Artefacts: `01_surface_matrix.md`, `01_render_hooks.md`, `01_protocol.md`, `02_player_hosts.md`, `02_vr_boundary.md`, `02_player_prefetch.md`, `03_browse_settings_pickers.md`, `03_dialog_entrypoints.md`, `03_cloud_pickers.md`. Spec status moved to `BlockExternal` until hardware measurements complete.
- 2026-05-16 — Primary-content Timber tags landed for `BrowseActivity`, `SettingsActivity`, the three `*FolderPickerActivity` classes, `PdfViewerManager`, and `EpubViewerManager` (preparatory work for Phase 04, Blocker #3 of the 2026-05-15 entry). Player image/audio/video tags were already present (added 2026-05-15). `standardDebug` build succeeds (46s). Spec status remains `BlockExternal` — operator measurement run still required before Phase 04 / 05 / 06 can advance.
- 2026-05-16 — Added `temp/S0196/04_measurement_journal.md`, `temp/S0196/04_trace_inventory.md`, and `temp/S0196/04_frame_notes.md` as Phase 04 scaffolds. Current-session preflight also recorded that `adb devices` is empty, so the tactical index remains blocked on external hardware measurements.