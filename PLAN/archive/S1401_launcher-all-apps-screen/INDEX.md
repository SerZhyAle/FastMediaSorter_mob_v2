# Tactical Plan: S1401 - launcher-all-apps-screen

**Strategic spec:** [`../S1401_launcher-all-apps-screen.md`](../S1401_launcher-all-apps-screen.md)
**Research inputs:** [`research/01__sortable-app-fields.md`](research/01__sortable-app-fields.md) · [`research/02__icon-cache-storage.md`](research/02__icon-cache-storage.md) · [`research/03__cache-invalidation-source.md`](research/03__cache-invalidation-source.md)
**Feature:** Full-screen "All apps" list in launcher mode - cached, searchable, sortable, with a unified long-press action menu
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 4 / 7 done - 02, 05 and 06 hold on device-only steps, see Blockers Log
**Last updated:** 2026-08-07

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | app-cache-schema | - | ✅ Done | 6/6 | [PHASE_01__app-cache-schema.md](PHASE_01__app-cache-schema.md) |
| 02 | app-cache-sync | 01 | 🚧 In Progress | 5/6 | [PHASE_02__app-cache-sync.md](PHASE_02__app-cache-sync.md) |
| 03 | sort-and-search | 01, 02 | ✅ Done | 5/5 | [PHASE_03__sort-and-search.md](PHASE_03__sort-and-search.md) |
| 04 | app-action-menu | 01 | ✅ Done | 5/5 | [PHASE_04__app-action-menu.md](PHASE_04__app-action-menu.md) |
| 05 | all-apps-screen | 02, 03, 04 | 🚧 In Progress | 4/5 | [PHASE_05__all-apps-screen.md](PHASE_05__all-apps-screen.md) |
| 06 | entry-points-and-gesture | 05 | 🚧 In Progress | 4/5 | [PHASE_06__entry-points-and-gesture.md](PHASE_06__entry-points-and-gesture.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All four strategic §6 research items are Resolved - items 1-3 by the research artifacts linked above, item 4 by an owner-level decision recorded in §6 (category sorting ships regardless; the fill-rate measurement happens during device verification and cannot block implementation).

---

## Completion Gate

- [ ] All phases show ✅ Done. **2026-08-07: 01, 03, 04 and 07 are Done; 02, 05 and 06 each hold on one device-only step - see Blockers Log. Every machine-checkable step in all seven phases is done.**
- [x] `docs/ALL_FEATURES.jsonl` carries the delivered capability via `scripts/all_features/add.ps1`. `docs/FEATURES*.md` is NOT edited here - it is `/skill-release`-owned (CLAUDE.md section 11). Record `launcher.all-apps-screen`, flavors read from the build gate.
- [x] `dev/CHANGELOG.md` has an entry for every logical change - one row per phase group (01, 02, 03-04, 05-07).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - this spec adds public classes; role and status filled for all six.
- [ ] `/spec-check S1401` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1401`.

---

## Blockers Log

- 2026-08-07 - Three steps are device-only and none of them ran: 02.6 (cold-open speed by eye), 05.5 (search, the five orders, the order surviving a force-stop, both orientations) and 06.5 (button, gesture yielding to scroll, Back, the absent Start-menu row). No online device on this machine, and reaching a launcher desktop needs the in-app onboarding walk on top of that. All three are folded into the ticket's `BlockNeedUserTest` note rather than skipped - the machine half of each is done and cited in its own Step Log.
- 2026-08-07 - The `settings-doc-sync` gate cannot render a verdict in this tree: `SettingsManifestExportTest` kills its JVM during Robolectric teardown, so the gate reports "manifest differs" when nothing was compared. Parked as **S1464**. It is not this ticket's defect - the manifest is byte-identical, and the same gate passed at 10:40 on this same code before the crash started. It does mean the closure verdict for this ticket carries that one gate unproven.

---

## Change Log

- 2026-08-05 - Initial tactical plan authored by `/spec-tech`.
