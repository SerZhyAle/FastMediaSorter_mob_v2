---
name: launcher-roadmap-greenlit-2026-07
description: S0404 launcher roadmap owner-greenlit 2026-07-18 via batch quiz; children S1087-S1104 now drivable Drafts, build S1088 dialog first; S1098 archived
metadata:
  type: project
---

On 2026-07-18 the owner greenlit the whole launcher roadmap (epic S0404) via a batch `/spec-quiz` (16 decisions, master gate Q0=A). The tactical freeze that previously parked its child feature tickets as `BlockQuestions` no longer applies.

**Why:** during the earlier `/spec-next` launcher run these tickets were blocked precisely because S0404 was owner-gated and each child needed an explicit greenlight + design call. The owner has now given all of them in one sheet (`temp/scratch/launcher-quiz-questions.md`).

**How to apply:**
- Treat launcher feature tickets (S1087, S1091, S1093, S1094, S1103, S1107, ..) as drivable Drafts - do NOT re-block them as "needs greenlight / owner-gated". They were restored BlockQuestions -> Draft.
- Build order: **S1088** (System launcher settings dialog) is the FOUNDATION - author + build it first. S1090 (lock desktop) and S1101 (wallpaper) stay `BlockByOtherTask(S1088)` until it lands; S1102 (launcher user docs) stays blocked until the feature surface lands.
- **S1098** (clickable status indicators) was ARCHIVED - owner deprioritized the status-area replace path (S1087 stays default-OFF, `S1098-1: N/A`). Revive only if that path is later prioritized.
- Non-obvious design decisions are locked in each spec's `### Quiz decisions (2026-07-18)` block. Deviations from the recommended default worth remembering: S1093 resize = drag-handles + any-widget-can-go-fullscreen (no per-widget cap); S1103 "scheduled operations" cell triggers the op directly (not open the schedules screen); S1107 onboarding-launcher fix = variant B (deferred HOME-role request from Settings/Main, amends ADR-2's "chooser on next Home press").
- S1107 is pri 90 (bug) and fully diagnosed - fast-track candidate ahead of the pri-50 feature tickets.

**UPDATE 2026-07-22 (owner explicitly drove 8 launcher-polish tickets):** the owner batch-requested `implement S1094, S1099, S1103, S1089, S1091, S1092, S1093, S1095` and I drove all 8 through the full pipeline (spec-tech -> spec-dev -> phase audits). All 8 landed in **BlockNeedUserTest** on 2026-07-22, awaiting a batched `/spec-sweep` on device (emulator-5554 was online but device tests were deferred to one sweep). Explicit owner request OVERRIDES the "do not autonomously drive launcher-family" note below - that note is about `/spec-next`, not an owner ask. Key outcomes worth remembering:
- **S1099 needed no code** - "don't cover the nav bar" was already implemented by `applySystemBarInsetPadding()` reserving the bottom inset on `launcherRoot` + no immersive hide. Filled the empty Draft, set BNUT for device confirm only.
- **S1093 (resize)** added `LauncherDesktopRepository.resizeCell` (mirrors `moveCell`, reuses `findOverlapping` excludeId) + `LauncherResizeManager` (preview-overlay gesture: real cell never mutated during drag, only the cells Flow commits) + a bottom-right handle on GADGET cells. Floor = gadget seed, ceiling = full width x viewport rows.
- **S1094** decoupled `LauncherGadget.minSpanW/minSpanH` (resize floor, default getters = defaultSpan) from `defaultSpanW/H` (seed) - clock seeds 4x2 but floors at 2x1; S1093's `seedFloor` now reads `minSpan*`. Long-press clock -> calendar (ACTION_VIEW CalendarContract), tap -> alarms, seconds added.
- **S1103 = Part 1 only** - `LauncherCellCommand.ScheduledOp(op:<id>)` (new sealed case + tolerant codec, no Room migration), a "Quick-access panel" `InternalRouteCatalog` route (self-referential panel-editor tile accepted, no per-consumer filter), and a scheduled-op cell that tap -> confirm dialog -> background run + toast (owner 2026-07-22, because ops can DELETE/MOVE). **Part 2 (third-party app-shortcuts) stays blocked on S0427** - strategic criterion 3 is open; `/spec-check` will likely mark S1103 `Partial`.
- Shared-infra additions reused cleanly: `SearchableOptionPickerController.attach` gained an optional `columns: Int = 1` (S1095 app picker grid; 7 other pickers untouched via the default).

**UPDATE 2026-07-20 (supersedes the greenlit framing above):** the live catalog now shows **S0404 (epic) and S1088 (foundation dialog) = `Archived`** (updated 2026-07-18 19:23). The launcher-mode-profiles direction was shelved, NOT driven. Yet the child feature tickets (S1087, S1089-S1095, S1099, S1101-S1103) plus bugfixes S1096/S1097 remain **live** (Draft/BlockNeedUserTest) and skip-cached as "concurrent-wip: active uncommitted system-launcher implementation". Net: do NOT autonomously drive launcher-family tickets - their epic is archived, so `/spec-next` correctly leaves them skip-cached; the "tree settled -> re-check concurrent-wip" note in [[debug-v026-tree-settled-2026-07-19]] does NOT mean these are drivable. Orphaned-children hygiene (archive the kids too, or un-archive the epic) is an owner call - flag it, don't act.
