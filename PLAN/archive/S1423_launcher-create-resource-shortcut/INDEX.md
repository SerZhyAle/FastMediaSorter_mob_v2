# Tactical Plan: S1423 - launcher-create-resource-shortcut

**Strategic spec:** [`../S1423_launcher-create-resource-shortcut.md`](../S1423_launcher-create-resource-shortcut.md)
**Research inputs:** none
**Feature:** Create a resource from the launcher home screen and pin its shortcut automatically
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-08-07

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | pin-on-create-link | - | ✅ Done | 4/4 | [PHASE_01__pin-on-create-link.md](PHASE_01__pin-on-create-link.md) |
| 02 | launcher-menu-entry | 01 | ✅ Done | 4/4 | [PHASE_02__launcher-menu-entry.md](PHASE_02__launcher-menu-entry.md) |
| 03 | content-picker-entry | 02 | ✅ Done | 3/3 | [PHASE_03__content-picker-entry.md](PHASE_03__content-picker-entry.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done |  2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 carries two research items, both `Resolved` (§6.1 by the owner on 2026-08-07, §6.2 mechanically). No blocker gates Phase 01.

- none

---

## Scope boundary

The owner's §3.3 ruling names three entry points. Two are in this ticket:

1. `LauncherStartMenuFragment` - a row in the launcher menu (Phase 02).
2. `ResourcePickerDialogFragment` opened on the content picker's resource-selection step - a "Create new.." item (Phase 03).

The third (long-press on an empty desktop) has no host menu in the project yet; it is built in **S1466** and is out of scope here. Phase 02 puts the launch behind one shared call so S1466 adds a call site, not a third handler (strategic §5.1.1).

---

## Flavor set

`docs/FLAVOR_MATRIX.md` (generated from `app_v2/build.gradle.kts`) declares `SUPPORT_LAUNCHER` `[+]` for **standard** and **noLegal**, and `[-]*` (inherited from `defaultConfig`) for lite, photos, legacy and vr.

The launcher UI is not gated by a `BuildConfig` read - it is mounted by source set. `app_v2/build.gradle.kts` adds `src/launcherEnabled/java` + `src/launcherEnabled/res` to standard and noLegal, and `src/launcherDisabled/java` to the other four. Every entry-point file in Phases 02-03 therefore lives under `app_v2/src/launcherEnabled/`, which ships the entry point in exactly the same set as the rest of the home screen (strategic §3.2) with no flavor guard in `src/main` (CLAUDE.md Rule 14).

The one Phase-03 file in `src/main` (`ResourcePickerDialogFragment`) is shared with the app-launch-panel editor, which ships in every flavor. It gains an **opt-in argument defaulting to off**, so the new item is absent unless a `launcherEnabled` caller asks for it.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/ALL_FEATURES.jsonl` carries the new capability (strategic §8). `docs/FEATURES*.md` is **not** edited here - CLAUDE.md §11 reserves the showcase for `/skill-release`.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - Phases 01 and 02 each add a public class.
- [ ] `/spec-check S1423` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## Device-only acceptance criteria

Strategic §11 has six criteria. Three are statically provable and are carried as step Verifications; three can only be settled on a device and are named here so no one dresses them up as a Grep:

- **§11.1 same creation screen** - static. Both entry points call `AddResourceActivity.createIntent`; no second creation screen exists.
- **§11.2 shortcut appears on success without further user action** - **device-only.** Requires a real launcher accepting the pin request.
- **§11.3 cancel leaves neither resource nor shortcut** - **device-only.** Static proxy only: the pin call sits inside the `ResourcesAdded` branch, which no failure or cancel path emits.
- **§11.4 in-app creation pins nothing** - static. The flag defaults to `false` and `MainEventHandler` is the only production caller of `createIntent`.
- **§11.5 message when pinning is unavailable** - **device-only** for the visible message; static proxy: the `Unsupported` branch maps to `R.string.resource_shortcut_unsupported`.
- **§11.6 keyboard and D-pad reach the entry point** - **device-only** for actual focus traversal; static proxy: the new row carries the same `focusable` / `foreground` attributes as its siblings.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1423`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-07 - Initial tactical plan authored by `/spec-tech`.
