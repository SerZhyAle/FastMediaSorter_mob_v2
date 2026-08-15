# Tactical Plan: S1424 - launcher-shortcut-full-resource-menu

**Strategic spec:** [`../S1424_launcher-shortcut-full-resource-menu.md`](../S1424_launcher-shortcut-full-resource-menu.md)
**Research inputs:** none
**Feature:** Long press on a launcher resource or stream cell opens that item's full action menu
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-08-07

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | action-catalog | - | ✅ Done | 4/4 | [PHASE_01__action-catalog.md](PHASE_01__action-catalog.md) |
| 02 | resource-cell-menu | 01 | ✅ Done | 6/6 | [PHASE_02__resource-cell-menu.md](PHASE_02__resource-cell-menu.md) |
| 03 | stream-cell-menu | 01, 02 | ✅ Done | 3/3 | [PHASE_03__stream-cell-menu.md](PHASE_03__stream-cell-menu.md) |
| 04 | share-and-scan-actions | 01, 02 | ✅ Done | 3/3 | [PHASE_04__share-and-scan-actions.md](PHASE_04__share-and-scan-actions.md) |
| 05 | single-source-menus | 01 | ✅ Done | 4/4 | [PHASE_05__single-source-menus.md](PHASE_05__single-source-menus.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 carries three research items, all `Resolved` by the owner on 2026-08-07. No blocker gates Phase 01.

- none

---

## Scope boundary

The owner's §3.3 ruling limits the gesture to cells the launcher itself created - the `res:` and `stream:` command kinds. Two neighbouring kinds are deliberately untouched:

- `pin:` (`LauncherCellCommand.PinnedShortcut`) - strategic §2 Non-goals and §6.1. The resource id survives only as the string convention `resource_<id>` inside `ResourceShortcutPinManager`'s shortcut id and is parsed back nowhere in the domain. Phase 02's dispatcher returns `false` for this kind, which leaves the cell behaving exactly as it does today.
- `app:` keeps its existing menu (`LauncherAppActionMenuManager`) untouched. Only the accessibility action of Phase 02 reaches it, per the owner's §3.3 accessibility ruling.

**Consequence to state out loud:** a resource shortcut pinned through "Add to home screen" and accepted onto our own desktop lands as a `pin:` cell, so two visually identical resource tiles answer the long press differently depending on which path created them. The owner resolved this knowingly (§6.1); §5.3 is the extension point that closes it later.

---

## Flavor set

`docs/FLAVOR_MATRIX.md` (generated from `app_v2/build.gradle.kts`) declares `SUPPORT_LAUNCHER` `[+]` for **standard** and **noLegal**, and `[-]*` (inherited from `defaultConfig`) for lite, photos, legacy and vr.

The launcher UI is mounted by source set, not by a `BuildConfig` read: `src/launcherEnabled/java` + `src/launcherEnabled/res` for standard and noLegal, `src/launcherDisabled/java` for the other four. Every file this plan adds under `ui/launcher/**` therefore lives in `app_v2/src/launcherEnabled/`, with no flavor guard in `src/main` (CLAUDE.md Rule 14).

The menu-composition catalogs of Phase 01 live in `src/main` on purpose: they carry no launcher dependency and the main window is their second caller (strategic §11.2). They name no launcher type, so `src/launcherDisabled` flavors compile them without pulling anything in.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/ALL_FEATURES.jsonl` carries the new capability (strategic §8), flavors `standard` + `noLegal`. `docs/FEATURES*.md` is **not** edited here - CLAUDE.md §11 reserves the showcase for `/skill-release`.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - Phases 01-04 each add public classes.
- [ ] `/spec-check S1424` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## Device-only acceptance criteria

Strategic §11 has six criteria. Two are statically provable and carried as step Verifications; four can only be settled on a device and are named here so no one dresses them up as a Grep:

- **§11.1 long press opens the menu on a resource and a stream cell, app cells unchanged** - **device-only.** Static proxy: the dispatcher's `when` covers `Resource` and `Stream` and falls through to the existing `App` branch.
- **§11.2 composition read from one provider by both hosts** - static. `ResourceActionCatalog.actionsFor` is the only producer of the item list, and both `ResourceAdapter` and the launcher menu manager call it.
- **§11.3 no item is a no-op** - static per action, device-only in aggregate. Each action the desktop surface emits is gated on a host capability, so an unwired action is absent rather than dead. `LauncherResourceActionManager.DEFERRED` is empty: every resource row the desktop shows runs. `LauncherStreamActionManager.DEFERRED` holds `EDIT` alone - see the Phase 06 handoff note for why it is absent rather than reproduced.
- **§11.4 delete asks the same dialog as the main window** - static. `ResourceDeleteConfirmation` is the single builder, called by `MainActivity` and by the launcher.
- **§11.5 the menu opens by accessibility action on every cell kind** - **device-only** for TalkBack; static proxy: the action is registered in the shared cell binder, before the command kind is read.
- **§11.6 composition covered by a unit test** - static. `ResourceActionCatalogTest` + `StreamActionCatalogTest`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1424`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-07 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-07 - Phases 01-02 implemented, then verified green by the owner (`fk`, `dq`, both test classes 7/7, `post-change: PASS`).
- 2026-08-07 - Phases 03, 04, 05 implemented and step 06.4 done. No gradle ran in that session by instruction, so every `Project compiles` criterion for 03-05 is UNPROVEN and left unticked; the dev log, catalog sync, `ALL_FEATURES` record, spec-catalog status and the `S1424` probe tag are the owner's.
