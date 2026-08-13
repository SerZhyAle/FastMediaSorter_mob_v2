# Tactical Plan: S1313 - settings-manifest-blind-to-dialog-settings

**Strategic spec:** [`../S1313_settings-manifest-blind-to-dialog-settings.md`](../S1313_settings-manifest-blind-to-dialog-settings.md)
**Research inputs:** none
**Feature:** Settings documentation scope - cover settings hosted outside `fragment_settings_*`
**Tier:** Documentation integrity / mechanical gate
**Priority:** 40
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-08-01

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Architecture decision this plan implements

The manifest has two consumers with different requirements, and today one scan serves both:

- **in-app settings search** needs every indexed row to be *navigable*. `SettingsActivity.onSearchResultSelected` switches a ViewPager tab and calls `findViewById(viewId)` on the activity, so a row that lives in a dialog can never be found. The owner already ruled on this twice: S0604 de-indexed transient buttons because they "yield dead search results", and S1035 settled that after the edge-gesture rows moved into a dialog "search now surfaces the entry point only (owner §6.6)".
- **published `SETTINGS_REFERENCE*.md`** needs *completeness*. A setting the user can change must be documented regardless of which surface hosts it.

Therefore this plan **does not widen `SettingsSearchLayoutCatalog`** - that would re-open the dead-result class the owner already closed. It adds a parallel documentation-scope catalog consumed only by the manifest exporter and the docs chain. In-app search behaviour is unchanged by design.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | doc-scope-catalog | - | ✅ Done | 2/2 | [PHASE_01__doc-scope-catalog.md](PHASE_01__doc-scope-catalog.md) |
| 02 | manifest-export-widening | 01 | ✅ Done | 3/3 | [PHASE_02__manifest-export-widening.md](PHASE_02__manifest-export-widening.md) |
| 03 | annotations-backfill | 02 | ✅ Done | 2/2 | [PHASE_03__annotations-backfill.md](PHASE_03__annotations-backfill.md) |
| 04 | reference-renderer-sections | 03 | ✅ Done | 3/3 | [PHASE_04__reference-renderer-sections.md](PHASE_04__reference-renderer-sections.md) |
| 05 | scope-regression-gate | 04 | ✅ Done | 3/3 | [PHASE_05__scope-regression-gate.md](PHASE_05__scope-regression-gate.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Owner scope call:** resolved by execution, not by asking (autonomous `/spec-do` run, no owner
  available mid-pipeline) - default proposal adopted for the mechanical exclusions, and the include list
  was corrected against live code rather than taken as written. See strategic spec §3 "Corrections 1/2":
  `dialog_camera_settings`/`dialog_camera_ocr_settings` got an empty `hostKey` (the plan's
  `btnSelectCameraPhotosDest` guess was wrong - verified by grep, that id opens a resource picker, not
  either camera dialog), and `dialog_player_settings`/`dialog_playback_control`/`dialog_slideshow_settings`
  moved from "include" to "exclude" after regenerating the manifest showed they produce noise (live
  transport controls, not settings) or incomplete coverage (`Slider` widget not recognized). Final surface
  count: 6 included (down from the plan's 9), 10 excluded (up from 8). The onboarding-wizard pages
  (`page_welcome_*`) stay explicitly deferred, per the plan's own default.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped; no FEATURES sentence, internal documentation
  fix. (`docs/ALL_FEATURES.jsonl` - the separate developer inventory, not the public showcase - DOES
  carry a record: `settings.dialog-hosted-settings-documented`, per Phase 06 Step 06.4.)
- [x] `dev/CHANGELOG.md` has an entry for the ticket's changed-file set (batched, not per-file).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - Phase 01's new `SettingsDocScopeCatalog` object is set
  `role`/`status` via `dev/CATALOG/scripts/set.ps1`.
- [x] `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0.
- [x] `/spec-check S1313` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1313`.

---

## Blockers Log

- 2026-07-31 - Phase 01 gated on the owner scope call above.
- 2026-08-01 - Resolved without an owner round-trip (autonomous `/spec-do` execution): the plan's default
  proposal adopted for mechanical exclusions; the include/exclude split corrected against live code
  (see strategic spec §3 "Corrections 1/2" and this file's Pre-Implementation Blockers entry above).

---

## Change Log

- 2026-07-31 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-01 - Executed by `/spec-do` -> `/spec-all` (autonomous). Two corrections applied against live
  code during Phase 01/02: `dialog_camera_settings`/`dialog_camera_ocr_settings` hostKey corrected to
  empty (plan's `btnSelectCameraPhotosDest` guess was wrong); `dialog_player_settings`/
  `dialog_playback_control`/`dialog_slideshow_settings` moved from included to excluded (noise /
  incomplete coverage discovered by regenerating the manifest with them included). All 6 phases done.
